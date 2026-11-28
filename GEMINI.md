# GEMINI.md — Gemini, Antigravity & Android Studio AI Instructions for `abeta-dev/kmp`

This repository is **Abeta KMP Kit (`abeta-dev/kmp`)**, pairing a 4-layer Kotlin Multiplatform + Compose Multiplatform SDUI & Wearable Kit (`kmp/`, **11 publishable library modules** including Room 3 `database`, plus `kmp-bom` and `dependencyManagement`) with a Go 1.26.0 Clean Architecture SDUI BFF (`bff/`).

Read [AGENTS.md](AGENTS.md) for the authoritative architectural contract. This file adapts routing for **Gemini CLI**, **Gemini Code Assist**, **Gemini in Android Studio**, and **Antigravity**.

---

## 0. Mandatory Grounding Before Generation (Non-Negotiable)

Follow this exact 6-step grounding order before planning, designing, or editing code:

```text
AGENTS.md → Architecture Docs (docs/architecture.md) → Flow Docs (docs/flows/*.md) → Pattern Docs (docs/patterns/*.md) → CodeGraph Symbols (docs/codegraph/INDEX.md & symbols.json / ./scripts/generate_codegraph.py --query <symbol>) → Source Reads Only for Verification
```

- Start with [AGENTS.md](AGENTS.md) and [docs/architecture.md](docs/architecture.md), then trace runtime behavior via [docs/flows/README.md](docs/flows/README.md) and implementation recipes via [docs/patterns/README.md](docs/patterns/README.md).
- Query exact Kotlin/Go/Swift symbols via `./scripts/generate_codegraph.py --query <symbol>` (backed by [docs/codegraph/INDEX.md](docs/codegraph/INDEX.md) & `docs/codegraph/symbols.json`) before opening targeted source lines for verification.
- Keep CodeGraph fresh on public API edits (`./scripts/generate_codegraph.py && ./scripts/generate_codegraph.py --check`).

---

## Skill & Module Code Owner Routing

Before modifying any module, load its **Skill** (`skills/*/SKILL.md`) and its **Module Code Owner Agent** (`agents/owners/<module>-owner.md`):

| Area | Skill | Code Owner Persona |
| :--- | :--- | :--- |
| Monorepo navigation & cross-stack features | [skills/abeta-kmp-kit/SKILL.md](skills/abeta-kmp-kit/SKILL.md) | [agents/kmp-architect.md](agents/kmp-architect.md) |
| Repo usage & onboarding (`io.github.abeta-dev:bom`) | [skills/abeta-kmp-usage/SKILL.md](skills/abeta-kmp-usage/SKILL.md) | [agents/owners/bom-catalog-owner.md](agents/owners/bom-catalog-owner.md) |
| Demo & Reference apps (`sample-app` 7-in-1 Super-App Showcase, `storybook` 20 stories / 31 atoms) | [skills/abeta-kmp-demo/SKILL.md](skills/abeta-kmp-demo/SKILL.md) | [agents/owners/sample-app-owner.md](agents/owners/sample-app-owner.md), [agents/owners/storybook-owner.md](agents/owners/storybook-owner.md) |
| Native Android, Wear OS, iOS, watchOS, macOS, Web | [skills/abeta-kmp-platforms/SKILL.md](skills/abeta-kmp-platforms/SKILL.md) | [agents/owners/platforms-owner.md](agents/owners/platforms-owner.md), [agents/owners/core-owner.md](agents/owners/core-owner.md), [agents/owners/watch-companion-owner.md](agents/owners/watch-companion-owner.md) |
| Multiplatform Local Database (Room 3 + SQLite) | [skills/abeta-kmp-platforms/SKILL.md](skills/abeta-kmp-platforms/SKILL.md) | [agents/owners/database-owner.md](agents/owners/database-owner.md) |
| Design tokens, wire DTOs & stateless atoms | [skills/abeta-kmp-atomic/SKILL.md](skills/abeta-kmp-atomic/SKILL.md) | [agents/owners/design-tokens-owner.md](agents/owners/design-tokens-owner.md), [agents/owners/sdui-contracts-owner.md](agents/owners/sdui-contracts-owner.md), [agents/owners/ui-atoms-owner.md](agents/owners/ui-atoms-owner.md) |
| OCP Action Engine (18 built-in `ActionTypes`) | [skills/abeta-kmp-actions/SKILL.md](skills/abeta-kmp-actions/SKILL.md) | [agents/owners/action-engine-owner.md](agents/owners/action-engine-owner.md) |
| Go 1.26.0 SDUI BFF (`bff/`) | [skills/abeta-kmp-bff/SKILL.md](skills/abeta-kmp-bff/SKILL.md) | [agents/owners/bff-owner.md](agents/owners/bff-owner.md) |
| Architecture, SOLID/KISS, BOM & ABI | [skills/abeta-kmp-architecture/SKILL.md](skills/abeta-kmp-architecture/SKILL.md) | [agents/solid-reviewer.md](agents/solid-reviewer.md) |
| Per-module Code Owner PR reviews | [skills/abeta-kmp-module-review/SKILL.md](skills/abeta-kmp-module-review/SKILL.md) | [agents/owners/](agents/owners/) (17 owner personas) |
| CI/CD workflows, security & releases | [skills/abeta-kmp-cicd/SKILL.md](skills/abeta-kmp-cicd/SKILL.md) | [agents/owners/cicd-platform-owner.md](agents/owners/cicd-platform-owner.md) |

