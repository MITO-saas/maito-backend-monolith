# Enterprise Git Branching Strategy & Multi-Environment Workflow Policy

This document defines the MNC-grade Git branching strategy for the **Maito Platform**. It guarantees clean git hygiene, zero untested code in production, and linear, auditable releases.

---

## 1. Enterprise Branch Taxonomy & Environment Hierarchy

```text
[ Feature / Bugfix ] ──(PR)──> [ dev ] ──(Release Cut)──> [ test (QA) ] ──(UAT Promotion)──> [ uat (Client) ] ──(Prod Release)──> [ main / production ]
```

| Branch | Target Environment | Protection Level | Purpose & Merge Rules |
| :--- | :--- | :--- | :--- |
| **`production`** | **Production Live** | 🔒 **Locked** | Direct mirror of live production infrastructure. Synchronized with `main` upon successful production deployment. |
| **`main`** | **Production Release (Trunk)**| 🔒 **Strictly Protected** | Golden source trunk. Direct pushes forbidden. Zero raw direct code commits. Merges exclusively from `uat` via PR after full sign-off. Tagged with SemVer (`v1.0.0`). |
| **`uat`** | **User Acceptance Testing** | 🔒 **Protected** | Pre-production environment for client/business sign-off, stakeholder demos, and shadow verification. Merges only from `test`. |
| **`test`** | **QA / Automated Testing** | 🔒 **Protected** | Release candidate verification, automated regression suites, load testing, and integration testing. Merges from `dev`. |
| **`dev`** | **Development Integration** | 🔒 **Protected** | Central continuous integration branch. All feature branches merge here first via Pull Request with green CI. |
| **`feat/*`** | Local / Developer | 🔓 Feature Branch | Feature development (e.g. `feat/catalog-makhana`). Branches off `dev`, merges back into `dev` via PR. |
| **`fix/*`** | Local / Developer | 🔓 Bugfix Branch | Bug fixes identified during testing. Branches off `dev` or `test`, merges back into `dev`. |
| **`hotfix/*`** | Emergency Patch | 🔓 Hotfix Branch | Critical production incidents. Branches off `main`, merges into both `main` and `dev`. |

---

## 2. Multi-Tier MNC Workflow Lifecycle

```mermaid
gitGraph
   commit id: "v1.0.0 (Base)"
   branch dev
   checkout dev
   commit id: "chore: init foundation"
   branch feat/health-api
   checkout feat/health-api
   commit id: "feat: add health & help api"
   commit id: "test: verify health endpoint"
   checkout dev
   merge feat/health-api id: "PR to dev (Squash & Merge)"
   branch test
   checkout test
   merge dev id: "Promote to test (QA)"
   branch uat
   checkout uat
   merge test id: "Promote to uat (User Acceptance)"
   checkout main
   merge uat id: "Release to main (v1.1.0)"
   branch production
   checkout production
   merge main id: "Sync with live production"
```

---

## 3. Strict Rules & Regulations

### Rule 1: Zero Code Directly on `main` or `production`
- Direct `git push origin main` or `git push origin production` is **strictly prohibited**.
- All code originates in `feat/*` branches and flows linearly through `dev` -> `test` -> `uat` -> `main` / `production`.

### Rule 2: Conventional Commits Standard (v1.0.0)
Every commit message must be descriptive and follow conventional commits:
```text
<type>(<scope>): <short imperative description>

[optional body explaining WHAT and WHY]
```

**Permitted Types:**
- `feat`: New feature implementation (e.g., `feat(catalog): add SKU pricing service`)
- `fix`: Bug fix (e.g., `fix(order): resolve stock reservation lock`)
- `refactor`: Internal restructure without changing external behavior
- `test`: Adding or updating unit/integration tests
- `docs`: Documentation, API contracts, Postman collections
- `chore`: Maven POM updates, tooling, git hygiene

### Rule 3: Quality Gates Before PR Merge
No Pull Request may be merged into `dev` or higher without satisfying:
1. **Compilation:** `.\mvnw.cmd clean compile` succeeds with zero errors.
2. **Automated Tests:** `.\mvnw.cmd test` passes 100% (zero failures, zero errors).
3. **Database Migration:** Liquibase master changelog syntax verified.
4. **Documentation & Contracts:** OpenAPI (`v3/api-docs`), Swagger UI (`/swagger-ui.html`), `postman_collection.json`, and `API_CURLS.md` kept in 100% sync.
5. **No Premature Code:** Never introduce code or entities without an approved PRD.

### Rule 4: Clean Branching for Next Steps
- Every new feature will branch off the latest updated `dev`:
  ```bash
  git checkout dev
  git pull origin dev
  git checkout -b feat/<module-name>
  ```
- Upon completion and automated test pass, the branch is merged into `dev`, then promoted to `test` for validation.