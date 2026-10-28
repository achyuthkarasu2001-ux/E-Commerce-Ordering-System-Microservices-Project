package com.ecommerce.orderservice.dto;

/** Mirrors user-service's UserDto — order-service only needs a subset of fields. */
public record UserDto(Long id, String username, String email, String fullName, String role) {}
