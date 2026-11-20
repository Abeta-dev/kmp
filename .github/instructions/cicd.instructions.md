---
applyTo: "{.github/**,scripts/**,agents/**,skills/**}"
---

# CI/CD, Code Owners & Skills Guidelines (`abeta-dev/kmp`)

- Load `agents/owners/cicd-platform-owner.md` and `skills/abeta-kmp-cicd/SKILL.md`.
- Keep workflows least-privilege with explicit `permissions:` and `concurrency:`.
- Never require paid LLM API keys for mandatory PR verification jobs; optional AI review steps must skip cleanly when secrets are absent.
- Every KMP/BFF module must have a dedicated owner persona in `agents/owners/` and matching entry in `.github/CODEOWNERS`.
- Run `./scripts/check_brand_hygiene.sh` and `./scripts/check_architecture_and_owners.sh`.
