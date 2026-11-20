# Visual Showcase — Demo App & Storybook Screenshots

All screenshots below are **pixel-accurate Retina (2×) PNG captures** rendered directly by Compose Multiplatform (Skiko `ImageComposeScene`) from `kmp/sample-app` and `kmp/storybook` with zero mockups or hand-edited assets.

---

## 1. Sample App — Quick-Commerce & Adaptive SDUI (`kmp/sample-app`)

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

## 2. Interactive Storybook (`kmp/storybook` — 12 Stories Across Desktop, WasmJS, macOS & iOS)

### Design Tokens & WCAG AA Contrast Specimen Board
![Storybook Design Tokens](assets/screenshots/storybook-design-tokens.png)

### All 14 SDUI Snippets Gallery (Dark Mode)
![Storybook All 14 Snippets Dark](assets/screenshots/storybook-snippets-dark.png)

### Motion Presets & Abeta Fresh Quick-Commerce Sandbox
![Storybook Motion and Store](assets/screenshots/storybook-motion-store.png)

### Interactive Form Controls, Avatars & Shimmer Skeletons
![Storybook Controls and Atoms](assets/screenshots/storybook-controls-atoms.png)

### Responsive Form-Factor Breakpoints & Live Core OS Bridges
![Storybook Responsive and OS Bridges](assets/screenshots/storybook-responsive-bridges.png)

---

## 3. Regenerating Screenshots Locally

```bash
# Export all 7 Demo screenshots (Desktop, Phone Light/Dark, Watch)
(cd kmp && ./gradlew :sample-app:run --args="--capture-screenshots=../docs/assets/screenshots")

# Export all 5 Storybook screenshots (Tokens, Controls, 14 Snippets Dark, Motion/Store, Responsive/Bridges)
(cd kmp && ./gradlew :storybook:run --args="--capture-screenshots=../docs/assets/screenshots")

# Optional: capture Demo screenshots directly against a live Go BFF
(cd bff && go run ./cmd/bff) &
(cd kmp && ./gradlew :sample-app:run --args="--bff-url=http://localhost:8080 --capture-screenshots=../docs/assets/screenshots")
```
