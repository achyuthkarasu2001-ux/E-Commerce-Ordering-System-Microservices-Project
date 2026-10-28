package com.ecommerce.orderservice.dto;

import java.math.BigDecimal;

public record OrderItemResponse(
        Long productId,
        Integer quantity,
        BigDecimal priceAtOrderTime
) {}
