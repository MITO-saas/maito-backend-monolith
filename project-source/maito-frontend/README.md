# Maito Frontend: Server-Driven UI (SDUI) Client

Standalone, high-performance Server-Driven UI (SDUI) frontend for the Maito multi-tenant eCommerce platform.

## Architecture
- **SDUI Engine**: Dynamic rendering via `SDUIRenderer.tsx` based on backend declarative component trees.
- **Component Registry**:
  - `PromoStrip`: Top notification banner
  - `HeroCarousel`: Full-bleed responsive hero with direct-sourcing story
  - `FeaturedGrid`: Dynamic product catalog cards with live photos and cart triggers
  - `BrandStory`: Bihar / Mithila heritage editorial layout
  - `FooterLinks`: Multi-column social & compliance navigation
- **Theme Injection**: Automatic CSS variable binding from `theme.tokens` (`--color-primary`, `--font-heading`, etc.).
- **Zero External Dependencies**: All assets are resolved locally via `/assets/brands/{tenant_slug}/**`.

## Development
```bash
npm install
npm run dev
```
The Vite dev server runs at `http://localhost:3000` and proxies `/api` and `/assets` to the Spring Boot backend on port 8080.
