package com.ecommerce.orderservice.exception;

public class OrderPlacementException extends RuntimeException {
    public OrderPlacementException(String message, Throwable cause) {
        super(message, cause);
    }
}
