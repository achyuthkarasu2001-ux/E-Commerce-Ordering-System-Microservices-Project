package com.ecommerce.orderservice.service;

import com.ecommerce.orderservice.client.InventoryServiceClient;
import com.ecommerce.orderservice.client.ProductServiceClient;
import com.ecommerce.orderservice.client.UserServiceClient;
import com.ecommerce.orderservice.dto.*;
import com.ecommerce.orderservice.entity.Order;
import com.ecommerce.orderservice.entity.OrderItem;
import com.ecommerce.orderservice.entity.OrderStatus;
import com.ecommerce.orderservice.exception.OrderNotFoundException;
import com.ecommerce.orderservice.exception.OrderPlacementException;
import com.ecommerce.orderservice.repository.OrderRepository;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final UserServiceClient userServiceClient;
    private final ProductServiceClient productServiceClient;
    private final InventoryServiceClient inventoryServiceClient;

    /**
     * Places an order by orchestrating three downstream calls:
     *   1. validate the user exists (user-service)
     *   2. look up each product's price (product-service)
     *   3. reserve stock for each item (inventory-service)
     *
     * Each downstream call is wrapped with its own Resilience4j policies
     * (see application.yml for the numeric thresholds):
     *   - CircuitBreaker: stops hammering a downstream service that's already failing
     *   - Retry: retries transient failures a couple of times with backoff
     *   - Bulkhead: caps concurrent in-flight calls to each downstream service,
     *     so a slow dependency can't exhaust this service's threads
     */
    public OrderResponse placeOrder(CreateOrderRequest request) {
        UserDto user = fetchUser(request.userId());

        Order order = Order.builder()
                .userId(user.id())
                .status(OrderStatus.PENDING)
                .totalAmount(BigDecimal.ZERO)
                .build();

        BigDecimal total = BigDecimal.ZERO;
        for (OrderItemRequest itemReq : request.items()) {
            ProductDto product = fetchProduct(itemReq.productId());
            reserveStock(itemReq.productId(), itemReq.quantity());

            BigDecimal lineTotal = product.price().multiply(BigDecimal.valueOf(itemReq.quantity()));
            total = total.add(lineTotal);

            order.addItem(OrderItem.builder()
                    .productId(product.id())
                    .quantity(itemReq.quantity())
                    .priceAtOrderTime(product.price())
                    .build());
        }

        order.setTotalAmount(total);
        order.setStatus(OrderStatus.CONFIRMED);
        Order saved = orderRepository.save(order);
        return toResponse(saved);
    }

    public OrderResponse getById(Long id) {
        return orderRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new OrderNotFoundException(id));
    }

    public List<OrderResponse> getByUserId(Long userId) {
        return orderRepository.findByUserId(userId).stream().map(this::toResponse).toList();
    }

    // ---- Individual downstream calls, each with its own resilience policy ----

    @CircuitBreaker(name = "userService", fallbackMethod = "fetchUserFallback")
    @Retry(name = "userService")
    @Bulkhead(name = "userService")
    public UserDto fetchUser(Long userId) {
        return userServiceClient.getUserById(userId);
    }

    private UserDto fetchUserFallback(Long userId, Throwable t) {
        throw new OrderPlacementException("Could not validate user " + userId + " — user-service unavailable", t);
    }

    @CircuitBreaker(name = "productService", fallbackMethod = "fetchProductFallback")
    @Retry(name = "productService")
    @Bulkhead(name = "productService")
    public ProductDto fetchProduct(Long productId) {
        return productServiceClient.getProductById(productId);
    }

    private ProductDto fetchProductFallback(Long productId, Throwable t) {
        throw new OrderPlacementException("Could not fetch product " + productId + " — product-service unavailable", t);
    }

    @CircuitBreaker(name = "inventoryService", fallbackMethod = "reserveStockFallback")
    @Retry(name = "inventoryService")
    @Bulkhead(name = "inventoryService")
    public InventoryDto reserveStock(Long productId, Integer quantity) {
        return inventoryServiceClient.reserveStock(new StockReservationRequest(productId, quantity));
    }

    private InventoryDto reserveStockFallback(Long productId, Integer quantity, Throwable t) {
        throw new OrderPlacementException("Could not reserve stock for product " + productId + " — inventory-service unavailable", t);
    }

    // ---- Mapping ----

    private OrderResponse toResponse(Order order) {
        List<OrderItemResponse> items = order.getItems().stream()
                .map(i -> new OrderItemResponse(i.getProductId(), i.getQuantity(), i.getPriceAtOrderTime()))
                .toList();
        return new OrderResponse(order.getId(), order.getUserId(), order.getTotalAmount(),
                order.getStatus(), order.getCreatedAt(), items);
    }
}
