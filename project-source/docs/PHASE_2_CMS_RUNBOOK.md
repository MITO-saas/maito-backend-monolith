# Maito Multi-Tenant SaaS Platform: Phase-2 CMS & Server-Driven UI (SDUI) Runbook
**System**: `maito-backend-monolith`  
**Phase**: Phase 2 ("Headless CMS & Server-Driven UI Engine")  
**Branch**: `feat/cms-sdui-engine`  
**Baseline**: Sealed Phase 1 (`v1.0.0-phase1-core`)  
**Target Audience**: Principal Architects, Front-End Infrastructure Engineers, Full-Stack Developers

---

## 1. Executive Architecture: Server-Driven UI (SDUI) Engine

In the Maito enterprise architecture, storefront experiences (Web Next.js, React Native, Mobile Apps) are completely decoupled from hardcoded page layouts. The backend acts as an **SDUI Orchestrator**, delivering a compiled declarative component tree that clients render deterministically based on:
1. `component_type`: A strictly registered UI component identifier (e.g., `PROMO_STRIP`, `HERO_CAROUSEL`, `FEATURED_GRID`, `BRAND_STORY`, `FOOTER_LINKS`).
2. `content_payload`: A flexible JSONB structure carrying UI copy, CTA links, media assets, and display parameters.
3. `visibility_rules`: Runtime evaluation parameters (date windows, client device, geo-targeting) evaluated dynamically per request.
4. `brand_tokens`: Tenant-specific design tokens (colors, typography, border radii) injected alongside layout data to guarantee isolated styling.

```
  +-----------------------------------------------------------------------------------+
  |                                Storefront Client                                  |
  |                           (Next.js / React / Flutter)                             |
  +-----------------------------------------+-----------------------------------------+
                                            |
                         GET /api/v1/cms/pages/home
                         Header: X-Tenant-ID: mito_crunch
                                            |
                                            v
  +-----------------------------------------------------------------------------------+
  |                             TenantResolutionFilter                                |
  |              (Binds tenant context 'mito_crunch' to TenantContextHolder)          |
  +-----------------------------------------+-----------------------------------------+
                                            |
                                            v
  +-----------------------------------------------------------------------------------+
  |                             CmsLayoutServiceImpl                                  |
  |   1. Check Redis Cache: tenant:mito_crunch:cms:home:en                            |
  |   2. If Cache Miss / Redis Offline -> Fallback to PostgreSQL Tenant Database      |
  |   3. Dynamic Routing: DynamicTenantRoutingDataSource -> db_mitocrunch             |
  |   4. Evaluate Visibility Rules (device, date windows)                             |
  |   5. Assemble PageLayoutResponse (SEO + Active Theme Tokens + Sorted Sections)    |
  +-----------------------------------------+-----------------------------------------+
                                            |
                                            v
  +-----------------------------------------------------------------------------------+
  |                          Physical Tenant DB: db_mitocrunch                        |
  |                     cms_pages | cms_sections | cms_themes                         |
  +-----------------------------------------------------------------------------------+
```

---

## 2. SDUI Component Registry & JSON Schemas

### 2.1 PROMO_STRIP
Announcement or countdown banner pinned to the top of the viewport.
```json
{
  "text": "Free Delivery on orders above ₹499 | Use Code: CRUNCHFREE",
  "backgroundColor": "#0F172A",
  "textColor": "#F8FAFC",
  "link": "/promotions/crunchfree"
}
```

### 2.2 HERO_CAROUSEL
High-impact image or video carousel with responsive headlines and action buttons.
```json
{
  "slides": [
    {
      "title": "Farm-Fresh Roasted Jumbo Makhana",
      "subtitle": "100% Organic, Handpicked from Mithila Wetlands",
      "ctaText": "Shop Collection",
      "ctaLink": "/collections/makhana",
      "imageUrl": "https://cdn.mitocrunch.in/banners/hero1.webp"
    }
  ]
}
```

