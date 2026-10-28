# E-commerce / Order Management System — Microservices Reference Project

A complete, runnable reference implementation of a Spring Cloud microservices
stack, built to teach (and let you replicate) the core patterns:

- **Database-per-service** (Postgres, one schema per service, no shared tables)
- **Centralized configuration** via Spring Cloud Config Server
- **Service discovery** via Netflix Eureka
- **API Gateway** via Spring Cloud Gateway (routing + rate limiting)
- **Authentication & authorization** via JWT, verified once at the gateway
- **Inter-service calls** via OpenFeign
- **Resilience** via Resilience4j (Circuit Breaker, Retry, Bulkhead)

---

## 1. Architecture at a glance

```
                        ┌─────────────────────┐
                        │   Config Server      │  :8888
                        │ (serves config-repo/) │
                        └──────────▲───────────┘
                                   │ all services pull config on boot
                                   │
                        ┌──────────┴───────────┐
                        │    Eureka Server      │  :8761
                        │ (service registry)    │
                        └──────────▲───────────┘
                                   │ everyone registers + discovers here
              ┌────────────────────┼─────────────────────┐
              │                    │                      │
   ┌──────────▼─────────┐          │                      │
   │   API Gateway       │  :8080  │                      │
   │ (routing, rate      │─────────┘                      │
   │  limit, circuit     │                                │
   │  breaker at edge)   │                                │
   └──────────┬──────────┘                                │
              │ lb://user-service, lb://product-service... │
   ┌──────────┼─────────────┬─────────────────┬────────────┴────┐
   │          │             │                 │                 │
┌──▼───┐  ┌───▼─────┐  ┌────▼──────┐   ┌───────▼───────┐         │
│ user  │  │ product │  │ inventory │   │    order      │◄────Feign
│service│  │ service │  │  service  │   │   service     │  (calls the
│ :8081 │  │  :8082  │  │   :8083   │   │    :8084      │  other three)
└──┬────┘  └────┬────┘  └─────┬─────┘   └──────┬────────┘
   │            │             │                │
┌──▼───┐  ┌─────▼───┐  ┌──────▼─────┐   ┌───────▼──────┐
│userdb │  │productdb│  │inventorydb │   │   orderdb    │
│(5432) │  │ (5433)  │  │  (5434)    │   │   (5435)     │
└───────┘  └─────────┘  └────────────┘   └──────────────┘
```

**Authentication flow** — verified ONCE, at the edge:
```
Client → POST /api/auth/register or /api/auth/login → user-service issues a JWT
Client → every other request → Authorization: Bearer <token>
              │
              ▼
        Gateway's JwtAuthFilter verifies the signature + expiry
              │  (invalid/missing → 401, request never reaches any service)
              ▼
        Adds X-User-Id / X-User-Role headers (overwriting anything
        the client tried to set itself), then routes downstream
              │
              ▼
        user/product/inventory/order-service read those headers
        and enforce their own role/ownership rules
              │
              ▼
        order-service forwards the SAME headers on its own Feign
        calls to product-service/inventory-service, via
        FeignAuthHeaderInterceptor, so authorization holds even
        for calls the client never makes directly
```

**Order placement flow** (the interesting one):
1. Client → Gateway → `order-service` `POST /api/orders`
2. `order-service` calls `user-service` (Feign) to validate the user
3. `order-service` calls `product-service` (Feign) to get each item's price
4. `order-service` calls `inventory-service` (Feign) to reserve stock
5. Each of those three calls is wrapped in **CircuitBreaker + Retry + Bulkhead**
   (see `order-service/src/main/java/.../service/OrderService.java`)
6. If everything succeeds, the order is saved as `CONFIRMED` in `orderdb`

---

## 2. Project layout

```
ecommerce-microservices/
├── pom.xml                    # parent Maven POM (dependency + module management)
├── config-server/             # Spring Cloud Config Server
├── config-repo/                # the config files config-server actually serves
├── eureka-server/             # Netflix Eureka registry
├── gateway-service/           # Spring Cloud Gateway
├── user-service/              # DB-backed CRUD service #1
├── product-service/           # DB-backed CRUD service #2
├── inventory-service/         # DB-backed CRUD service #3 (stock reservation)
├── order-service/             # Orchestrator: Feign + Resilience4j
├── docker-compose.yml         # 4x Postgres containers (database-per-service)
└── README.md                  # this file
```

