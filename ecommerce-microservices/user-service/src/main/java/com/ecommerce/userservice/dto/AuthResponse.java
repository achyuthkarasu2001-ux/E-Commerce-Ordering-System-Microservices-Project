package com.ecommerce.userservice.dto;

/** Returned on successful register/login. The client sends this token back
 *  as "Authorization: Bearer <token>" on every subsequent request. */
public record AuthResponse(
        String token,
        Long userId,
        String username,
        String role,
        long expiresInMs
) {}
