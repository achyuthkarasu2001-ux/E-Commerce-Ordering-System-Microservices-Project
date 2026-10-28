# E-commerce Microservices

A Spring Boot and Spring Cloud project for exploring how a small e-commerce backend can be split into independent services. It covers service discovery, centralized configuration, API routing, JWT-based access control, service-to-service communication, and resilience patterns.

## What this project covers

- **Database per service:** each business service has its own PostgreSQL database.
- **Centralized configuration:** services load shared and service-specific settings from Spring Cloud Config Server.
- **Service discovery:** Eureka keeps track of service instances.
- **API Gateway:** routes incoming requests and applies rate limiting and circuit-breaker handling.
- **Authentication and authorization:** the user service issues JWTs; the gateway validates them and forwards trusted identity headers.
- **Service-to-service communication:** the order service uses OpenFeign to call the user, product, and inventory services.
- **Resilience:** Resilience4j provides circuit breakers, retries, and bulkheads around downstream calls.

This is a learning/reference project, not a production-ready deployment. Review the security, configuration, and operational choices before using the same approach in a real environment.

## Architecture

```text
                         Config Server :8888
                                |
                         configuration
                                |
                          Eureka :8761
                                |
Client ---> API Gateway :8080 --+-------------------------------+
                  |             |               |               |
                  v             v               v               v
             user-service  product-service  inventory-service  order-service
                :8081          :8082            :8083             :8084
                  |               |                |                 |
                userdb         productdb        inventorydb        orderdb

                 Order service calls the other services using OpenFeign.
```

Each business service owns its data. Services do not query one another's database tables directly; they request the information they need through service APIs.

### Request authentication

1. A client registers or logs in through `/api/auth/register` or `/api/auth/login`.
2. The user service returns a signed JWT.
3. The client sends the token in the `Authorization: Bearer <token>` header on protected requests.
4. The gateway validates the token's signature and expiry. Missing or invalid tokens are rejected with `401 Unauthorized`.
5. The gateway sets `X-User-Id` and `X-User-Role` from the verified token claims, replacing any values supplied by the client.
6. Business services use those headers to apply their own ownership and role checks.
7. When the order service calls product or inventory APIs, a Feign interceptor forwards the identity headers.

The gateway is the main authentication boundary in this example. Because downstream services trust the gateway-provided headers, they must not be directly reachable by untrusted clients. See the security notes below.

### Order placement

When a customer submits `POST /api/orders`, the order service:

1. Validates the user through the user service.
2. Retrieves product details and prices from the product service.
3. Requests stock reservation from the inventory service.
4. Applies Resilience4j policies to its downstream calls.
5. Saves the order with status `CONFIRMED` if the required calls succeed.

## Project structure

```text
ecommerce-microservices/
├── pom.xml
├── config-server/
├── config-repo/
├── eureka-server/
├── gateway-service/
├── user-service/
├── product-service/
├── inventory-service/
├── order-service/
├── docker-compose.yml
└── README.md
```

The business services use a consistent package layout:

```text
com.ecommerce.<service>
├── entity/       JPA entities
├── repository/   Spring Data repositories
├── service/      business logic
├── controller/   REST endpoints
├── dto/          request and response models
├── exception/    custom exceptions and global handlers
├── client/       OpenFeign clients and fallbacks (order service)
├── config/       service-specific configuration
└── security/     JWT utilities and security configuration (user service)
```

The gateway also has a `filter/` package for its global filters and a fallback controller.

## Prerequisites

- Java 17 or later
- Maven 3.8 or later
- Docker and Docker Compose

## Run locally

Run commands from the repository root unless a command changes directory.

### 1. Start PostgreSQL

```bash
docker compose up -d
```

This starts four PostgreSQL containers on ports `5432`–`5435`, one for each business service. Hibernate is configured to create or update the tables when each service starts.

### 2. Build the project

```bash
mvn clean install
```

### 3. Start the infrastructure services

Start the config server first:

```bash
cd config-server
mvn spring-boot:run
```

