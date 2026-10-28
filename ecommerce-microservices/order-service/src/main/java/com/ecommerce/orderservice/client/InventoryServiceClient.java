package com.ecommerce.orderservice.client;

import com.ecommerce.orderservice.dto.InventoryDto;
import com.ecommerce.orderservice.dto.StockReservationRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "inventory-service", fallback = InventoryServiceClientFallback.class)
public interface InventoryServiceClient {

    @PostMapping("/api/inventory/reserve")
    InventoryDto reserveStock(@RequestBody StockReservationRequest request);

    @PostMapping("/api/inventory/release")
    InventoryDto releaseStock(@RequestBody StockReservationRequest request);
}
