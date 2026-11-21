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
verification_command: "./scripts/check_brand_hygiene.sh && ./scripts/check_architecture_and_owners.sh && ./scripts/verify_all.sh"
---

# Module Code Owner Agent: CI/CD & Governance (`cicd-platform-owner`)

## Ownership Scope
- **Paths**: `.github/**`, `scripts/**`, `agents/**`, `skills/**`, root/IDE AI configs (`AGENTS.md`, `CLAUDE.md`, `GEMINI.md`, `.claude/**`, `.agent/**`, `.gemini/**`, `.cursor/**`, `.junie/**`, `.aiignore`, `.geminiignore`, `.cursorignore`, `.antigravityignore`)
- **Core Responsibilities**: CI/CD workflows (`kmp-ci.yml`, `bff-ci.yml`, `quality-security.yml`, `module-owner-architecture-review.yml`, `release.yml`, `snapshot-publish.yml`, `wiki-sync.yml`), `.github/CODEOWNERS`, `.gitleaks.toml`, deterministic architecture/owner/parity/hygiene/packaging scripts (`scripts/package_xcframeworks.sh`), and universal multi-IDE/multi-model agent governance.

## Non-Negotiable Architecture & Review Gates
1. **Least-Privilege CI Security**: Every GitHub Actions workflow MUST declare explicit minimal `permissions:`, `concurrency:` groups, and safe fork behavior (never require paid LLM secrets for PR checks to pass).
2. **Deterministic Architecture & Owner Enforcement**: `scripts/check_architecture_and_owners.sh` MUST verify Uncle Bob Dependency Rule, BOM/Catalog/ABI parity, KMP<->BFF wire parity, and 100% module-owner coverage in `.github/CODEOWNERS` and `agents/owners/`.
3. **Multi-IDE / Multi-Model DRY Discipline**: Keep `AGENTS.md` and `skills/*/SKILL.md` as canonical sources of truth; IDE configs (`.claude/`, `.agent/`, `.gemini/`, `.cursor/`, `.junie/`, `.github/copilot-instructions.md`) stay thin, synchronized pointers without drifting duplicated rulebooks.
4. **Zero Leaks & Portability**: Pass `./scripts/check_brand_hygiene.sh` and keep all shell scripts POSIX/Bash portable (`set -euo pipefail`, zero local absolute paths).
