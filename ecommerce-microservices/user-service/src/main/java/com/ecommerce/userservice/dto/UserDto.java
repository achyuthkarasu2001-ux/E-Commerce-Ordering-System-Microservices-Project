package com.ecommerce.userservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record UserDto(
        Long id,
        @NotBlank String username,
        @Email @NotBlank String email,
        @NotBlank String fullName,
        String role
) {}