In a separate terminal, start Eureka:

```bash
cd eureka-server
mvn spring-boot:run
```

Wait for both services to start before launching the business services. The config import is optional in this setup, so a service may still start if the config server is unavailable, but it will not receive the centralized configuration.

### 4. Start the business services

Run each command in a separate terminal:

```bash
cd user-service
mvn spring-boot:run
```

```bash
cd product-service
mvn spring-boot:run
```

```bash
cd inventory-service
mvn spring-boot:run
```

```bash
cd order-service
mvn spring-boot:run
```

The default ports are `8081`, `8082`, `8083`, and `8084`, respectively.

### 5. Start the gateway

In another terminal:

```bash
cd gateway-service
mvn spring-boot:run
```

The gateway listens on port `8080`. Use it as the entry point for the API examples below.

### 6. Check the services

- Eureka dashboard: <http://localhost:8761>
- Config served for the order service: <http://localhost:8888/order-service/default>

The Eureka dashboard should list the user, product, inventory, order, and gateway services after they have registered.

## Try the API

The following examples send requests through the gateway on port `8080`. The register and login endpoints are public; other endpoints require a valid JWT.

### 1. Register a customer

```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"asha","email":"asha@example.com","fullName":"Asha Rao","password":"correct-horse-1"}'
```

The response includes a token and user details. For the remaining examples, log in and save the token:

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"asha","password":"correct-horse-1"}' \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['token'])")
```

### 2. Check role-based access

A regular customer should not be able to create a product:

```bash
curl -i -X POST http://localhost:8080/api/products \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"Wireless Mouse","price":19.99,"sku":"WM-001","category":"Electronics"}'
```

The expected result is `403 Forbidden`, because product creation is restricted to administrators.

To test administrator operations, register a separate account and promote it directly in the local development database. There is no public self-service role-promotion endpoint.

```bash
docker exec -it user-db psql -U postgres -d userdb \
  -c "UPDATE users SET role='ADMIN' WHERE username='admin_user';"
```

Log in as that account and save its token:

```bash
ADMIN_TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin_user","password":"admin-pass-1"}' \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['token'])")
```

Use credentials that match the account you registered. The username and password above are examples.

### 3. Create a product as an administrator

```bash
curl -X POST http://localhost:8080/api/products \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"Wireless Mouse","price":19.99,"sku":"WM-001","category":"Electronics"}'
```

### 4. Add inventory

Assuming the product was created with ID `1`:

```bash
curl -X PUT "http://localhost:8080/api/inventory/1?quantity=100&warehouseLocation=HYD-1" \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

### 5. Place an order as the customer

Assuming the customer has user ID `1` and the product has ID `1`:

```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"userId":1,"items":[{"productId":1,"quantity":2}]}'
```

### 6. Retrieve the order

```bash
curl http://localhost:8080/api/orders/1 \
  -H "Authorization: Bearer $TOKEN"
```

A customer should be able to retrieve their own order, but not another customer's order.

## Test the security and resilience behavior

### Authorization

- Send a protected request without an `Authorization` header. The gateway should return `401 Unauthorized`.
- Try to place an order using another customer's `userId`. The order service should return `403 Forbidden`.
- Try to update inventory with a customer token. The inventory service should reject the request with `403 Forbidden`.

### Resilience

- Stop the inventory service and submit an order. Check the order service logs for retry attempts and circuit-breaker behavior. The fallback should return a `503` response rather than leaving the request waiting indefinitely.
- The circuit-breaker endpoint configured for the order service can be checked at <http://localhost:8084/actuator/circuitbreakers>.
- Send enough requests through a gateway route in a short period to test the custom in-memory rate limiter. Requests above the configured limit should receive `429 Too Many Requests`.

Exact outcomes depend on the configuration and the current state of the services.

## Design notes

### Database per service

The user, product, inventory, and order services use separate databases: `userdb`, `productdb`, `inventorydb`, and `orderdb`. Each service is responsible for its own schema. This avoids direct cross-service SQL queries and allows schemas to evolve independently. Data needed from another service is retrieved through its API.

