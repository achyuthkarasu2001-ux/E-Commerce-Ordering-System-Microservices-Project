package com.ecommerce.inventoryservice.dto;

public record InventoryDto(
        Long id,
        Long productId,
        Integer quantity,
        Integer reservedQuantity,
        String warehouseLocation
) {}
