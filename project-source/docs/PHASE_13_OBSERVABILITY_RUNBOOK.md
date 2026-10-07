# Phase 13: Observability, Metrics & Distributed Tracing Runbook

## 1. Executive Summary & Architecture Overview

Phase 13 establishes enterprise-grade, multi-tenant observability across the **Maito Backend Monolith** distributed architecture. Built on **Micrometer**, **Prometheus**, **Grafana**, and **Grafana Tempo (OpenTelemetry / Zipkin bridge)**, every operational and domain event is partitioned dynamically by tenant.

### Architecture Topology
```
┌─────────────────────────────────────────────────────────────────────────────┐
│                             INCOMING TRAFFIC                                │
│                   (Customers, Admin Portal, B2B HoReCa)                    │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │
                                       ▼
                     ┌───────────────────────────────────┐
                     │          Nginx Reverse Proxy       │
                     │  (Rate Limiting & Subdomain Host) │
                     └─────────────────┬─────────────────┘
                                       │
                                       ▼
                     ┌───────────────────────────────────┐
                     │     Maito Spring Boot Monolith    │
                     │ ┌───────────────────────────────┐ │
                     │ │ TenantMetricsFilter           │ │
                     │ │ (Tags: tenant=mito_crunch...) │ │
                     │ └───────────────┬───────────────┘ │
                     │                 ▼                 │
                     │ ┌───────────────────────────────┐ │
                     │ │ HikariPoolManager             │ │
                     │ │ (Tenant DB Connection Pools)  │ │
                     │ └───────────────┬───────────────┘ │
                     │                 ▼                 │
                     │ ┌───────────────────────────────┐ │
                     │ │ BusinessMetricsService        │ │
                     │ │ (Orders, GMV, 429s, Stock)    │ │
                     │ └───────────────┬───────────────┘ │
                     └───────┬───────────────────┬───────┘
                             │                   │
                  Metrics Scrape (/actuator/prometheus)   Distributed Tracing (Brave Zipkin)
                             │                   │
                             ▼                   ▼
                     ┌───────────────┐   ┌───────────────┐
                     │  Prometheus   │   │ Grafana Tempo │
                     │   (Port 9090) │   │ (Port 9411/   │
                     │               │   │       3200)   │
                     └───────┬───────┘   └───────┬───────┘
                             │                   │
                             └─────────┬─────────┘
                                       ▼
                             ┌───────────────────┐
                             │  Grafana Portal   │
                             │   (Port 3000)     │
                             │ Auto-provisioned  │
                             │    Dashboards     │
                             └───────────────────┘
```

---

## 2. Multi-Tenant Tagging Model

Every HTTP observation and timer metric is dynamically injected with the `tenant` tag:
- **Header Resolution**: `X-Tenant-ID`
- **Subdomain Resolution**: Hostnames like `mitocrunch.maito.io`, `vijiyasolar.maito.io`, `everrites.maito.io`
- **Fallback**: Untagged or internal system tasks resolve as `tenant="system"`.

### Key Observation Tags:
| Tag Key | Possible Values | Description |
|---|---|---|
| `tenant` | `mito_crunch`, `vijiyasolar`, `everrites`, `system` | Multi-tenant tenant identifier |
| `application` | `maito-backend-monolith` | Application identity |
| `environment` | `prod`, `dev`, `local` | Active profile context |
| `status` | `200`, `201`, `400`, `429`, `500`, etc. | HTTP response status code |
| `uri` | `/api/v1/orders`, `/api/v1/catalog/products`, etc. | Normalized HTTP route template |

---

## 3. Metrics Glossary

### A. HTTP & Core Observability Metrics
- **`http_server_requests_seconds_count`**: Total count of inbound HTTP requests partitioned by `tenant`, `method`, `status`, and `uri`.
- **`http_server_requests_seconds_sum`**: Total response duration in seconds.
- **`http_server_requests_seconds_bucket`**: Histogram latency buckets for SLA percentiles (50ms, 100ms, 250ms, 500ms, 1s, 2s).

### B. HikariCP Database Connection Pool Metrics
Connection pools managed by `HikariPoolManager` are instrumented via `MeterRegistry`:
- **`hikaricp_connections_active{pool="..."}`**: Active in-use JDBC connections.
- **`hikaricp_connections_idle{pool="..."}`**: Idle available JDBC connections in the pool.
- **`hikaricp_connections_pending{pool="..."}`**: Threads waiting for a connection from the pool.
- **`hikaricp_connections_timeout_total{pool="..."}`**: Count of connection checkout timeouts.
- **Instrumented Pools**: `db_mitocrunch`, `db_vijiyasolar`, `db_everrites`, `maito_db` (master).

### C. Domain & Business Custom Metrics
Managed by `BusinessMetricsService` (`com.maito.observability.service`):
- **`maito_orders_created_total`** (Counter):
  - Tags: `tenant`, `payment_method` (e.g. `WALLET`, `RAZORPAY`, `COD`, `B2B_CREDIT`), `status` (`SUCCESS`, `FAILED`).
- **`maito_gmv_revenue_total`** (Counter):
  - Tags: `tenant`.
  - Tracks cumulative Gross Merchandise Value in Indian Rupees (INR).
- **`maito_rate_limit_rejections_total`** (Counter):
  - Tags: `tenant`, `route_group` (`AUTH_LOGIN`, `CHECKOUT`, `DEFAULT`, etc.).
  - Incremented whenever a request is blocked with HTTP 429.
