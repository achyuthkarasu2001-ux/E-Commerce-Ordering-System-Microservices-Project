package com.ecommerce.configserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;

/**
 * Centralized Configuration Server.
 *
 * Serves configuration files (from ../config-repo by default, "native" profile)
 * to every other microservice. Each service pulls its config on startup and
 * (optionally) refreshes it at runtime via the /actuator/refresh endpoint or
 * Spring Cloud Bus.
 */
@SpringBootApplication
@EnableConfigServer
public class ConfigServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(ConfigServerApplication.class, args);
    }
}
