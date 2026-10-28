package com.ecommerce.orderservice.client;

import com.ecommerce.orderservice.dto.InventoryDto;
import com.ecommerce.orderservice.dto.StockReservationRequest;
import org.springframework.stereotype.Component;

@Component
public class InventoryServiceClientFallback implements InventoryServiceClient {
    @Override
    public InventoryDto reserveStock(StockReservationRequest request) {
        throw new IllegalStateException("inventory-service is currently unavailable (fallback triggered) while reserving stock for product " + request.productId());
    }

    @Override
    public InventoryDto releaseStock(StockReservationRequest request) {
        throw new IllegalStateException("inventory-service is currently unavailable (fallback triggered) while releasing stock for product " + request.productId());
    }
}
