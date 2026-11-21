# Visual Showcase — Demo App & Storybook Screenshots

All screenshots below are **pixel-accurate Retina (2×) PNG captures** rendered directly by Compose Multiplatform (Skiko `ImageComposeScene`) from `kmp/sample-app` and `kmp/storybook` with zero mockups or hand-edited assets.

---

## 1. 6-in-1 Super-App Showcase Screenshots (`kmp/sample-app --verticals`)

Six distinct consumer and B2B verticals rendered from one shared Compose Multiplatform SDUI engine (`sdui-layout` + `ui-atoms` + `design-tokens`), powered by `search_bar`, `chip_group`, `swipe_deck`, `product_card`, responsive `grid`/`carousel`, `FloatingCartDockHost`, and adaptive theme presets (`EMERALD`, `AMBER`, `INDIGO`):

### Abeta Fresh — Quick-Commerce Grocery (Desktop `1280×860` Super-App Launcher + Adaptive Grid)
![Abeta Fresh Desktop](assets/screenshots/vertical-fresh-desktop.png)

### All 6 Vertical Showcase Apps — Mobile Phone Form Factor (`412×915`)

| Abeta Fresh (`fresh-home` · 8-Min Grocery) | Abeta Bites (`bites-home` · Food Delivery) | Abeta Pulse (`pulse-home` · Events & Movies) |
| :---: | :---: | :---: |
| ![Abeta Fresh Phone](assets/screenshots/vertical-fresh-phone.png) | ![Abeta Bites Phone](assets/screenshots/vertical-bites-phone.png) | ![Abeta Pulse Phone](assets/screenshots/vertical-pulse-phone.png) |

| Abeta Mart (`mart-home` · Retail Marketplace) | Abeta Spark (`spark-deck` · Swipe Discovery) | Abeta Trade (`trade-home` · B2B Wholesale & RFQ) |
| :---: | :---: | :---: |
| ![Abeta Mart Phone](assets/screenshots/vertical-mart-phone.png) | ![Abeta Spark Phone](assets/screenshots/vertical-spark-phone.png) | ![Abeta Trade Phone](assets/screenshots/vertical-trade-phone.png) |

---

## 2. Sample App — Core Kit & Adaptive SDUI (`kmp/sample-app`)

### Abeta Fresh Express Store (`store-home` — Desktop Split-Pane, 4-Column Responsive Grid & Floating Cart Dock)
![Abeta Fresh Desktop Split-Pane](assets/screenshots/demo-store-home-desktop.png)

### Mobile Phone Form Factor (`store-home` — 412dp Compact 2-Column Grid, Scrollable Tabs, Light & Dark Mode)

| Light Mode (Phone `412×915`) | Dark Mode (Phone `412×915`) |
| :---: | :---: |
| ![Abeta Fresh Phone Light](assets/screenshots/demo-store-home-phone.png) | ![Abeta Fresh Phone Dark](assets/screenshots/demo-store-home-dark.png) |

### Wearable Glanceable Form Factor (`watch-status` — `320×390` Dark Mode)
![Watch Status Glance](assets/screenshots/demo-watch-status.png)

### Workspace Home, Nested Containers & Declarative Validated Forms

| Home Dashboard (`home`) | Nested Containers (`containers`) |
| :---: | :---: |
| ![Demo Home Desktop](assets/screenshots/demo-home-desktop.png) | ![Demo Containers Desktop](assets/screenshots/demo-containers-desktop.png) |

![Demo Form Validation](assets/screenshots/demo-form-demo-desktop.png)

---

## 3. Interactive Storybook (`kmp/storybook` — 13 Stories Across Desktop, WasmJS, macOS & iOS)

### Design Tokens & WCAG AA Contrast Specimen Board
![Storybook Design Tokens](assets/screenshots/storybook-design-tokens.png)

### All 17 SDUI Snippets Gallery (Dark Mode)
![Storybook All 17 Snippets Dark](assets/screenshots/storybook-snippets-dark.png)

### Motion Presets & Abeta Fresh Quick-Commerce Sandbox
![Storybook Motion and Store](assets/screenshots/storybook-motion-store.png)

### Interactive Form Controls, Avatars & Shimmer Skeletons
![Storybook Controls and Atoms](assets/screenshots/storybook-controls-atoms.png)

### Responsive Form-Factor Breakpoints & Live Core OS Bridges
![Storybook Responsive and OS Bridges](assets/screenshots/storybook-responsive-bridges.png)

---

## 4. Regenerating Screenshots Locally

```bash
# Export all 7 Core Demo screenshots (Desktop, Phone Light/Dark, Watch)
(cd kmp && ./gradlew :sample-app:run --args="--capture-screenshots=../docs/assets/screenshots")

# Export all 6-in-1 Super-App Showcase screenshots (Phone & Desktop across all 6 verticals)
(cd kmp && ./gradlew :sample-app:run --args="--capture-verticals=../docs/assets/screenshots")

# Export all 5 Storybook screenshots (Tokens, Controls, 17 Snippets Dark, Motion/Store, Responsive/Bridges)
(cd kmp && ./gradlew :storybook:run --args="--capture-screenshots=../docs/assets/screenshots")

# Optional: capture screenshots directly against a live Go BFF (bff/ or Abeta-dev/sdui-bff)
(cd bff && go run ./cmd/bff) &
(cd kmp && ./gradlew :sample-app:run --args="--bff-url=http://localhost:8080 --capture-verticals=../docs/assets/screenshots")
```
