package com.ecommerce.userservice.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.time.Instant;
import java.util.Date;

/**
 * Issues signed JWTs on successful register/login.
 *
 * IMPORTANT: the secret configured here (app.jwt.secret) MUST be identical
 * to the one configured in gateway-service, since the gateway is what
 * verifies the signature on every subsequent request. In this reference
 * project both read the same value from config-server's shared
 * application.yml. In a real deployment, put this secret in a proper vault
 * (AWS Secrets Manager / HashiCorp Vault) and inject it as an env var —
 * never commit a real signing secret to source control.
 */
@Component
public class JwtUtil {

    private final Key signingKey;
    private final long expirationMs;

    public JwtUtil(@Value("${app.jwt.secret}") String secret,
                    @Value("${app.jwt.expiration-ms:3600000}") long expirationMs) {
        // HS256 requires a key of at least 256 bits; the demo secret in
        // application.yml is long enough, but Keys.hmacShaKeyFor will throw
        // a clear error early if someone shortens it.
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes());
        this.expirationMs = expirationMs;
    }

    public String generateToken(Long userId, String username, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .setSubject(username)
                .claim("userId", userId)
                .claim("role", role)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plusMillis(expirationMs)))
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public long getExpirationMs() {
        return expirationMs;
    }
}