Every business service (`user`, `product`, `inventory`, `order`) follows the
same internal package structure, so once you understand one, you understand
all four:

```
com.ecommerce.<service>
├── entity/          JPA @Entity classes
├── repository/      Spring Data JpaRepository interfaces
├── service/         business logic
├── controller/      @RestController REST endpoints
├── dto/             request/response records (never expose entities directly)
├── exception/       custom exceptions + @RestControllerAdvice handler
├── client/          (order-service only) OpenFeign clients + fallbacks
├── config/          (order-service: Feign timeouts + auth header forwarding;
│                     gateway-service: fallback controller)
└── security/         (user-service only) JwtUtil, SecurityConfig
```
`gateway-service` additionally has a `filter/` package
(`InMemoryRateLimiterFilter`, `JwtAuthFilter`) since it's built on Spring
Cloud Gateway's `GlobalFilter` model rather than `@RestController`s.

---

## 3. Prerequisites

- Java 17+
- Maven 3.8+
- Docker + Docker Compose (for the four Postgres instances)

## 4. Running it locally

### Step 1 — start the databases
```bash
docker compose up -d
```
This starts 4 isolated Postgres containers (ports 5432–5435), one per
service — the database-per-service pattern in action. Tables are
auto-created by Hibernate (`ddl-auto: update`) on first boot of each service.

### Step 2 — build everything
```bash
mvn clean install
```

### Step 3 — start infrastructure services first, in this order
```bash
# Terminal 1
cd config-server && mvn spring-boot:run

# Terminal 2 (wait for config-server to be up)
cd eureka-server && mvn spring-boot:run
```
Order matters here: `config-server` should be up before the business
services start (they do an *optional* config import, so they'll still boot
without it, just without centralized overrides). `eureka-server` should be
up before anything tries to register.

### Step 4 — start the business services (any order, any number of terminals)
```bash
cd user-service      && mvn spring-boot:run   # :8081
cd product-service   && mvn spring-boot:run   # :8082
cd inventory-service && mvn spring-boot:run   # :8083
cd order-service     && mvn spring-boot:run   # :8084
```

### Step 5 — start the gateway
```bash
cd gateway-service && mvn spring-boot:run     # :8080
```

### Step 6 — verify
- Eureka dashboard: http://localhost:8761 — you should see all 5 services
  registered (user, product, inventory, order, gateway).
- Config server: http://localhost:8888/order-service/default — returns the
  merged config order-service would receive.

---

## 5. Trying out the whole flow

All calls below go **through the gateway** on port 8080. Everything except
`/api/auth/register` and `/api/auth/login` now requires a valid JWT.

```bash
# 1. Register a regular customer account
curl -X POST localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"asha","email":"asha@example.com","fullName":"Asha Rao","password":"correct-horse-1"}'
# → { "token": "eyJ...", "userId": 1, "username": "asha", "role": "CUSTOMER", "expiresInMs": 3600000 }

# Save the token for convenience:
TOKEN=$(curl -s -X POST localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"asha","password":"correct-horse-1"}' | python3 -c "import sys,json;print(json.load(sys.stdin)['token'])")

# 2. Try creating a product AS asha (a CUSTOMER) — expect 403, only ADMIN can do this
curl -i -X POST localhost:8080/api/products \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"Wireless Mouse","price":19.99,"sku":"WM-001","category":"Electronics"}'

# To actually create products/manage stock, register a second account and
# promote it to ADMIN directly in userdb (there's no self-service promotion
# endpoint, by design):
#   docker exec -it user-db psql -U postgres -d userdb \
#     -c "UPDATE users SET role='ADMIN' WHERE username='admin_user';"

ADMIN_TOKEN=$(curl -s -X POST localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin_user","password":"admin-pass-1"}' | python3 -c "import sys,json;print(json.load(sys.stdin)['token'])")

# 3. Create a product as ADMIN
curl -X POST localhost:8080/api/products \
  -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"Wireless Mouse","price":19.99,"sku":"WM-001","category":"Electronics"}'

# 4. Stock it in inventory (productId = 1 from step 3) — also ADMIN-only
curl -X PUT "localhost:8080/api/inventory/1?quantity=100&warehouseLocation=HYD-1" \
  -H "Authorization: Bearer $ADMIN_TOKEN"

# 5. Place an order AS asha (userId=1, productId=1) — any authenticated customer, for their OWN userId
curl -X POST localhost:8080/api/orders \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"userId":1,"items":[{"productId":1,"quantity":2}]}'

# 6. Fetch the order — asha can see her own order; would get 403 for someone else's
curl -H "Authorization: Bearer $TOKEN" localhost:8080/api/orders/1
```

