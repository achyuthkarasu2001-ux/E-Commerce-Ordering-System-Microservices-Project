package com.ecommerce.orderservice.config;

import feign.Logger;
import feign.Request;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class FeignConfig {

    @Bean
    Logger.Level feignLoggerLevel() {
        return Logger.Level.BASIC;
    }

    @Bean
    Request.Options feignRequestOptions() {
        // connectTimeout, readTimeout — keep short so Resilience4j's own
        // timeouts/circuit breaker take effect quickly rather than the
        // caller hanging on a slow downstream call.
        return new Request.Options(2, TimeUnit.SECONDS, 3, TimeUnit.SECONDS, true);
    }
}
