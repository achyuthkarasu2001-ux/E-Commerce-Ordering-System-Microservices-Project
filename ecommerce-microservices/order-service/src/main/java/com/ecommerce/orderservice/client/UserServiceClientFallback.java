package com.ecommerce.orderservice.client;

import com.ecommerce.orderservice.dto.UserDto;
import org.springframework.stereotype.Component;

/**
 * Fallback used when the circuit for user-service is OPEN or the call
 * otherwise fails. Feign fallbacks fire for ANY exception from the client,
 * so combine with Resilience4j annotations at the call site for finer-grained
 * control (see OrderService).
 */
@Component
public class UserServiceClientFallback implements UserServiceClient {
    @Override
    public UserDto getUserById(Long id) {
        throw new IllegalStateException("user-service is currently unavailable (fallback triggered) for user id " + id);
    }
}
