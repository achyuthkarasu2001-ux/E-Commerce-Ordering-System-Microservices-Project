package com.ecommerce.orderservice.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

/**
 * order-service calls product-service and inventory-service on the
 * caller's behalf while placing an order. Those downstream services enforce
 * their own role checks (e.g. "only ADMIN can create a product") based on
 * X-User-Id / X-User-Role — headers that gateway-service attached to the
 * ORIGINAL inbound request to order-service.
 *
 * Without this interceptor, those headers would be silently dropped: Feign
 * builds a brand new outgoing HTTP request and doesn't automatically copy
 * anything from the request order-service itself is currently handling.
 */
@Component
public class FeignAuthHeaderInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return; // not running within an inbound HTTP request (e.g. a scheduled job) — nothing to forward
        }
        HttpServletRequest request = attrs.getRequest();
        copyHeaderIfPresent(request, template, "X-User-Id");
        copyHeaderIfPresent(request, template, "X-User-Role");
    }

    private void copyHeaderIfPresent(HttpServletRequest request, RequestTemplate template, String headerName) {
        String value = request.getHeader(headerName);
        if (value != null) {
            template.header(headerName, value);
        }
    }
}
