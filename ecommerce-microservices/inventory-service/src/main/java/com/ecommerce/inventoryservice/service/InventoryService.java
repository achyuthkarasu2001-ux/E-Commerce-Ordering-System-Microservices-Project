package com.ecommerce.inventoryservice.service;

import com.ecommerce.inventoryservice.dto.InventoryDto;
import com.ecommerce.inventoryservice.dto.StockReservationRequest;
import com.ecommerce.inventoryservice.entity.Inventory;
import com.ecommerce.inventoryservice.exception.InsufficientStockException;
import com.ecommerce.inventoryservice.exception.InventoryNotFoundException;
import com.ecommerce.inventoryservice.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository inventoryRepository;

    public InventoryDto getByProductId(Long productId) {
        return inventoryRepository.findByProductId(productId)
                .map(this::toDto)
                .orElseThrow(() -> new InventoryNotFoundException(productId));
    }

    public InventoryDto upsertStock(Long productId, int quantity, String warehouseLocation) {
        Inventory inv = inventoryRepository.findByProductId(productId)
                .orElse(Inventory.builder().productId(productId).quantity(0).reservedQuantity(0).build());
        inv.setQuantity(quantity);
        if (warehouseLocation != null) inv.setWarehouseLocation(warehouseLocation);
        return toDto(inventoryRepository.save(inv));
    }

    /**
     * Reserves stock for an order. Called by order-service (via OpenFeign)
     * during order placement. Uses a pessimistic lock so two concurrent
     * orders can't both reserve the last unit of the same product.
     */
    @Transactional
    public InventoryDto reserveStock(StockReservationRequest request) {
        Inventory inv = inventoryRepository.findByProductIdForUpdate(request.productId())
                .orElseThrow(() -> new InventoryNotFoundException(request.productId()));

        if (inv.availableQuantity() < request.quantity()) {
            throw new InsufficientStockException(request.productId(), request.quantity(), inv.availableQuantity());
        }
        inv.setReservedQuantity(inv.getReservedQuantity() + request.quantity());
        return toDto(inventoryRepository.save(inv));
    }

    /** Releases a previously-made reservation, e.g. if an order is cancelled. */
    @Transactional
    public InventoryDto releaseStock(StockReservationRequest request) {
        Inventory inv = inventoryRepository.findByProductIdForUpdate(request.productId())
                .orElseThrow(() -> new InventoryNotFoundException(request.productId()));

        int newReserved = Math.max(0, inv.getReservedQuantity() - request.quantity());
        inv.setReservedQuantity(newReserved);
        return toDto(inventoryRepository.save(inv));
    }

    private InventoryDto toDto(Inventory i) {
        return new InventoryDto(i.getId(), i.getProductId(), i.getQuantity(), i.getReservedQuantity(), i.getWarehouseLocation());
    }
}
