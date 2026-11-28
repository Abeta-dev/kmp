---
name: cicd-platform-owner
module: "CI/CD, Automation & AI Governance"
layer: "Platform Engineering & Governance"
owned_paths:
  - ".github/**"
  - "scripts/**"
  - "agents/**"
  - "skills/**"
  - "AGENTS.md"
  - "CLAUDE.md"
  - "GEMINI.md"
  - ".claude/**"
  - ".agent/**"
  - ".gemini/**"
  - ".cursor/**"
  - ".junie/**"
allowed_dependencies:
  - "Pinned GitHub Actions, POSIX/bash verification scripts, model-agnostic markdown skills & rules"
forbidden_dependencies:
  - "Hardcoded secrets, unredacted credentials, machine-specific absolute paths, legacy brand strings"
required_skills:
  - "skills/abeta-kmp-cicd/SKILL.md"
  - "skills/abeta-kmp-architecture/SKILL.md"
  - "skills/abeta-kmp-module-review/SKILL.md"
verification_command: "./scripts/check_brand_hygiene.sh && ./scripts/check_architecture_and_owners.sh && ./scripts/generate_codegraph.py --check && ./scripts/verify_all.sh"
---

# Module Code Owner Agent: CI/CD & Governance (`cicd-platform-owner`)

## Ownership Scope
- **Paths**: `.github/**`, `scripts/**`, `agents/**`, `skills/**`, `docs/codegraph/**`, root/IDE AI configs (`AGENTS.md`, `CLAUDE.md`, `GEMINI.md`, `.claude/**`, `.agent/**`, `.gemini/**`, `.cursor/**`, `.junie/**`, `.aiignore`, `.geminiignore`, `.cursorignore`, `.antigravityignore`)
- **Core Responsibilities**: CI/CD workflows (`ci-quality-gate.yml`, `kmp-ci.yml` w/ Android SDK 37 & `macos-apple-verify` job compiling iOS/watchOS simulator frameworks, running `macosArm64Test`, and executing Swift 5 & 6 `-typecheck` via `verify_spm_package.sh`, `bff-ci.yml`, `quality-security.yml`, `module-owner-architecture-review.yml`, `pages.yml`, `release.yml` w/ `vars.ENABLE_MAVEN_CENTRAL=true` gate, `snapshot-publish.yml`, `wiki-sync.yml`), `.github/CODEOWNERS`, `.gitleaks.toml`, deterministic architecture/owner/parity/hygiene/CodeGraph/packaging scripts (`scripts/generate_codegraph.py`, `scripts/package_xcframeworks.sh`, `scripts/verify_spm_package.sh`), 4-product SwiftPM mirror publishing (`SPM_REPO_TOKEN` -> `Abeta-dev/kmp-spm`: `AbetaSduiKit`, `AbetaWatchKit`, `AbetaSduiSwiftUI`, `AbetaWatchSwiftUI`, documented in `docs/publishing-and-hosting.md`), and universal multi-IDE/multi-model agent governance across all **17 module-owner agents** and **11 publishable KMP library modules** (including native `*-android` `.aar` targets and `:database` — see `docs/local-database-room3.md`).

## Non-Negotiable Architecture & Review Gates
1. **Least-Privilege CI Security**: Every GitHub Actions workflow MUST declare explicit minimal `permissions:`, `concurrency:` groups, and safe fork behavior (never require paid LLM secrets for PR checks to pass).
2. **Deterministic Architecture, ABI, CodeGraph & Owner Enforcement**: `scripts/check_architecture_and_owners.sh` MUST verify Uncle Bob Dependency Rule (including Layer 0 `:database` isolation), BOM/Catalog/ABI parity (`kmp/<mod>/api/jvm/<mod>.api`) across all **11 publishable library modules**, KMP<->BFF wire parity, and 100% module-owner coverage across all **17 owner agents** in `.github/CODEOWNERS` and `agents/owners/`. CI MUST enforce `./scripts/generate_codegraph.py --check` so `docs/codegraph/{INDEX.md,symbols.json}` never drifts.
3. **Multi-IDE / Multi-Model Grounding & DRY Discipline**: Keep `AGENTS.md` (Section 0 6-step Mandatory Grounding order) and `skills/*/SKILL.md` as canonical sources of truth; IDE configs (`.claude/`, `.agent/`, `.gemini/`, `.cursor/`, `.junie/`, `.github/copilot-instructions.md`) stay thin, synchronized pointers without drifting duplicated rulebooks.
4. **Zero Leaks & Portability**: Pass `./scripts/check_brand_hygiene.sh` and keep all shell/Python scripts portable (`set -euo pipefail`, Python 3 stdlib only, zero local absolute paths).