- **`maito_inventory_depletion_events`** (Counter):
  - Tags: `tenant`, `sku`.
  - Incremented on inventory stockouts or depletion below safe safety-stock threshold.
- **`maito_b2b_credit_used_gauge`** (Gauge):
  - Tags: `tenant`.
  - Real-time gauge of outstanding credit utilized by B2B wholesale buyers.

---

## 4. Distributed Tracing (Zipkin / Tempo Bridge)

Spring Boot 3.3.x distributed tracing is instrumented using **Micrometer Tracing Brave** and **Zipkin Reporter**:
- **Span Export Endpoint**: `http://tempo:9411/api/v2/spans` (configurable via `ZIPKIN_ENDPOINT`).
- **Trace Context Propagation**: W3C / B3 propagation headers passed across HTTP boundaries.
- **Tempo Integration**: Tempo ingests spans and allows Grafana to jump from Prometheus high-latency spikes directly into distributed traces via `tracesToLogs` and `serviceMap`.

---

## 5. Security & Least Privilege Access

Actuator endpoints are strictly governed by `SecurityConfig.java`:
1. **Public Endpoints**:
   - `/actuator/health/**` (Readiness and Liveness probes for Kubernetes / Docker swarm).
   - `/actuator/info`
2. **Secured Administrative Endpoints**:
   - `/actuator/prometheus`
   - `/actuator/metrics/**`
   - Authorized only for internal infrastructure calls (RFC1918 / Loopback / Docker overlay subnet) or authenticated operators holding `ROLE_ADMIN` / `ROLE_TENANT_ADMIN`.

---

## 6. Docker Cluster Deployment & Quickstart

To spin up the entire cluster including Prometheus, Tempo, and Grafana:
```bash
# 1. Start all infrastructure components
docker compose up -d

# 2. Verify containers are healthy
docker compose ps

# 3. Access Observability Web Interfaces:
# - Grafana Portal:     http://localhost:3000 (User: admin / Pass: admin)
# - Prometheus Portal:  http://localhost:9090
# - Tempo Tracing API:  http://localhost:3200 (or Zipkin ingester on 9411)
# - Backend Actuator:   http://localhost:8080/actuator/prometheus
```

### Provisioned Grafana Dashboard
Dashboard is auto-loaded at `/var/lib/grafana/dashboards/maito-executive-dashboard.json`:
1. **Panel 1**: Multi-Tenant Request Rate (RPS grouped by `tenant` & `status`).
2. **Panel 2**: P95 & P99 Latency Across Endpoints.
3. **Panel 3**: Rate Limiting Blocks (HTTP 429 trigger spikes).
4. **Panel 4**: Active vs. Idle DB Connections per HikariCP Pool.
5. **Panel 5**: JVM Memory Usage (Heap/Non-Heap) & GC Pause Rate.
6. **Panel 6**: Live Order Velocity & Cumulative Revenue Counter (₹).

---

## 7. Production Prometheus Alertmanager Rules

Save the following alert definitions to `/etc/prometheus/alertmanager-rules.yml`:

```yaml
groups:
  - name: maito_production_alerts
    rules:
      # Alert 1: High P99 Latency
      - alert: TenantEndpointP99HighLatency
        expr: histogram_quantile(0.99, sum(rate(http_server_requests_seconds_bucket[5m])) by (le, uri, tenant)) > 1.0
        for: 2m
        labels:
          severity: critical
          service: maito-monolith
        annotations:
          summary: "P99 latency exceeding 1.0s for tenant {{ $labels.tenant }} on route {{ $labels.uri }}"
          description: "Tenant {{ $labels.tenant }} experiencing degradation: {{ $value }}s on {{ $labels.uri }}."

      # Alert 2: Rate Limit Spike (DDoS or Brute-Force Attack)
      - alert: HighRateLimitRejectionSpike
        expr: sum by (tenant, route_group) (rate(maito_rate_limit_rejections_total[2m])) > 20
        for: 1m
        labels:
          severity: warning
        annotations:
          summary: "Rate limit spike detected on {{ $labels.route_group }} for {{ $labels.tenant }}"
          description: "Tenant {{ $labels.tenant }} has over 20 rejected requests/sec on {{ $labels.route_group }}."

      # Alert 3: Database Connection Pool Starvation
      - alert: HikariPoolExhaustion
        expr: hikaricp_connections_pending > 5
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "Database connection pool {{ $labels.pool }} exhausted"
          description: "Pool {{ $labels.pool }} has {{ $value }} threads pending connection checkout for > 1m."

      # Alert 4: JVM Heap Memory Critical
      - alert: JVMHeapPressureCritical
        expr: (jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"}) > 0.85
        for: 3m
        labels:
          severity: critical
        annotations:
          summary: "JVM Heap usage above 85%"
          description: "Heap utilization is {{ $value | humanizePercentage }}. Consider scaling or increasing heap."

      # Alert 5: Tenant Traffic Anomaly (Drop to Zero)
      - alert: TenantTrafficDrop
        expr: sum by (tenant) (rate(http_server_requests_seconds_count[10m])) == 0
        for: 15m
        labels:
          severity: warning
        annotations:
          summary: "Zero traffic detected for active tenant {{ $labels.tenant }}"
          description: "Tenant {{ $labels.tenant }} has received zero requests over the last 15 minutes."
```
