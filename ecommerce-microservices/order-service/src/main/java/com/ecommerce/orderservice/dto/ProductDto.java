package com.ecommerce.orderservice.dto;

import java.math.BigDecimal;

/** Mirrors product-service's ProductDto. */
public record ProductDto(Long id, String name, String description, BigDecimal price, String sku, String category) {}