### Seeing authorization rules in action
- Omit the `Authorization` header entirely on any non-auth endpoint → `401
  Unauthorized` from `JwtAuthFilter`, before the request even reaches a
  service.
- Log in as `asha` and try `POST /api/orders` with someone else's `userId`
  → `403 Forbidden` from `order-service`'s ownership check.
- Try `PUT /api/inventory/{id}` with a CUSTOMER token → `403 Forbidden` from
  `inventory-service`'s role check.

### Seeing resilience in action
- Stop `inventory-service` and repeat step 5 — you should see repeated
  `Retry` attempts in `order-service`'s logs, then a `CircuitBreaker` opening
  after enough failures (watch: `localhost:8084/actuator/circuitbreakers`),
  and finally a clean `503` from the fallback method instead of a hang.
- Hit any gateway route ~25 times in 10 seconds — you'll get `429 Too Many
  Requests` from the custom in-memory rate limiter (`InMemoryRateLimiterFilter`).

---

## 6. Key patterns explained

### Database-per-service
Each service owns its schema exclusively (`userdb`, `productdb`,
`inventorydb`, `orderdb`) and never queries another service's tables
directly. Cross-service data needs go through synchronous API calls
(here, OpenFeign) — never through shared SQL joins. This is what lets each
team evolve their schema independently.

### Config Server
`config-server` serves files from `config-repo/` (using the `native`
profile — a real filesystem folder — instead of Git, to keep the demo
dependency-free). Each service's `spring.config.import` line
(`optional:configserver:http://localhost:8888`) pulls `application.yml`
(shared) + `<service-name>.yml` (specific) on startup. The `optional:`
prefix means services still boot even if the config server is down —
good for local dev, reconsider for production.

### Eureka Service Discovery
Every service registers itself under its `spring.application.name`. Other
services (and the Gateway) refer to it as `lb://service-name` or, for Feign,
`@FeignClient(name = "service-name")` — never a hardcoded host:port. This is
what lets you scale any service to N instances with zero config changes
elsewhere.

*(Swapping to Consul: replace `spring-cloud-starter-netflix-eureka-client`/
`-server` with `spring-cloud-starter-consul-discovery`, point at a running
Consul agent, and delete the `eureka:` config blocks — the `@FeignClient` and
`lb://` usages don't change at all, since they target Spring Cloud's
abstract discovery layer, not Eureka specifically.)*

### API Gateway
Routes are declared in `gateway-service/application.yml`. Each route:
- resolves its target via Eureka (`lb://...`)
- is wrapped in a Resilience4j `CircuitBreaker` gateway filter with a
  `fallbackUri` so a dead service returns a friendly 503 instead of a raw
  connection error
- passes through the custom `InMemoryRateLimiterFilter` (a `GlobalFilter`)
  before any routing happens

### Authentication & Authorization
- **user-service** is the identity provider. `POST /api/auth/register` hashes
  the password with BCrypt (`spring-boot-starter-security`'s
  `BCryptPasswordEncoder`) and stores the user; both `register` and
  `POST /api/auth/login` return a signed JWT (`JwtUtil`, using `io.jsonwebtoken`)
  containing `userId` and `role` as claims.
- **gateway-service**'s `JwtAuthFilter` (a `GlobalFilter`, order `0`, runs
  right after the rate limiter) is the **only** place the token's signature
  and expiry are checked. Every route except `/api/auth/**` requires
  `Authorization: Bearer <token>`; anything invalid or missing gets a `401`
  before it ever reaches a downstream service.