### Centralized configuration

The config server reads files from `config-repo/` using Spring's `native` profile. This project uses a local filesystem directory rather than a Git-backed config repository to keep the local setup simple.

Each service imports configuration using:

```yaml
spring:
  config:
    import: optional:configserver:http://localhost:8888
```

The config server combines shared settings from `application.yml` with service-specific settings such as `order-service.yml`. The `optional:` prefix allows a service to start without the config server. That can be useful during local development, but production behavior should be chosen deliberately.

### Eureka service discovery

Services register with Eureka using their `spring.application.name`. The gateway routes to service names such as `lb://user-service`, while Feign clients use `@FeignClient(name = "user-service")`. Spring Cloud resolves the available instance rather than requiring each caller to hardcode a host and port.

Eureka can be replaced with another Spring Cloud-compatible discovery implementation, such as Consul, with the corresponding dependency and configuration changes.

### API Gateway

Routes are configured in the gateway's application configuration. The gateway resolves service names through discovery, applies circuit-breaker filters with fallback handling, and runs the custom `InMemoryRateLimiterFilter` before routing the request.

The in-memory limiter is suitable for a local demonstration. Its counters are not shared between gateway instances, so it is not a consistent distributed rate limit when multiple instances are running.

### JWT authentication and authorization

The user service hashes passwords with BCrypt and issues signed JWTs containing the user ID and role. The gateway validates the token and forwards those claims in `X-User-Id` and `X-User-Role` headers.

The business services still apply authorization rules:
- Product and inventory write operations require the `ADMIN` role.
- Order operations check whether the caller owns the order, with administrator access where configured.
- The order service forwards the identity headers on its Feign requests so downstream authorization checks have the caller's context.

The JWT signing secret is stored in the shared local configuration for this example. Do not commit real secrets or use a development secret in a deployed environment. Use a secrets manager or another appropriate secret-injection mechanism.

**Security limitation:** downstream services trust the identity headers set by the gateway and do not independently validate the JWT. If a client can reach a business service directly, it may be able to forge those headers. In a real deployment, restrict network access to internal services and consider validating tokens in each service as an additional layer of protection.

### OpenFeign

The order service defines Feign clients for the user, product, and inventory services. Eureka and Spring Cloud LoadBalancer resolve the service instances. This keeps the order service from needing hardcoded addresses for its dependencies.

### Resilience4j

Resilience is demonstrated at two levels:

1. **Gateway:** route-level circuit breakers and fallback handling.
2. **Order service:** method-level `@CircuitBreaker`, `@Retry`, and `@Bulkhead` policies for individual downstream dependencies.

These patterns serve different purposes:

- **Circuit breaker:** temporarily stops calls to a dependency that is repeatedly failing.
- **Retry:** repeats a failed call when the failure may be temporary.
- **Bulkhead:** limits concurrent calls so a slow dependency is less likely to consume all available resources.

Feign fallback classes are also included for comparison. In a real application, choose a clear fallback strategy and avoid layering multiple mechanisms without understanding how their behavior interacts. Retries should be configured carefully for operations that may have side effects.

## Ideas for further work

Once the basic flows are working, useful extensions include:

- Add distributed tracing with Micrometer Tracing and Zipkin or Tempo.
- Add Spring Cloud Bus or another mechanism to refresh configuration without restarting every service.
- Introduce an event-driven order workflow with Kafka or RabbitMQ.
- Replace the in-memory rate limiter with Spring Cloud Gateway's Redis-based rate limiter.
- Add refresh tokens alongside short-lived access tokens.
- Add a protected administrator endpoint for changing user roles.
- Add JWT verification within each business service for defense in depth.
- Add integration tests for authorization, inventory reservation, and downstream service failures.

## Notes

This repository is intended for learning and experimentation. Configuration, secrets management, observability, data consistency, deployment hardening, and security should be reviewed before adapting the project for production use.
