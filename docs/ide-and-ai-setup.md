# IDE & Multi-Model AI Setup Guide (`abeta-dev/kmp`)

`abeta-dev/kmp` ships first-class, zero-lock-in project configuration for **every major AI coding environment and LLM family**—anchored to one canonical source of truth ([AGENTS.md](../AGENTS.md), [agents/owners/](../agents/owners/), and [skills/](../skills/)) so rules never drift across tools.

---

## Mandatory Grounding Before Generation (6-Step Order)

Every IDE rule file (`AGENTS.md`, `GEMINI.md`, `CLAUDE.md`, `.cursor/rules/00-monorepo-core.mdc`, `.github/copilot-instructions.md`, `.junie/guidelines.md`, `.agent/rules/abeta-kmp-rules.md`) enforces the exact same 6-step grounding workflow before code generation:

```text
AGENTS.md → Architecture Docs (docs/architecture.md) → Flow Docs (docs/flows/*.md) → Pattern Docs (docs/patterns/*.md) → CodeGraph Symbols (docs/codegraph/INDEX.md & symbols.json / ./scripts/generate_codegraph.py --query <symbol>) → Source Reads Only for Verification
```

1. **Contract & Invariants**: [AGENTS.md](../AGENTS.md)
2. **Architecture & Boundaries**: [docs/architecture.md](architecture.md)
3. **End-to-End Runtime Flows**: [docs/flows/README.md](flows/README.md) (`screen-load-cache-and-manifest-flow.md`, `action-dispatch-and-offline-sync-flow.md`, `sse-streaming-and-delta-patch-flow.md`, `wearable-and-apple-host-flow.md`)
4. **Canonical Implementation Patterns**: [docs/patterns/README.md](patterns/README.md) (`sdui-atom-and-snippet-pattern.md`, `ocp-action-and-bridge-pattern.md`, `go-bff-cache-and-server-pattern.md`, `kmp-multiplatform-hmpp-and-abi-pattern.md`)
5. **CodeGraph Deterministic Symbol Index**: [docs/codegraph/INDEX.md](codegraph/INDEX.md) & [docs/codegraph/README.md](codegraph/README.md) — resolve any Kotlin, Go, or Swift symbol in milliseconds via `./scripts/generate_codegraph.py --query <symbol>` (CI-checked with `./scripts/generate_codegraph.py --check`)
6. **Targeted Source Reads**: Inspect exact `file:line` slices only to verify implementation details before editing.

---

## Supported IDEs, Agents & Model Families

| Tool / IDE / Model | Native Discovery Path(s) | Capabilities Provided |
| :--- | :--- | :--- |
| **Claude Code (Anthropic Claude)** | `CLAUDE.md`, `.claude/settings.json`, `.claude/commands/*.md`, `.claude/agents/*.md` | Slash commands (`/review-module`, `/verify-all`, `/add-sdui-feature`, `/impeccable-audit`), model-agnostic subagents (`module-owner-reviewer`, `architecture-auditor`, `bff-parity-reviewer`, `ui-impeccable-reviewer`), pre-approved safe verification commands |
| **Google Antigravity (Gemini & multi-model)** | `AGENTS.md`, `GEMINI.md`, `.agent/rules/abeta-kmp-rules.md`, `.agent/workflows/*.md`, `.antigravityignore` | Workspace architecture rules, automated workflows (`review-module`, `verify-all`, `add-screen-e2e`, `release-check`), token-optimized ignore rules |
| **Gemini CLI & Gemini Code Assist** | `GEMINI.md`, `.gemini/settings.json`, `.geminiignore` | Auto-loaded context for `GEMINI.md`, `AGENTS.md`, `skills/`, and `agents/owners/`, with `.geminiignore` FinOps filtering |
| **Android Studio (Gemini in Android Studio + JetBrains Junie / AI Assistant)** | `GEMINI.md`, `.junie/guidelines.md`, `.aiignore`, `AGENTS.md` | JDK 21 / Gradle 9.7.0 multiplatform setup, Android/Wear OS `kmp/platforms/` integration guidance, token-filtered `.aiignore` |
| **Cursor** | `.cursor/rules/00-monorepo-core.mdc` .. `50-cicd-owners-skills.mdc`, `.cursorignore` | Path-scoped glob rules for KMP layers, Atomic/Impeccable UI craft, Go BFF Clean Architecture, Platforms/Wearables, and CI/CD governance |
| **GitHub Copilot (VS Code, Android Studio, JetBrains, Xcode, GitHub PR)** | `.github/copilot-instructions.md`, `.github/instructions/{kmp,bff,cicd}.instructions.md` | Repository-wide instructions plus path-scoped instructions (`kmp/`, `bff/`, `.github/`/`scripts/`) |
| **OpenAI Codex & Any Model** | `AGENTS.md`, `skills/*/SKILL.md`, `agents/owners/*.md` | Universal open `AGENTS.md` + Agent Skills `SKILL.md` standard + optional CI LLM provider (`AI_REVIEW_PROVIDER=openai\|anthropic\|gemini`) |

---

## Android Studio & IntelliJ IDEA Setup

