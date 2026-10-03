# Maito Backend - AI Coding Guidelines and Strict Architecture Rules

1. Architecture Model: Strict Modular Monolith using Spring Boot 3.x and Java 21/17 under package root com.maito.*
2. Package Boundaries:
   - Modules (catalog, order, payment) must remain completely decoupled.
   - Cross-module operations must go through public Service interfaces/DTOs; never inject or query another module's Repository or JPA Entity directly.
3. Database-Driven Design:
   - Tables must be partitioned logically via prefixes (e.g., cat_products, ord_orders).
   - Every schema change requires a Liquibase changelog file inside src/main/resources/db/changelog/
4. Postman and Contracts:
   - Every exposed Controller must have a corresponding OpenAPI specification and exported Postman collection JSON.
5. State Tracking:
   - Update PROGRESS.md after every functional module or schema migration.
