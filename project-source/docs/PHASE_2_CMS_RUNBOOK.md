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
