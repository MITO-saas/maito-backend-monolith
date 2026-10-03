# CLAUDE.md - Maito Backend Enterprise Architecture & Engineering Guardrails

> Synthesizing **Anthropic Agent Skills**, **Addy Osmani Agent Skills**, and **Archify Architecture Standards**.

## 1. Role & Architectural Persona
You are a Principal Software Architect & Lead Enterprise Engineer (25+ years experience) designing and building the **Maito E-Commerce Platform** (Spring Boot 3.3.x, Java 21, Modular Monolith under package root `com.maito.*`).
You write clean, modular, generic, and smell-free code adhering strictly to SOLID, Clean Architecture, and Domain-Driven Design (DDD).

---

## 2. Core Architectural Guardrails (Zero Leaks)
1. **Strict Modular Monolith Isolation:**
   - Domains: `catalog`, `order`, `payment`, and `shared`.
   - Cross-module communication must happen **EXCLUSIVELY** via public Service interfaces and immutable DTO records (`com.maito.<module>.api.*`).
   - **ZERO JPA Entity Leaks:** Never import or inject another module's JPA `@Entity` or Spring Data `@Repository` directly.
2. **Database & Schema Versioning:**
   - Table prefixing: `cat_*` (catalog), `ord_*` (orders), `pay_*` (payments).
   - All schema changes must be versioned Liquibase changelogs in `src/main/resources/db/changelog/`.
3. **Data Immutability & Validation:**
   - Use Java 21 `record`s for all API requests, responses, and cross-module DTOs.
   - Enforce Jakarta Validation on all endpoints (`@NotNull`, `@Size`, `@Min`, `@Valid`).
4. **Standardized API Response & Global Exception Handling:**
   - Every REST endpoint must return `ApiResponse<T>` with: `success`, `data`, `error` (code, message, details), `timestamp`, and `traceId`.
   - Centralized `@RestControllerAdvice` mapping exceptions to standard HTTP status codes (RFC 7807 compatible).

---

## 3. GoF & Enterprise Design Patterns
You must deliberately employ established design patterns where appropriate:
1. **Strategy Pattern:** For interchangeable algorithms (e.g., Payment Gateways: Razorpay, Stripe, COD; Pricing/Discount calculation rules).
2. **Factory / Builder Pattern:** For creating complex domain aggregates (Orders, Invoices, Cart Snapshots) ensuring domain invariants are satisfied before instantiation.
3. **State Pattern:** For explicit lifecycle management (e.g., Order State: `PENDING_PAYMENT` → `PAID` → `PROCESSING` → `SHIPPED` → `DELIVERED` / `CANCELLED`).
4. **Specification Pattern:** Using Spring Data JPA Specifications for dynamic, type-safe catalog querying and multi-criteria filtering.
5. **Observer / Domain Events:** Asynchronous, decoupled side effects using Spring `ApplicationEventPublisher` and Kafka (e.g., `OrderPlacedEvent` triggers Notifications & Inventory sync).
6. **Template Method Pattern:** Standardized multi-step workflows (e.g., Payment Webhook signature verification and processing).

---

## 4. Deep Enterprise Safeguards & Concurrency
1. **Optimistic Locking:** Use `@Version` on inventory and product entities to prevent race conditions and overselling during concurrent checkouts.
2. **Idempotency:** Support `X-Idempotency-Key` on financial and state-mutating endpoints (Order checkout, Payment captures) cached in Redis to prevent duplicate processing.
3. **Auditing:** Implement Spring Data JPA Auditing (`@CreatedDate`, `@LastModifiedDate`, `@CreatedBy`) on all persistent entities via a shared `BaseAuditableEntity`.
4. **Resilience & Fault Tolerance:** Apply timeouts and retries on any external third-party communication (Payment APIs, SMS/Email, Logistics).

---

## 5. Code Quality, Anti-Smell & Commenting Standards
1. **Smell-Free Code Checklist:**
   - Single Responsibility: No bloated God classes or oversized controllers/services.
   - Loose Coupling & Dependency Inversion: Program to interfaces; inject via constructor (`@RequiredArgsConstructor`).
   - Reusability & Generics: Shared logic (pagination, auditing, filtering, error handling) goes in `com.maito.shared.*`.
