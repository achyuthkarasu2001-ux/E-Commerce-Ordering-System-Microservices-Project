package com.ecommerce.inventoryservice.controller;

import com.ecommerce.inventoryservice.dto.InventoryDto;
import com.ecommerce.inventoryservice.dto.StockReservationRequest;
import com.ecommerce.inventoryservice.exception.ForbiddenException;
import com.ecommerce.inventoryservice.service.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping("/{productId}")
    public InventoryDto getByProductId(@PathVariable Long productId) {
        return inventoryService.getByProductId(productId);
    }

    /** Warehouse stock adjustments are an ADMIN-only operation. */
    @PutMapping("/{productId}")
    public InventoryDto upsertStock(@PathVariable Long productId,
                                     @RequestParam int quantity,
                                     @RequestParam(required = false) String warehouseLocation,
                                     @RequestHeader(value = "X-User-Role", required = false) String role) {
        if (!"ADMIN".equals(role)) {
            throw new ForbiddenException("Only ADMIN can adjust stock levels");
        }
        return inventoryService.upsertStock(productId, quantity, warehouseLocation);
    }

    /**
     * Called by order-service via Feign when an order is placed. Any
     * authenticated caller (not just ADMIN) may trigger a reservation —
     * order-service already establishes that the request came from a real
     * logged-in customer placing their own order (see order-service's
     * ownership check in OrderController).
     */
    @PostMapping("/reserve")
    public InventoryDto reserve(@Valid @RequestBody StockReservationRequest request,
                                 @RequestHeader(value = "X-User-Id", required = false) String userId) {
        requireAuthenticated(userId);
        return inventoryService.reserveStock(request);
    }

    @PostMapping("/release")
    public InventoryDto release(@Valid @RequestBody StockReservationRequest request,
                                 @RequestHeader(value = "X-User-Id", required = false) String userId) {
        requireAuthenticated(userId);
        return inventoryService.releaseStock(request);
    }

    private void requireAuthenticated(String userId) {
        if (userId == null) {
            throw new ForbiddenException("This operation requires an authenticated caller");
        }
    }
}
