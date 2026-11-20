---
description: Run per-module Code Owner architecture, SOLID/KISS, lint, ABI, and parity review on current changes
---

1. Run deterministic hygiene and architecture/owner checks:
   - `./scripts/check_brand_hygiene.sh`
   - `./scripts/check_architecture_and_owners.sh --pr-diff origin/main`
2. Read `skills/abeta-kmp-module-review/SKILL.md` and every matched module owner agent under `agents/owners/<module>-owner.md`.
3. Run each matched module's `verification_command` (Spotless, Detekt, `apiCheck`, tests, or Go vet/race tests).
4. Review diffs for Uncle Bob Dependency Rule, SOLID, Clean Code, KISS, wire parity, and (for UI/platform files) `/impeccable` native iOS/Android craft guidelines.
5. Report P0/P1/P2 findings with file/line references or an explicit APPROVED verdict backed by observed command output.