---

## Non-Negotiable Invariants

1. **Uncle Bob Dependency Rule**: `Layer 3 -> Layer 2 -> Layer 1 -> Layer 0`; Go `handler -> usecase -> domain <- infra`.
2. **SOLID, Clean Code & KISS**: Keep files cohesive (recommended production ceiling `<= 750 LOC`), register actions/snippets via OCP registries, inject dependencies via constructors/Koin.
3. **1:1 Wire Parity**: `kmp/sdui-contracts` + `kmp/action-engine` (`29` wire atoms incl. `MediaPagerAtom`, `CountdownAtom` w/ `expires_at_epoch_ms`, & 12 `ExtendedAtoms.kt`, `31` stateless UI atoms incl. `UiMediaPager` & real ISO/IEC 18004 `UiQrCode`, `23` snippets adding `nudge_bar`/`marquee_strip`/`order_tracker`/`composed_block` Atomic Snippet Molding Engine with `36` `ComposedNodeKind`s, `custom_slot`, & `graph_canvas`, `18` actions, `6` `SnippetDeltaOp`s, `7` ambient `MotionLoopEffect`s, `FloatingCartSpec` free-delivery threshold nudges) MUST match `bff/pkg/sdui/*` (`bff@v0.9.0`, 8 exportable packages incl. `cache` & `server`) and all **11** golden fixtures (`bff/testdata/golden/`), powering the 7-in-1 Super-App Showcase Launcher (`Abeta Fresh`, `Abeta Bites`, `Abeta Pulse`, `Abeta Mart`, `Abeta Spark`, `Abeta Trade`, `Abeta Pay`) and 20-story Storybook (8 UI Atom stories covering all 31 atoms, All 23 Snippets, Live 7-in-1 Super-App Demos in WasmJS browser, Animation & Nudges Lab, Atomic Snippet Crafter, Voice & Desktop OS Bridges, and End-to-End App Journeys).
4. **BOM, Catalog, Native Android `.aar` & ABI**: Never hardcode external versions in module `build.gradle.kts`; update `.api` baselines with `(cd kmp && ./gradlew apiDump)` across all **11 publishable modules** (`io.github.abeta-dev:*:0.8.0` + `bom` + `dependencies` = **13 Maven Central artifacts**, shipping native `-android` `.aar` libraries via AGP `9.5.3` `compileSdk = 37`, `minSdk = 24`, including Room 3 `database` schema v2 w/ `MIGRATION_1_2` & `DatabaseKeyValueStore` — see [docs/local-database-room3.md](docs/local-database-room3.md) and [docs/publishing-and-hosting.md](docs/publishing-and-hosting.md)).
5. **Android Studio & Apple Notes**:
   - Open `kmp/` as a Gradle project in Android Studio / IntelliJ IDEA (JDK 21, Android SDK 37).
   - Call `AbetaAndroidContext.initialize(applicationContext)` once at startup so native `androidMain` bridges (`core`, `state`, `database`, `network`, `sdui-layout`) activate automatically; optional host templates live under `kmp/platforms/{android,wearos}`.
   - Apple C/ObjC code using bit-width-varying types (`NSInteger`/`CFIndex`) lives in `apple64Main` + `watchosArm64Main` so HMPP metadata & Dokka compile cleanly.

## Verification Gates

```bash
./scripts/check_brand_hygiene.sh
./scripts/check_architecture_and_owners.sh
./scripts/generate_codegraph.py --check
./scripts/verify_all.sh
```
