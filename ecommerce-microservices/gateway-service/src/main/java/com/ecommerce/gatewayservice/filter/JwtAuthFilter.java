package com.ecommerce.gatewayservice.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.List;

/**
 * Validates the JWT on every request BEFORE it's routed to any downstream
 * service. This is the single enforcement point for authentication in the
 * whole system — none of the business services re-check the token
 * themselves; they trust that a request only reaches them via the gateway.
 *
 * On success: strips nothing, but ADDS two headers the downstream services
 * read instead of re-parsing the token themselves:
 *   X-User-Id
 *   X-User-Role
 *
 * On failure: short-circuits with 401 and never calls the downstream chain.
 *
 * Runs at order 0 — after the rate limiter (-1), before routing.
 */
@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {

    // Paths that don't require a token at all.
    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/auth/register",
            "/api/auth/login",
            "/actuator",
            "/fallback"
    );

    private final Key signingKey;

    public JwtAuthFilter(@Value("${app.jwt.secret}") String secret) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        if (isPublic(path)) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return unauthorized(exchange, "Missing or malformed Authorization header");
        }

        String token = authHeader.substring(7);
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(signingKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            Long userId = claims.get("userId", Long.class);
            String role = claims.get("role", String.class);

            // .header(name, value) REPLACES any existing value for that header name —
            // so even if a client tried to spoof X-User-Id/X-User-Role themselves,
            // whatever they sent is discarded here and overwritten with the values
            // decoded from the verified JWT signature.
            ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                    .header("X-User-Id", String.valueOf(userId))
                    .header("X-User-Role", role)
                    .build();

            return chain.filter(exchange.mutate().request(mutatedRequest).build());

        } catch (ExpiredJwtException e) {
            return unauthorized(exchange, "Token has expired");
        } catch (JwtException | IllegalArgumentException e) {
            return unauthorized(exchange, "Invalid token");
        }
    }

    private boolean isPublic(String path) {
        return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String reason) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().add("X-Auth-Error", reason);
        return exchange.getResponse().setComplete();
    }

    @Override
    public int getOrder() {
        return 0; // after the rate limiter, before routing filters
    }
}
