package com.ecommerce.orderservice.controller;

import com.ecommerce.orderservice.dto.CreateOrderRequest;
import com.ecommerce.orderservice.dto.OrderResponse;
import com.ecommerce.orderservice.exception.ForbiddenException;
import com.ecommerce.orderservice.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Ownership rule: a CUSTOMER may only place/view THEIR OWN orders.
 * ADMIN may act on behalf of/view anyone's orders (e.g. support staff
 * investigating a customer issue).
 *
 * X-User-Id / X-User-Role are set by gateway-service's JwtAuthFilter from
 * the verified JWT — they cannot be spoofed by the caller (see that
 * filter's header-mutation comment).
 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<OrderResponse> placeOrder(@Valid @RequestBody CreateOrderRequest request,
                                                      @RequestHeader(value = "X-User-Id", required = false) Long callerId,
                                                      @RequestHeader(value = "X-User-Role", required = false) String role) {
        requireOwnerOrAdmin(request.userId(), callerId, role);
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.placeOrder(request));
    }

    @GetMapping("/{id}")
    public OrderResponse getById(@PathVariable Long id,
                                  @RequestHeader(value = "X-User-Id", required = false) Long callerId,
                                  @RequestHeader(value = "X-User-Role", required = false) String role) {
        OrderResponse order = orderService.getById(id);
        requireOwnerOrAdmin(order.userId(), callerId, role);
        return order;
    }

    @GetMapping("/user/{userId}")
    public List<OrderResponse> getByUserId(@PathVariable Long userId,
                                            @RequestHeader(value = "X-User-Id", required = false) Long callerId,
                                            @RequestHeader(value = "X-User-Role", required = false) String role) {
        requireOwnerOrAdmin(userId, callerId, role);
        return orderService.getByUserId(userId);
    }

    private void requireOwnerOrAdmin(Long resourceOwnerId, Long callerId, String role) {
        boolean isAdmin = "ADMIN".equals(role);
        boolean isOwner = callerId != null && callerId.equals(resourceOwnerId);
        if (!isAdmin && !isOwner) {
            throw new ForbiddenException("You may only access your own orders");
        }
    }
}
