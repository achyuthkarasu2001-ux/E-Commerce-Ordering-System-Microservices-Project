package com.ecommerce.userservice.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security posture for user-service itself.
 *
 * Real request authentication (verifying the JWT on every call) happens
 * ONCE, at the edge, in gateway-service — see JwtAuthFilter there. By the
 * time a request reaches user-service, the gateway has already validated
 * it and attached X-User-Id / X-User-Role headers.
 *
 * This means user-service's own Spring Security config only needs to:
 *   1. Provide a PasswordEncoder bean for hashing passwords on register/login
 *   2. Stay stateless (no sessions, no CSRF — this is a pure REST API)
 *   3. Permit ALL its own endpoints, since it TRUSTS the gateway to have
 *      already gate-kept the traffic that reaches it.
 *
 * This "trust the edge" model is simple and common, but it does mean
 * user-service must never be reachable directly from outside the private
 * network in a real deployment — only the gateway should have a public
 * address. For defense-in-depth, some teams re-validate the JWT signature
 * in every downstream service too; see the README for that alternative.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
