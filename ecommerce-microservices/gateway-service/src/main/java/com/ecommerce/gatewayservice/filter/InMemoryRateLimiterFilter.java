package com.ecommerce.gatewayservice.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simple in-memory token-bucket-style rate limiter, keyed by client IP.
 *
 * This is intentionally dependency-free (no Redis) so the whole stack can run
 * locally with just Postgres. For a multi-instance gateway in production,
 * replace this with Spring Cloud Gateway's built-in RedisRateLimiter so the
 * limit is enforced consistently across all gateway instances.
 */
@Component
public class InMemoryRateLimiterFilter implements GlobalFilter, Ordered {

    private static final int MAX_REQUESTS_PER_WINDOW = 20; // requests
    private static final long WINDOW_SIZE_MILLIS = 10_000;  // per 10 seconds

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String clientKey = resolveClientKey(exchange);
        Bucket bucket = buckets.computeIfAbsent(clientKey, k -> new Bucket());

        long now = Instant.now().toEpochMilli();
        synchronized (bucket) {
            if (now - bucket.windowStart > WINDOW_SIZE_MILLIS) {
                bucket.windowStart = now;
                bucket.count.set(0);
            }
            int current = bucket.count.incrementAndGet();
            if (current > MAX_REQUESTS_PER_WINDOW) {
                exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                exchange.getResponse().getHeaders().add("X-RateLimit-Retry-After-Millis",
                        String.valueOf(WINDOW_SIZE_MILLIS - (now - bucket.windowStart)));
                return exchange.getResponse().setComplete();
            }
        }
        return chain.filter(exchange);
    }

    private String resolveClientKey(ServerWebExchange exchange) {
        if (exchange.getRequest().getRemoteAddress() != null) {
            return exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
        }
        return "unknown";
    }

    @Override
    public int getOrder() {
        return -1; // run early, before routing
    }

    private static class Bucket {
        volatile long windowStart = Instant.now().toEpochMilli();
        AtomicInteger count = new AtomicInteger(0);
    }
}
