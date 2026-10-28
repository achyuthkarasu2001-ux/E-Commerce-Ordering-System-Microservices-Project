package com.ecommerce.orderservice.client;

import com.ecommerce.orderservice.dto.ProductDto;
import org.springframework.stereotype.Component;

@Component
public class ProductServiceClientFallback implements ProductServiceClient {
    @Override
    public ProductDto getProductById(Long id) {
        throw new IllegalStateException("product-service is currently unavailable (fallback triggered) for product id " + id);
    }
}
