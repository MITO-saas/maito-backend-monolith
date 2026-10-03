# Maito E-Commerce - Modular Monolith Progress Tracker

| # | Status | Task | Verify / Check |
|---|--------|------|----------------|
| 1 | ✅ Done | Master workspace structure initialized | Project tree, Maven wrapper (`mvnw.cmd`), git setup |
| 2 | ✅ Done | Infrastructure & Local Database setup | PostgreSQL 16 `maito_db` with `maito_user` operational |
| 3 | ✅ Done | Spring Boot 3.3.4 parent POM, Web, JPA, Actuator initialized | Java 21, Spring Boot framework operational |
| 4 | ✅ Done | Liquibase database migration framework configured | `db/changelog/db.changelog-master.xml` with `01-init-schema.xml` |
| 5 | ✅ Done | Foundational Health & Help API (`/api/v1/health`, `/api/v1/help`) | Live endpoint, unit tests 100% passing, Swagger UI & OpenAPI live |
| 6 | ⏳ Awaiting PRD | Catalog Module (Products, Categories, SKUs, Inventory) | Ready to implement step-by-step upon PRD upload |
| 7 | ⏳ Awaiting PRD | Order Processing Module | Ready to implement step-by-step upon PRD upload |
| 8 | ⏳ Awaiting PRD | Payment & Logistics Integration Module | Ready to implement step-by-step upon PRD upload |

## Running the Application
```powershell
# Set JAVA_HOME to JDK 21
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"

# Run tests
.\mvnw.cmd test

# Run application locally
.\mvnw.cmd spring-boot:run
```
- Health Check: `http://localhost:8080/api/v1/health`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`