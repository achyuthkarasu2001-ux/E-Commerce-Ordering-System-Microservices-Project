package com.ecommerce.userservice.exception;

/** Thrown when the caller's role (forwarded by the gateway via X-User-Role) isn't sufficient. */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