### 2.3 FEATURED_GRID
Card grid displaying trending products, categories, or featured highlights.
```json
{
  "heading": "Trending Flavors",
  "items": [
    { "name": "Peri Peri Crunch", "tag": "Best Seller", "price": 199 },
    { "name": "Himalayan Pink Salt", "tag": "Classic", "price": 189 },
    { "name": "Mint Magic", "tag": "Popular", "price": 199 }
  ]
}
```

### 2.4 BRAND_STORY
Rich editorial section communicating heritage, mission, and direct-sourcing proof points.
```json
{
  "heading": "From Pond to Pack",
  "body": "Directly sourced from Bihar farmers, roasted with zero trans-fats.",
  "badge": "100% Traceable"
}
```

### 2.5 FOOTER_LINKS
Structured legal, policy, and social navigation links.
```json
{
  "social": {
    "instagram": "@mitocrunch",
    "twitter": "@mitocrunch_in"
  },
  "policies": [
    "Privacy Policy",
    "Terms of Service",
    "Shipping & Returns"
  ]
}
```

---

## 3. Complete API Catalog & Operational Contracts

### 3.1 Storefront: Fetch Published Page Layout
- **URL**: `GET /api/v1/cms/pages/{slug}`
- **Headers**:
  - `X-Tenant-ID`: `mito_crunch` (or resolved via Host header domain)
  - `Accept-Language`: `en` (optional)
- **Response (`200 OK`)**:
```json
{
  "success": true,
  "data": {
    "pageSlug": "home",
    "title": "Mito Crunch - Farm Fresh Foxnuts",
    "seo": {
      "metaTitle": "Mito Crunch | Premium Roasted Foxnuts & Snacks",
      "metaDescription": "Authentic Mithila makhana sourced directly from farmers."
    },
    "theme": {
      "themeName": "DEFAULT_THEME",
      "tokens": {
        "primaryColor": "#D97706",
        "fontHeadings": "Cabinet Grotesk",
        "fontBody": "Inter",
        "borderRadiusPx": 8
      }
    },
    "sections": [
      {
        "sectionId": "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d",
        "componentType": "PROMO_STRIP",
        "displayOrder": 1,
        "contentPayload": {
          "text": "Free Delivery on orders above ₹499 | Use Code: CRUNCHFREE",
          "backgroundColor": "#0F172A",
          "textColor": "#F8FAFC"
        }
      }
    ]
  },
  "error": null,
  "timestamp": "2026-10-05T03:30:00Z"
}
```
- **cURL Command**:
```bash
curl -X GET http://localhost:8080/api/v1/cms/pages/home \
  -H "X-Tenant-ID: mito_crunch" \
  -H "Accept: application/json"
```

---

### 3.2 Admin: Create / Update Page Layout
- **URL**: `POST /api/v1/admin/cms/pages`
- **Headers**:
  - `X-Tenant-ID`: `mito_crunch`
  - `Content-Type`: `application/json`
- **Request Body**:
```json
{
  "pageSlug": "summer-sale",
  "title": "Summer Makhana Bonanza",
  "seoMetadata": {
    "metaTitle": "Summer Sale | Mito Crunch",
    "metaDescription": "Up to 30% off on all roasted jumbo makhana."
  },
  "isPublished": true,
  "sections": [
    {
      "componentType": "HERO_CAROUSEL",
      "displayOrder": 1,
      "isActive": true,
      "visibilityRules": {
        "devices": ["MOBILE", "DESKTOP"]
      },
      "contentPayload": {
        "slides": [
          {
            "title": "Summer Bonanza",
            "ctaText": "Grab Deal",
            "ctaLink": "/collections/summer"
          }
        ]
      }
    }
  ]
}
```
- **cURL Command**:
```bash
curl -X POST http://localhost:8080/api/v1/admin/cms/pages \
  -H "X-Tenant-ID: mito_crunch" \
  -H "Content-Type: application/json" \
  -d '{"pageSlug":"summer-sale","title":"Summer Makhana Bonanza","seoMetadata":{"metaTitle":"Summer Sale"},"isPublished":true,"sections":[]}'
```

