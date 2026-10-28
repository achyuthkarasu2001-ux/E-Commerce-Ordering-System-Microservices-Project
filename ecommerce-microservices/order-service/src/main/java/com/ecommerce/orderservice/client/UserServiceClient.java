package com.ecommerce.orderservice.client;

import com.ecommerce.orderservice.dto.UserDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * OpenFeign client for user-service. "name" must match the service's
 * spring.application.name so it resolves via Eureka (client-side load
 * balancing is automatic when spring-cloud-starter-loadbalancer is on the
 * classpath, which it is transitively via the Eureka client starter).
 */
@FeignClient(name = "user-service", fallback = UserServiceClientFallback.class)
public interface UserServiceClient {

    @GetMapping("/api/users/{id}")
    UserDto getUserById(@PathVariable("id") Long id);
}