- On success, the filter decodes the claims and **overwrites** the request's
  `X-User-Id` / `X-User-Role` headers with the verified values — so even if
  a client tried to send fake values in those headers themselves, they're
  discarded. Downstream services never re-verify the JWT; they trust these
  two headers because they trust that only the gateway can set them.
- Each business service enforces its **own** authorization rule from those
  headers: `product-service`/`inventory-service` require `X-User-Role: ADMIN`
  for writes; `order-service` requires the caller's `X-User-Id` to match the
  order's `userId` (or `ADMIN`) for placing/viewing orders.
- **order-service** calls `product-service`/`inventory-service` on the
  customer's behalf while placing an order — and those two services enforce
  role checks too. So `FeignAuthHeaderInterceptor` copies the *original*
  `X-User-Id`/`X-User-Role` from the inbound request onto every outgoing
  Feign call order-service makes, otherwise those headers would be silently
  dropped and every internal call would look unauthenticated.
- The signing secret (`app.jwt.secret`) lives once in `config-repo/application.yml`
  (shared config) so both `user-service` (signs) and `gateway-service`
  (verifies) always agree on the exact same value. Swap that block for a
  real secrets manager reference before deploying anywhere real.
- This is the common **"trust the edge"** model: only the gateway is
  internet-facing, and everything behind it trusts requests that arrive with
  those two headers set. Its main risk is that anything that CAN reach a
  business service directly (a compromised container on the same network,
  a mis-scoped security group) bypasses auth entirely — some teams add a
  second layer of JWT verification in each service for defense-in-depth;
  that's a straightforward extension of `JwtAuthFilter`'s logic if you want it.

### OpenFeign
`order-service` declares one interface per downstream dependency
(`UserServiceClient`, `ProductServiceClient`, `InventoryServiceClient`).
`@FeignClient(name = "...")` is enough — Feign resolves the actual
host:port through Eureka + client-side load balancing automatically.

### Resilience4j
Applied at **two layers** in this project, deliberately, so you can see both styles:
1. **Gateway layer** — coarse-grained, per-route `CircuitBreaker` (declarative YAML filter)
2. **Order-service layer** — fine-grained, per-dependency `@CircuitBreaker` +
   `@Retry` + `@Bulkhead` annotations on individual methods in `OrderService.java`,
   each with its own fallback method and its own tuned thresholds in
   `application.yml`.

   - **CircuitBreaker** — stops calling a dependency that's already failing,
     so failures don't cascade or pile up latency.
   - **Retry** — retries a handful of times with exponential backoff for
     transient blips (a dropped connection, a momentary timeout).
   - **Bulkhead** — caps how many concurrent calls can be in-flight to a
     given dependency, so one slow downstream service can't exhaust all of
     order-service's threads.

   Feign fallback classes (`*ClientFallback`) also exist as a second safety
   net at the client level — in a real system you'd typically pick one layer
   (usually the Resilience4j annotations, since they're more configurable)
   rather than stacking both; both are included here so you can compare them.

---

## 7. Extending this project

Natural next exercises once you're comfortable with the above:
- Add Spring Cloud Bus + `/actuator/refresh` (or a Kafka-based bus) so
  config changes propagate to running services without restarts.
- Add distributed tracing (Micrometer Tracing + Zipkin/Tempo) so you can see
  a single order's request travel across all four services.
- Replace synchronous Feign calls for order confirmation with an
  event-driven flow (Kafka/RabbitMQ) — e.g. `order-service` publishes
  `OrderPlaced`, `inventory-service` consumes it and reserves stock
  asynchronously, publishing `StockReserved`/`StockRejected` back.
- Swap the in-memory rate limiter for Spring Cloud Gateway's built-in
  `RedisRateLimiter` so limits are enforced consistently if you run more
  than one gateway instance.
- Add a refresh-token flow (short-lived access token + longer-lived refresh
  token) instead of the single 1-hour token used here.
- Add an admin-only `POST /api/users/{id}/role` endpoint instead of promoting
  admins via direct SQL, and protect it the same way `create`/`delete` are.
- Add per-service JWT re-verification (defense-in-depth) instead of the
  "trust the edge" model, so a compromised internal network can't bypass auth.