---

### 3.3 Admin: Hot-Update Section Payload & Cache Invalidation
- **URL**: `PUT /api/v1/admin/cms/sections/{sectionId}`
- **Headers**:
  - `X-Tenant-ID`: `mito_crunch`
  - `Content-Type`: `application/json`
- **Request Body**:
```json
{
  "displayOrder": 1,
  "isActive": true,
  "visibilityRules": {
    "devices": ["MOBILE", "DESKTOP"]
  },
  "contentPayload": {
    "text": "Flash Sale: 50% Off Peri Peri Makhana!",
    "backgroundColor": "#DC2626",
    "textColor": "#FFFFFF"
  }
}
```
- **cURL Command**:
```bash
curl -X PUT http://localhost:8080/api/v1/admin/cms/sections/44444444-4444-4444-4444-444444444441 \
  -H "X-Tenant-ID: mito_crunch" \
  -H "Content-Type: application/json" \
  -d '{"displayOrder":1,"isActive":true,"visibilityRules":{},"contentPayload":{"text":"Flash Sale: 50% Off!"}}'
```

---

## 4. Multi-Tenant CMS Isolation Verification

To verify that Tenant A and Tenant B maintain complete physical database isolation with independent CMS layouts:

### Step 1: Query Tenant A (`mito_crunch`)
```bash
curl -X GET http://localhost:8080/api/v1/cms/pages/home -H "X-Tenant-ID: mito_crunch"
```
*Result*: Fetches layout from `db_mitocrunch` with brand tokens `#D97706` and 5 seed sections.

### Step 2: Query Tenant B (`vijiya_solar`)
```bash
curl -X GET http://localhost:8080/api/v1/cms/pages/home -H "X-Tenant-ID: vijiya_solar"
```
*Result*: Returns isolated layout or 404 if not yet configured in `db_vijiya_solar`.
Changes to `mito_crunch` never affect `vijiya_solar`.

---

## 5. Resilient Cache-Aside Architecture (Redis Offline Fallback)
1. **Cache Key Pattern**: `tenant:{tenantId}:cms:{pageSlug}:{locale}`
2. **TTL**: 15 minutes.
3. **Resilience Invariant**: All Redis operations are guarded with `try-catch`. If Redis is down, the service catches the connection exception, queries PostgreSQL directly, and returns the response without latency spikes or user-facing errors.

---

## 6. 10-Year Media & Static Asset Storage Architecture

To eliminate reliance on volatile external CDNs that can rot, fail offline, or suffer network partitioning, Maito implements a storage-agnostic, localized asset architecture.

### 6.1 Directory & Brand Taxonomy
Static assets are organized by brand identity directly within the monolith resources:
```
src/main/resources/static/assets/brands/mito_crunch/
├── banners/
│   ├── hero_roasted_makhana.webp    [Hero Carousel Primary Image]
│   └── promo_holi_sale.webp         [Story / Heritage Sourcing Image]
├── products/
│   ├── makhana_peri_peri.webp       [Featured Grid Item 1]
│   ├── makhana_himalayan_salt.webp  [Featured Grid Item 2]
│   └── makhana_mint_magic.webp      [Featured Grid Item 3]
└── brand/
    └── logo_crunch.svg              [Vector Brand Identity Logo]
```

### 6.2 HTTP Caching & Edge Propagation
Spring Boot's `WebMvcConfig` maps `/assets/**` directly to `classpath:/static/assets/`:
```java
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/assets/**")
                .addResourceLocations("classpath:/static/assets/")
                .setCacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePublic());
    }
}
```

