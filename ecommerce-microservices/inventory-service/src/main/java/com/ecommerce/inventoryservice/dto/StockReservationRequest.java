package com.ecommerce.inventoryservice.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record StockReservationRequest(
        @NotNull Long productId,
        @NotNull @Positive Integer quantity
) {}
