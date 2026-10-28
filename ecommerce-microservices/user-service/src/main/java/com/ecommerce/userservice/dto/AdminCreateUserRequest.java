package com.ecommerce.userservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Used only by the admin-only "create user" endpoint (POST /api/users).
 * Normal users create their own accounts via POST /api/auth/register instead.
 * Kept separate from RegisterRequest because an admin can also set the role.
 */
public record AdminCreateUserRequest(
        @NotBlank String username,
        @Email @NotBlank String email,
        @NotBlank String fullName,
        @NotBlank @Size(min = 8) String password,
        String role
) {}
