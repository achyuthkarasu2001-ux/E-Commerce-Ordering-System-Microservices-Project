package com.ecommerce.orderservice.dto;

/** Mirrors inventory-service's InventoryDto. */
public record InventoryDto(Long id, Long productId, Integer quantity, Integer reservedQuantity, String warehouseLocation) {}