### 6.3 Live Visual Storefront Rendering ASCII Mockup (http://localhost:8080)
```
+---------------------------------------------------------------------------------------+
|  [OFFER] Free Delivery on orders above ₹499 | Use Code: CRUNCHFREE                [X]  |  <- PromoStrip (SDUI)
+---------------------------------------------------------------------------------------+
|  [LOGO: MITO CRUNCH]        Trending Flavors | Our Roots | Wetlands    [Tenant: mito] (0) |  <- Header Nav
+---------------------------------------------------------------------------------------+
|                                                                                       |
|  [100% Traceable Mithila Foxnuts]                  +-------------------------------+  |
|  FARM-FRESH ROASTED JUMBO MAKHANA                 | [PHOTO: Crispy Roasted        |  |  <- HeroCarousel (SDUI)
|  100% Organic, Handpicked from Mithila Wetlands.  |  Makhana Bowl on Dark Slate]  |  |     (/assets/.../hero.webp)
|  Roasted with zero trans-fats.                    |                               |  |
|                                                   +-------------------------------+  |
|  [Shop Collection ->]   [Explore Origins]                                            |
|                                                                                       |
+---------------------------------------------------------------------------------------+
|                                  TRENDING FLAVORS                                     |
|  Slowly roasted to perfection with cold-pressed olive oil & natural Himalayan herbs.  |
|                                                                                       |
|  +--------------------+    +--------------------+    +--------------------+           |
|  | [PHOTO: Peri Peri] |    | [PHOTO: Pink Salt] |    | [PHOTO: Mint Magic]|           |  <- FeaturedGrid (SDUI)
|  | Peri Peri Crunch   |    | Himalayan Pink Salt|    | Mint Magic         |           |     (/assets/.../products/)
|  | ₹199  [Add to Cart]|    | ₹199  [Add to Cart]|    | ₹199  [Add to Cart]|           |
|  +--------------------+    +--------------------+    +--------------------+           |
+---------------------------------------------------------------------------------------+
|                                                                                       |
|  FROM POND TO PACK                                 +-------------------------------+  |
|  Directly sourced from Bihar farmers,              | [PHOTO: Festive Celebration & |  |  <- BrandStory (SDUI)
|  roasted with zero trans-fats.                     |  Mithila Wetland Sourcing]    |  |     (/assets/.../promo.webp)
|  [100% Organic Harvest]  [Zero Preservatives]      +-------------------------------+  |
+---------------------------------------------------------------------------------------+
|  MITO CRUNCH - Farm Fresh Makhana                  Policies: Privacy | Terms | Refund  |  <- FooterLinks (SDUI)
|  (c) 2026 Mito Crunch. 30-Year High Availability Standards.                           |
+---------------------------------------------------------------------------------------+
```

---

## 7. Single Runnable Monolith Deployment & Execution Guide

The entire Maito platform (Spring Boot 3.3 backend + PostgreSQL dynamic routing + Server-Driven UI engine + localized static media pipeline + responsive visual storefront) builds into a single self-contained runnable JAR.

### 7.1 Unified Maven Packaging
Execute from the monorepo root:
```powershell
cmd.exe /c "set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot&& mvnw.cmd clean package -DskipTests=false"
```
- Validates the complete 34-test regression suite.
- Assembles `src/main/resources/static/` (HTML + WebP banners + product photos + brand vectors) into `BOOT-INF/classes/static/`.
- Outputs fat JAR at: `target/maito-backend-monolith-0.0.1-SNAPSHOT.jar` (~92.6 MB).

### 7.2 Booting the Monolithic Artifact
Run directly with Java 21 LTS:
```powershell
& "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot\bin\java.exe" -jar target/maito-backend-monolith-0.0.1-SNAPSHOT.jar
```

### 7.3 Live Access Endpoints
1. **Interactive Visual Storefront**: Open your browser at `http://localhost:8080/`.
2. **Server-Driven UI Layout API**: `curl -X GET http://localhost:8080/api/v1/cms/pages/home -H "X-Tenant-ID: mito_crunch"`.
3. **Local Media Asset Delivery**: `curl -I http://localhost:8080/assets/brands/mito_crunch/banners/hero_roasted_makhana.webp`.
4. **OpenAPI / Swagger UI Documentation**: `http://localhost:8080/swagger-ui/index.html`.
