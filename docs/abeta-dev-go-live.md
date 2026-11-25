# `abeta.dev` Go-Live Checklist (Maven Central + Custom Domains)

All code, signing keys, secrets, CI/CD release workflows, Apple XCFrameworks, SwiftPM mirror (`Abeta-dev/kmp-spm`), public multi-arch GHCR container images, Go modules, and GitHub Pages portal are already wired and live.

When you are ready to point **`abeta.dev`** DNS live, complete these 3 quick steps:

---

## 1. Sonatype Central Portal Namespace (`io.github.abeta-dev` Verified & Live)

Everything required for Maven Central signing and upload is already configured in `Abeta-dev` Organization Secrets (`Public repositories`) and live under the verified **`io.github.abeta-dev`** Maven Central namespace (Kotlin package namespace remains `dev.abeta.kmp.*`):
- `MAVEN_CENTRAL_USERNAME` & `MAVEN_CENTRAL_PASSWORD` (already verified — authenticated cleanly)
- `GPG_SIGNING_KEY`, `GPG_SIGNING_KEY_ID` (`84338C10`), `GPG_SIGNING_PASSWORD` (RSA-4096 fingerprint `33D7EE077BD5BFE065907588D71E873484338C10`, already published & verified on `keyserver.ubuntu.com` and `keys.openpgp.org`)

*(Optional)* If you later wish to also verify a custom `dev.abeta` DNS namespace on Sonatype Central Portal:
1. Open **https://central.sonatype.com/publishing/namespaces** and click **Add Namespace**.
2. Enter **`dev.abeta`** and copy the verification TXT token.
3. Add the TXT record at your `abeta.dev` DNS provider and click **Verify Namespace** in Central Portal.

---

## 2. Publish `io.github.abeta-dev:bom:0.7.0` + All 11 KMP Libraries (1 Click)

1. In `Abeta-dev/kmp` -> **Settings -> Secrets and variables -> Actions -> Variables**, add:
   - `ENABLE_MAVEN_CENTRAL` = `true` (mandatory gate in `release.yml` enabling automatic Maven Central publishing on all future `v*` tags)
2. Go to **Actions -> Release -> Run workflow**:
   - `publish_target`: **`maven-central`**
   - `version_override`: **`0.7.0`**
3. Click **Run workflow**. Within ~12 minutes, `io.github.abeta-dev:bom:0.7.0`, `io.github.abeta-dev:dependencies:0.7.0`, and all 11 KMP libraries (`core`, `state`, `analytics`, `database`, `design-tokens`, `sdui-contracts`, `ui-atoms`, `action-engine`, `network`, `sdui-layout`, `watch-companion` — 13 Maven Central artifacts total) will be live on `mavenCentral()`.

---

## 3. Optional Custom Domains on `abeta.dev` (\$0 TLS via GitHub Pages / Render)

| Subdomain | Target | Setup |
| :--- | :--- | :--- |
| `docs.abeta.dev` (or `kmp.abeta.dev`) | GitHub Pages Portal, WasmJS Storybook & Dokka | Add `CNAME` -> `abeta-dev.github.io` and set Custom Domain in `Abeta-dev/kmp` -> Settings -> Pages |
| `studio.abeta.dev` | `Abeta-dev/layout-engine` (`/studio` + `/llms.txt`) | Deploy via `render.yaml` (or Docker `ghcr.io/abeta-dev/layout-engine:0.4.0`) and bind custom domain |
| `api.abeta.dev` | `Abeta-dev/sdui-bff` (7 vertical apps + `/docs` + `/llms.txt`) | Deploy via `render.yaml` (or Docker `ghcr.io/abeta-dev/sdui-bff:0.4.0`, `LAYOUT_ENGINE_URL=https://studio.abeta.dev`) and bind custom domain |