1. **Install JDK 21** (Temurin / Azul / JetBrains Runtime 21).
2. **Open `kmp/`** (or the repository root when working across both `kmp/` and `bff/`).
3. In **Settings -> Build, Execution, Deployment -> Build Tools -> Gradle**, set **Gradle JVM** to **JDK 21**.
4. **Local vs Published Dependency Mode**:
   - Default (`kmp/gradle.properties`): `abeta.kmpLocalBuild=true` substitutes `io.github.abeta-dev:*` with local `:module` projects for instant refactoring and navigation.
   - External BOM verification: run Gradle with `-Pabeta.kmpLocalBuild=false`.
5. **Android & Wear OS App Integration**:
   - All **11 publishable KMP modules** ship native `-android` `.aar` artifacts via `com.android.kotlin.multiplatform.library` `9.5.3` (`compileSdk = 37`, `minSdk = 24`, aligned by `io.github.abeta-dev:bom:0.8.0`). Ensure Android SDK 37 (`ANDROID_HOME`) is installed when building the KMP workspace locally.
   - Call `AbetaAndroidContext.initialize(applicationContext)` once at startup so native `androidMain` actuals activate immediately; optional host templates live under [`kmp/platforms/android/`](../kmp/platforms/android/) (`AndroidPlatformBridgeFactory.kt`, `AbetaAndroidHostActivity.kt`, `AndroidDatabaseFactory.kt`) and [`kmp/platforms/wearos/`](../kmp/platforms/wearos/) (`WearCompanionHostActivity.kt`, `WearSduiRenderer.kt`).
6. **Xcode / Apple Targets (macOS)**:
   - Assemble Apple frameworks sequentially to prevent Kotlin/Native LTO heap exhaustion:
     ```bash
     (cd kmp && ./gradlew :watch-companion:assembleAbetaWatchKitXCFramework :sdui-layout:assembleAbetaSduiKitXCFramework :storybook:assembleAbetaStorybookKitXCFramework --max-workers=1 --no-daemon -Dorg.gradle.jvmargs="-Xmx6g -Dfile.encoding=UTF-8" -Dkotlin.daemon.jvm.options="-Xmx6g")
     ```
   - Reference SwiftUI hosts live in [`kmp/platforms/apple/AbetaAppleHosts.swift`](../kmp/platforms/apple/AbetaAppleHosts.swift) and importable SwiftPM source targets live under [`kmp/platforms/apple/Sources/`](../kmp/platforms/apple/Sources/) (`AbetaSduiSwiftUI`, `AbetaWatchSwiftUI`).

---

## Complete Repository Skills Catalog (`skills/`)

| Skill | Purpose |
| :--- | :--- |
| [abeta-kmp-kit](../skills/abeta-kmp-kit/SKILL.md) | Monorepo umbrella router across all 11 publishable KMP modules & Go BFF |
| [abeta-kmp-usage](../skills/abeta-kmp-usage/SKILL.md) | Consuming via `io.github.abeta-dev:bom:0.8.0` + catalog (incl. native `*-android` `.aar`), wiring Koin/`SduiHostedRoot`, OCP extensions |
| [abeta-kmp-demo](../skills/abeta-kmp-demo/SKILL.md) | Complete 7-in-1 Super-App Showcase & 20-story End-to-End Storybook reference guide (`sample-app`, `storybook`, `bff`) |
| [abeta-kmp-platforms](../skills/abeta-kmp-platforms/SKILL.md) | Android, Wear OS, iOS/iPadOS, watchOS (`apple64Main`/`watchosArm64Main`), macOS, Desktop & WasmJS |
| [abeta-kmp-atomic](../skills/abeta-kmp-atomic/SKILL.md) | Design tokens, 29 wire atoms, 23 snippets (incl. `composed_block` w/ 36 `ComposedNodeKind`s, `custom_slot`, `graph_canvas`), 31 stateless Compose atoms & `/impeccable` craft |
| [abeta-kmp-actions](../skills/abeta-kmp-actions/SKILL.md) | 18 built-in OCP `ActionTypes`, decoders, handlers, `ActionRegistry`, `DefaultActionDispatcher` |
| [abeta-kmp-bff](../skills/abeta-kmp-bff/SKILL.md) | Go 1.26.0 Clean Architecture SDUI BFF (`bff@v0.9.0`), 8 `pkg/sdui/*` packages (`cache`, `server`, `molecules`, `analytics`, etc.), SSE streams, OpenAPI, 11 golden fixtures |
| [abeta-kmp-architecture](../skills/abeta-kmp-architecture/SKILL.md) | Uncle Bob Dependency Rule, SOLID/KISS, BOM/Catalog & Binary Compatibility Validator (`apiCheck`) |
| [abeta-kmp-module-review](../skills/abeta-kmp-module-review/SKILL.md) | Per-module Code Owner PR review workflow across all 17 `agents/owners/*` personas |
| [abeta-kmp-cicd](../skills/abeta-kmp-cicd/SKILL.md) | GitHub Actions workflows, automated architecture/owner gates, optional AI PR review & releases |
