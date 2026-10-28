package com.ecommerce.inventoryservice.exception;

public class InventoryNotFoundException extends RuntimeException {
    public InventoryNotFoundException(Long productId) {
        super("No inventory record for product id: " + productId);
    }
}
