---
description: Execute the complete abeta-dev/kmp verification pipeline across hygiene, architecture, KMP, headless demo, and Go BFF
---

# Workflow: Full Monorepo Verification (`/verify-all`)

1. Run the unified verification gate:
   ```bash
   ./scripts/verify_all.sh
   ```
2. For public API or documentation changes, also verify Dokka HTML docs:
   ```bash
   (cd kmp && ./gradlew dokkaGenerateHtml --no-daemon)
   ```
3. On macOS, when verifying Apple XCFrameworks or WasmJS web bundling, run sequentially to prevent Kotlin/Native LTO heap exhaustion:
   ```bash
   (cd kmp && ./gradlew :watch-companion:assembleAbetaWatchKitXCFramework :sdui-layout:assembleAbetaSduiKitXCFramework :storybook:assembleAbetaStorybookKitXCFramework :storybook:wasmJsBrowserProductionWebpack --max-workers=1 --no-daemon)
   ```
4. Report exact observed results and explicitly note any skipped host-dependent targets (e.g., Android SDK or Xcode simulator runtimes absent locally).
