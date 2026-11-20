---
description: Run a per-module Code Owner architecture, SOLID/KISS, lint, and ABI review for changed modules
---

# Workflow: Module Code Owner Review (`/review-module`)

1. Identify all changed files via `git diff --name-only origin/main...HEAD` (or working tree changes).
2. Run deterministic architecture, wire-parity, BOM, and owner checks:
   ```bash
   ./scripts/check_brand_hygiene.sh
   ./scripts/check_architecture_and_owners.sh --pr-diff origin/main
   ```
3. Load each touched module's Code Owner persona from `agents/owners/<module>-owner.md` and `skills/abeta-kmp-module-review/SKILL.md`.
4. Execute each matched owner's `verification_command` (Spotless, Detekt, BCV `apiCheck`, unit/Compose tests, or Go `gofmt`/`go vet`/`go test -race`).
5. Review changed code for SOLID, Clean Code, KISS, security, and (for UI/platform files) `/impeccable` native iOS/Android craft guidelines.
6. Report concrete P0/P1/P2 findings with clickable file/line links or issue an explicit **APPROVED** verdict backed by observed command outputs.
