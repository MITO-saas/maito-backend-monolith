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
   - Keep PROGRESS.md in sync after every functional module or schema migration.
   - Use `python scripts/status_verifier.py --auto` to reconcile the Status column with the
     repository — never hand-edit the Status column. Read PROGRESS.md before planning the next
     piece of work; it is the source of truth for what is done vs. pending.
