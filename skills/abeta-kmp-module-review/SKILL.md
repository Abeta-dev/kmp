---
name: abeta-kmp-module-review
description: Per-module Code Owner review skill for abeta-dev/kmp. Maps any changed module to its dedicated agents/owners/<module>-owner.md persona and enforces SOLID, Clean Code, KISS, Uncle Bob Dependency Rule, lint, ABI, wire parity, and /impeccable native UI gates.
---

# Abeta KMP Kit — Module Code Owner Review Skill (`abeta-kmp-module-review`)

Use this skill whenever reviewing a pull request, diff, or refactor across one or more modules in `abeta-dev/kmp` (in Claude Code, Gemini, Antigravity, Cursor, Android Studio, Copilot, or CI).

## 1. Per-Module Code Owner Routing Table (17 Owners)

| Module / Path | Layer | Code Owner Agent Persona |
| :--- | :--- | :--- |
| `kmp/core/**` | Layer 0 | [agents/owners/core-owner.md](../../agents/owners/core-owner.md) |
| `kmp/state/**` | Layer 0 | [agents/owners/state-owner.md](../../agents/owners/state-owner.md) |
| `kmp/analytics/**` | Layer 0 | [agents/owners/analytics-owner.md](../../agents/owners/analytics-owner.md) |
| `kmp/database/**` | Layer 0 | [agents/owners/database-owner.md](../../agents/owners/database-owner.md) |
| `kmp/design-tokens/**` | Layer 1 | [agents/owners/design-tokens-owner.md](../../agents/owners/design-tokens-owner.md) |
| `kmp/sdui-contracts/**` | Layer 1 | [agents/owners/sdui-contracts-owner.md](../../agents/owners/sdui-contracts-owner.md) |
| `kmp/ui-atoms/**` | Layer 1 | [agents/owners/ui-atoms-owner.md](../../agents/owners/ui-atoms-owner.md) |
| `kmp/action-engine/**` | Layer 2 | [agents/owners/action-engine-owner.md](../../agents/owners/action-engine-owner.md) |
| `kmp/network/**` | Layer 2 | [agents/owners/network-owner.md](../../agents/owners/network-owner.md) |
| `kmp/sdui-layout/**` | Layer 3 | [agents/owners/sdui-layout-owner.md](../../agents/owners/sdui-layout-owner.md) |
| `kmp/watch-companion/**` | Layer 3 | [agents/owners/watch-companion-owner.md](../../agents/owners/watch-companion-owner.md) |
| `kmp/storybook/**` | Layer 3 | [agents/owners/storybook-owner.md](../../agents/owners/storybook-owner.md) |
| `kmp/sample-app/**` | Layer 3 | [agents/owners/sample-app-owner.md](../../agents/owners/sample-app-owner.md) |
| `kmp/platforms/**` | Layer 3 | [agents/owners/platforms-owner.md](../../agents/owners/platforms-owner.md) |
| `kmp/kmp-bom/**`, `kmp/dependencyManagement/**`, root Gradle | Build Platform | [agents/owners/bom-catalog-owner.md](../../agents/owners/bom-catalog-owner.md) |
| `bff/**` | Go 1.26.0 BFF | [agents/owners/bff-owner.md](../../agents/owners/bff-owner.md) |
| `.github/**`, `scripts/**`, `agents/**`, `skills/**`, IDE configs | CI/CD & Governance | [agents/owners/cicd-platform-owner.md](../../agents/owners/cicd-platform-owner.md) |

## 2. Standard 6-Step Code Owner Review Protocol

1. **Automated Pre-Flight**:
   ```bash
   ./scripts/check_brand_hygiene.sh
   ./scripts/check_architecture_and_owners.sh --pr-diff origin/main
   ```
2. **Load Matched Owners**: Read every `agents/owners/<module>-owner.md` persona matching changed paths.
3. **Architecture & Dependency Check**: Verify zero forbidden `projects.*` deps, zero Layer 1 `core`/Ktor/action imports, zero Compose UI in `watch-companion`, zero Gin/HTTP in `bff/internal/domain`.
4. **SOLID, Clean Code & KISS Check**:
   - **SRP**: One cohesive responsibility per file/class (`<= 750 LOC` prod file guardrail; Detekt `LongMethod <= 60`, `CyclomaticComplexMethod <= 14`, `TooManyFunctions <= 11`).
   - **OCP**: New actions via `ActionDecoderRegistry`/`ActionRegistry`, new snippets via `SnippetRegistry`, new analytics via decorators.
   - **LSP / ISP / DIP**: Narrow interfaces (`HapticEngine`, `ClipboardPort`, `ScreenRepository`), constructor/Koin injection, deterministic fakes in tests.
   - **KISS / DRY**: No copy-pasted logic blocks, no speculative abstractions, no silent catch blocks.
5. **Lint, ABI, Parity & Native UI Craft**:
   - Execute every matched owner's `verification_command` (Spotless, Detekt, BCV `apiCheck`, tests, Go `gofmt`/`go vet`/`go test -race`).
   - For UI/platform diffs (`design-tokens`, `ui-atoms`, `sdui-layout`, `storybook`, `platforms`), verify `/impeccable` native iOS/Android criteria (48dp/44pt touch targets, safe areas/IME insets, Dark Mode, Reduced Motion, VoiceOver/TalkBack roles & live regions).
6. **Structured Verdict**: Output P0 (Blocking), P1 (Major), P2 (Minor) findings with file:line links, or **APPROVED** backed strictly by observed command outputs.
