# Phase 12: Production Containerization, Docker Cluster, Rate Limiting & Zero-Downtime Infrastructure Runbook

## 1. Executive Summary
Phase 12 establishes enterprise production containerization, edge reverse proxy routing, DDoS shield rate limiting, and zero-downtime deployment pipelines for `maito-backend-monolith`. The architecture packages the Java 21 monolith using Spring Boot 3.3.x layered JAR extraction into an unprivileged, hardened Alpine Linux image, connects a production-grade Docker cluster (PostgreSQL 16, Redis 7, Nginx Gateway, Spring Boot App), protects the platform with a high-throughput Token Bucket rate limiter (`com.maito.gateway`), and exposes standardized Kubernetes liveness and readiness health probes (`/actuator/health/liveness`, `/actuator/health/readiness`).

---

## 2. Multi-Stage Hardened Dockerfile Architecture

### 2.1 Layered JAR Extraction Strategy
Spring Boot 3.3.x splits monolithic JAR artifacts into logical dependency cache layers. This optimizes Docker build cache reuse and dramatically minimizes CI/CD deployment push sizes.

- **Stage 1 (`builder`):** `eclipse-temurin:21-jdk-alpine`
  * Pre-caches Maven dependencies offline using `./mvnw dependency:go-offline`.
  * Compiles production executable with `./mvnw clean package -DskipTests`.
  * Extracts layers: `java -Djarmode=layertools -jar target/*.jar extract`.
- **Stage 2 (`runner`):** `eclipse-temurin:21-jre-alpine`
  * Creates unprivileged system user `appuser` (UID 10001) and group `appgroup` (GID 10001).
  * Copies layers in order of changing frequency:
    1. `dependencies/` (Rarely changes - cached)
    2. `spring-boot-loader/` (Rarely changes)
    3. `snapshot-dependencies/` (Internal shared libraries)
    4. `application/` (Changes per commit)
  * Sets `USER appuser` (Non-root execution principle).
  * JVM Container Optimization Flags:
    `-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError`
  * Entrypoint: `org.springframework.boot.loader.launch.JarLauncher`.

---

## 3. Production Docker Cluster Architecture

```
                                  [ Internet / Clients ]
                                             │
                                             ▼
                 +────────────────────────────────────────────────────────+
                 |             Nginx Alpine Reverse Proxy                 |
                 |  - Ports 80 / 443                                      |
                 |  - Wildcard Subdomain Mapping: (*.maito.io)            |
                 |  - Client IP & Real Protocol Forwarding                |
                 |  - 60s Upstream Timeouts & Gzip Compression            |
                 +────────────────────────────────────────────────────────+
                                             │
                                             ▼
                 +────────────────────────────────────────────────────────+
                 |           Maito Spring Boot Application                |
                 |  - Port 8080 (Internal Docker Network)                 |
                 |  - RateLimitingFilter (DDoS & Token Bucket Shield)     |
                 |  - TenantResolutionFilter & AbstractRoutingDataSource  |
                 |  - Actuator Probes: /liveness & /readiness             |
                 +────────────────────────────────────────────────────────+
                                │                          │
                                ▼                          ▼
        +───────────────────────────────+  +──────────────────────────────+
        |         PostgreSQL 16         |  |           Redis 7            |
        |  - Port 5432                  |  |  - Port 6379                 |
        |  - Master DB (maito_db)       |  |  - Routing Cache             |
        |  - Isolated Tenant DBs        |  |  - Rate Limiter Store        |
        |  - init-multi-tenant-dbs.sql  |  |  - Append-Only Persistence   |
        +───────────────────────────────+  +──────────────────────────────+
```

---

## 4. Edge Wildcard Subdomain & Dynamic Tenant Resolution

### 4.1 Nginx Subdomain Mapping
Nginx captures wildcard subdomains via PCRE regex and extracts the tenant slug dynamically:
```nginx
map $http_host $mapped_tenant {
    ~^(?<tenant>[a-zA-Z0-9_-]+)\.maito\.io$ $tenant;
    default "";
}
```

### 4.2 Header Precedence & Fallback Rules
1. If the client request explicitly sends `X-Tenant-ID`, Nginx forwards that value.
2. If omitted, Nginx injects `X-Tenant-ID: $mapped_tenant` from the subdomain.
3. If hitting raw IP or unmapped domains (e.g. `localhost`), Nginx falls back to `X-Tenant-ID: mito_crunch`.

---

## 5. Token Bucket Rate Limiting & DDoS Shield (`com.maito.gateway`)

### 5.1 Route Group Quota Matrix
| Route Group | Route Pattern | Burst Capacity | Refill Rate | HTTP Status |
|---|---|:---:|:---:|:---:|
| **AUTH** | `/api/v1/auth/**` | **10 tokens** | 5 tokens / min | 429 Too Many Requests |
| **CHECKOUT_ORDERS** | `/api/v1/checkout/**`, `/api/v1/b2b/bulk-orders/**` | **15 tokens** | 10 tokens / min | 429 Too Many Requests |
| **GENERAL** | `/api/**` | **120 tokens** | 60 tokens / min | 429 Too Many Requests |

### 5.2 Client Identity Partitioning
- Buckets are keyed by `Client IP + ":" + Route Group`.
- Real Client IP is extracted with proxy traversal: `X-Forwarded-For` (first IP) -> `X-Real-IP` -> `request.getRemoteAddr()`.

### 5.3 Depletion Response Headers
When rate limits are exceeded, the filter rejects requests immediately before authentication:
- `Status`: `429 Too Many Requests`
- `X-RateLimit-Limit`: Maximum burst capacity for route group.
- `X-RateLimit-Remaining`: `0`
- `Retry-After`: `60` (seconds)
- `Content-Type`: `application/json;charset=UTF-8`
- `Error Code`: `GATEWAY_4290` (`RATE_LIMIT_EXCEEDED`)

---

## 6. Container Health & Readiness Probes

### 6.1 Endpoints
- **Liveness Probe:** `GET /actuator/health/liveness`
  * Returns `{"status":"UP"}` when the JVM process is alive and internal event loops are responding.
- **Readiness Probe:** `GET /actuator/health/readiness`
  * Returns `{"status":"UP"}` when the database connection pools and routing registries are ready to serve incoming HTTP traffic.
- **Security Ingress:** Both endpoints are publicly accessible without JWT authentication in `SecurityConfig.java`.

---

## 7. Zero-Downtime Deployment & Operating Commands

### 7.1 Spin Up Local Docker Cluster
```bash
# 1. Copy environment template
cp .env.example .env

# 2. Build and launch all cluster containers in background
docker compose up -d --build

# 3. Verify health status
docker compose ps
```

### 7.2 Zero-Downtime Rolling Upgrade
```bash
# Build updated application container
docker compose build app

# Scale and rolling-replace backend instances
docker compose up -d --no-deps --scale app=2 app
docker compose up -d --no-deps --scale app=1 app
```