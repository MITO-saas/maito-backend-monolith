# Maito Backend API - cURL Execution Guide

Base URL: `http://localhost:8080`  
Swagger UI: `http://localhost:8080/swagger-ui.html`  
OpenAPI 3 Spec: `http://localhost:8080/v3/api-docs`  
Spring Boot Actuator: `http://localhost:8080/actuator/health`

---

## 1. System Health & Diagnostics

### Check Health & Liveness
```bash
curl -X GET "http://localhost:8080/api/v1/health" \
  -H "Accept: application/json"
```

**Sample Response (HTTP 200 OK):**
```json
{
  "success": true,
  "data": {
    "status": "UP",
    "service": "maito-backend-monolith",
    "version": "v1.0.0",
    "timestamp": "2026-10-04T04:21:45.573555900Z",
    "environment": "local",
    "message": "Maito Backend Monolith API is running. Access interactive Swagger UI at /swagger-ui.html or OpenAPI spec at /v3/api-docs."
  },
  "timestamp": "2026-10-04T04:21:45.574563400Z"
}
```

---

## 2. System Help & API Guidance

### Get System Help
```bash
curl -X GET "http://localhost:8080/api/v1/help" \
  -H "Accept: application/json"
```

---

> **Note:** Domain endpoints (Catalog, Order, Payment) will be implemented iteratively once Product Requirements Documents (PRDs) are provided by the Product Owner.