package com.ecommerce.gatewayservice.config;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Fallback endpoints invoked by the Gateway's CircuitBreaker filter when a
 * downstream service is unavailable/timing out. Returning a fast, friendly
 * 503 instead of letting the caller hang or see a raw connection error.
 */
@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @GetMapping("/users")
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Map<String, Object> users() {
        return body("user-service");
    }

    @GetMapping("/products")
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Map<String, Object> products() {
        return body("product-service");
    }

    @GetMapping("/inventory")
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Map<String, Object> inventory() {
        return body("inventory-service");
    }

    @GetMapping("/orders")
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Map<String, Object> orders() {
        return body("order-service");
    }

    private Map<String, Object> body(String service) {
        return Map.of(
                "status", "SERVICE_UNAVAILABLE",
                "message", service + " is temporarily unavailable. Please try again shortly.",
                "service", service
        );
    }
}