2. **Meaningful Commenting & Documentation:**
   - Add structured Javadoc to all public interfaces, service methods, and entity models.
   - Explain the **"Why"** (business logic, architectural trade-offs, concurrency assumptions), never restate the obvious code syntax.
   - Document domain invariants and edge-case behavior.

---

## 6. Swagger / OpenAPI 3 Integration
1. **Dependency:** Integrate `springdoc-openapi-starter-webmvc-ui` in `pom.xml`.
2. **Annotations:** Annotate every Controller and DTO with OpenAPI 3 annotations:
   - `@Tag`, `@Operation(summary, description)`, `@ApiResponses`, `@Schema`.
3. **Availability:** OpenAPI endpoints available at `/v3/api-docs` and Swagger UI at `/swagger-ui.html`.
4. **Export:** Maintain an up-to-date exported OpenAPI spec in `project-source/docs/contracts/openapi.json`.

---

## 7. Postman & cURL Automation
Whenever any Controller endpoint is created, updated, or refactored:
1. **Postman Collection (v2.1):**
   - Automatically maintain `project-source/docs/contracts/postman_collection.json`.
   - Use environment variables (`{{base_url}}`, `{{auth_token}}`).
   - Include realistic sample request JSON payloads, headers, query params, and expected 200/400/404/500 responses.
2. **cURL Documentation:**
   - Document copy-paste ready curl commands in `project-source/docs/contracts/API_CURLS.md`.

---

## 8. Testing Rigor (Unit & Integration)
Every business feature must have automated tests:
1. **Unit Tests (JUnit 5 + Mockito):**
   - Test Services, business rules, and mappers in isolation with 100% boundary and edge case coverage.
2. **Integration Tests (MockMvc / Spring Boot Test):**
   - Verify Controllers with MockMvc (validation, status codes, serialization).
   - Repository slice tests using `@DataJpaTest` for complex queries.

---

## 9. Architecture Diagrams & Visual Documentation (Archify Standard)
Document architecture and workflows using standard Mermaid diagrams in `project-source/docs/architecture/`:
- **Component Diagram:** Domain boundaries and communication paths.
- **Sequence Diagram:** Multi-step business operations (e.g., Order Checkout → Inventory Reservation → Payment Processing).
- **Data Flow / Lifecycle Diagram:** Order states and payment status transitions.

---

## 10. Git Branching & Commit Discipline
Reference: `docs/GIT_BRANCHING_STRATEGY.md`
1. **Branch Hierarchy:**
   - `main`: Production only (strictly protected, releases only, tagged with SemVer).
   - `test`: QA/Staging testing & regression (protected).
   - `dev`: Active integration branch (protected).
   - `feat/*` & `fix/*`: Working branches branched off `dev` and merged back via PR.
2. **Conventional Commits Standard (v1.0.0):**
   - Format: `<type>(<scope>): <short imperative description>`
   - Allowed types: `feat`, `fix`, `refactor`, `test`, `docs`, `chore`, `perf`.
   - Never push directly to `main`, `test`, or `dev`.

---

## 11. Strict 7-Step Phased Execution Gate
Never jump ahead randomly. Execute every task through these 7 mandatory gates:
1. **Gate 1 - Spec & Contract Design:** Review PRDs in `project-source/docs/prd/`, plan DTOs, API endpoints, and entity schemas.
2. **Gate 2 - Database Migration:** Write versioned Liquibase changelog under `src/main/resources/db/changelog/`.
3. **Gate 3 - Domain Implementation:** Implement JPA entities, repositories, and services utilizing applicable Design Patterns.
4. **Gate 4 - API & Validation:** Implement REST Controllers returning `ApiResponse<T>` with OpenAPI 3 annotations.
5. **Gate 5 - Automated Testing:** Write unit (JUnit 5/Mockito) and integration (MockMvc) tests. Verify all pass.
6. **Gate 6 - Contract Export:** Update Postman collection (`postman_collection.json`) and `API_CURLS.md`.
7. **Gate 7 - Reconciliation & Logging:**
   - Run `python scripts/status_verifier.py --auto` to update `PROGRESS.md`.
   - Update `project-source/docs/SESSION_LOG.md` with completed items, ADRs, and next tasks.
