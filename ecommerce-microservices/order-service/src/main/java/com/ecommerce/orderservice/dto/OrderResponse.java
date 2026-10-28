package com.ecommerce.orderservice.dto;

import com.ecommerce.orderservice.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
        Long id,
        Long userId,
        BigDecimal totalAmount,
        OrderStatus status,
        Instant createdAt,
        List<OrderItemResponse> items
) {}
