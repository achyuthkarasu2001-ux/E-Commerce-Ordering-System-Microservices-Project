package com.ecommerce.eurekaserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/**
 * Service Discovery server. Every other service registers itself here on
 * startup and looks up other services' instances (host:port) by name,
 * instead of hardcoding URLs. The Gateway and OpenFeign clients both use
 * this registry via "lb://service-name" and @FeignClient(name = "service-name").
 *
 * (An equivalent Consul-based setup would swap this module for a Consul
 * agent + spring-cloud-starter-consul-discovery on each client — see the
 * README for that alternative wiring.)
 */
@SpringBootApplication
@EnableEurekaServer
public class EurekaServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(EurekaServerApplication.class, args);
    }
}
