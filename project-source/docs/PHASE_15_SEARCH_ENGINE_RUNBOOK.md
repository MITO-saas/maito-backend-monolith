# Phase 15: Elasticsearch 8.x Full-Text Catalog Search & Faceted Engine Runbook

## 1. Overview & Architecture
Phase 15 upgrades the Maito platform's catalog discovery mechanism from single-table SQL ILIKE pattern queries to an Amazon/Flipkart-grade full-text search engine powered by Elasticsearch 8.x.

### Core Capabilities
- **Multi-Tenant Index Isolation**: Dedicated versioned index `{tenant_slug}_products_v1` aliased to `{tenant_slug}_products`.
- **Edge-Ngram Autocomplete Analyzer**: Instant search-as-you-type tokenization (min_gram: 2, max_gram: 20) with lowercase and asciifolding filters.
- **Typo Tolerance & Fuzzy Search**: Multi-match queries with `fuzziness: "AUTO"` allowing single-edit transpositions (e.g., "pari peri" matches "Peri Peri").
- **Dynamic Faceted Aggregations**: Real-time term aggregations for brands and categories, accompanied by min, max, and avg price statistics.
- **High Availability & SQL Fail-Safe**: Automatic graceful fallback to optimized PostgreSQL queries when Elasticsearch is offline, disabled, or unreachable.
- **Real-Time Dual-Write & Event Sync**: Spring Transactional Event Listeners (`AFTER_COMMIT`) for `ProductCreatedEvent`, `ProductUpdatedEvent`, `ProductDeletedEvent`, and `StockAdjustedEvent`.
- **Batch Administrative Re-Indexing**: Endpoint `POST /api/v1/admin/search/reindex` restricted to `ROLE_TENANT_ADMIN`.

---

## 2. Infrastructure & Configuration

### Docker Compose Service
```yaml
elasticsearch:
  image: docker.elastic.co/elasticsearch/elasticsearch:8.13.4
  container_name: maito-elasticsearch
  restart: unless-stopped
  environment:
    - discovery.type=single-node
    - xpack.security.enabled=false
    - ES_JAVA_OPTS=-Xms512m -Xmx512m
  ports:
    - "9200:9200"
  volumes:
    - maito_elasticsearch_data:/usr/share/elasticsearch/data
  healthcheck:
    test: ["CMD-SHELL", "curl -s http://localhost:9200/_cluster/health | grep -q 'green\\|yellow'"]
    interval: 10s
    timeout: 5s
    retries: 10
    start_period: 15s
```

### Application Properties
```yaml
maito:
  search:
    elasticsearch:
      enabled: ${ELASTICSEARCH_ENABLED:true}
      uris: ${ELASTICSEARCH_URIS:http://localhost:9200}
      connection-timeout-ms: 3000
      socket-timeout-ms: 5000
```

---

## 3. Custom Analyzers & Mappings Specification

### Edge-Ngram Analyzer
- **Tokenizer**: `edge_ngram` (min_gram: 2, max_gram: 20, token_chars: ["letter", "digit"])
- **Filter**: `lowercase`, `asciifolding`
- **Search Analyzer**: `standard` with `lowercase` and `asciifolding` (prevents query string expansion).

### Mappings
- `name`: Multi-field (`text` analyzed with `edge_ngram_analyzer`, `keyword` for exact matching/sorting, `standard` for traditional text search).
- `brand`: Multi-field (`keyword` for aggregation, `text` for fuzzy search).
- `categorySlug`, `categoryName`: `keyword` for high-speed faceted grouping.
- `price`, `mrp`: `double` for range queries and price stats aggregations.
- `attributes`: `flattened` for flexible JSON attribute filtering.

---

## 4. API Endpoints & Verification

### Storefront Search: `GET /api/v1/search/products`
- **Query Parameters**:
  - `q`: Search terms (e.g., `pari peri`)
  - `category`: Category slug (e.g., `roasted-makhana`)
  - `brand`: Brand name (e.g., `Mito Crunch`)
  - `minPrice`, `maxPrice`: Price range boundary filters
  - `inStock`: Boolean flag for availability
  - `sort`: `relevance`, `price_asc`, `price_desc`, `rating`, `newest`
  - `page`, `size`: Pagination parameters

### Autocomplete Suggestions: `GET /api/v1/search/suggest`
- **Query Parameters**:
  - `q`: Prefix query string (e.g., `peri`)
- **Response**: Top 5 suggestions with text, slug, price, and primary thumbnail image.

### Admin Re-Indexing: `POST /api/v1/admin/search/reindex`
- **Authentication**: JWT with `ROLE_TENANT_ADMIN` or `ROLE_ADMIN`.
- **Action**: Iterates over all active products in tenant DB, provisions index if missing, and syncs documents.

---

## 5. Verification Commands

### Run Full Regression Suite
```bash
cmd.exe /c "set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot&& mvnw.cmd clean test"
```
All 176 tests passing (0 failures, 0 errors, 0 skipped).
