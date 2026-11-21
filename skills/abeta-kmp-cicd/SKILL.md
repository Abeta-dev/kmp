---
name: abeta-kmp-cicd
description: Complete CI/CD, GitHub Actions workflows, automated Module Code Owner & Architecture PR review, Binary Compatibility Validator (ABI), brand/secret hygiene, and SemVer release guide for abeta-dev/kmp. Use when authoring workflows, debugging CI checks, configuring AI PR reviewers, or cutting a release.
---

# Abeta KMP Kit — CI/CD & Quality Gates Skill (`abeta-kmp-cicd`)

Use this skill whenever configuring, extending, debugging, or executing CI/CD pipelines, automated code-owner reviews, ABI checks, or open-registry releases (Maven Central, Cloudsmith Open Source, SwiftPM XCFrameworks, Go `proxy.golang.org`) in `abeta-dev/kmp`.

## 1. GitHub Actions Workflows Map

| Workflow | Trigger | Purpose |
| :--- | :--- | :--- |
| [kmp-ci.yml](../../.github/workflows/kmp-ci.yml) | PR / push (`kmp/**`, `scripts/**`) | Java 21 + Gradle 9.7.0 `./gradlew check --no-daemon --stacktrace` (Spotless, Detekt, BCV `apiCheck`, JVM tests, WasmJS compile) |
| [bff-ci.yml](../../.github/workflows/bff-ci.yml) | PR / push (`bff/**`, `scripts/**`) | Go 1.26.0 `gofmt`, `go vet ./...`, `go test -race -coverprofile=coverage.out ./...` |
| [quality-security.yml](../../.github/workflows/quality-security.yml) | PR / push | Brand & machine-path hygiene (`check_brand_hygiene.sh`), Architecture & Code Owner gate (`check_architecture_and_owners.sh`), SHA-256-verified open-source `gitleaks` CLI secret scan (`.gitleaks.toml`), Spotless & Detekt |
| [module-owner-architecture-review.yml](../../.github/workflows/module-owner-architecture-review.yml) | PR / manual dispatch | Deterministic Uncle Bob Dependency Rule, BOM/Catalog/ABI parity, 1:1 KMP<->BFF wire parity, per-module Code Owner PR review comment, Spotless/Detekt/`apiCheck`/Go `arch_test`, and optional multi-model AI diff review (Claude / Gemini / OpenAI) |
| [release.yml](../../.github/workflows/release.yml) | Tags `v*`, `bff/v*` or `workflow_dispatch` | Full KMP + Go verification -> `macos-latest` publish all **13 Gradle modules** (**11 KMP libs** incl. `:database` + `:kmp-bom` + `:dependencyManagement`) to **Maven Central (Sonatype Central Portal)**, **Cloudsmith Open Source**, optional custom Maven mirror, plus Release Apple XCFrameworks (`AbetaSduiKit`, `AbetaWatchKit`) + verified SwiftPM `Package.swift` (`verify_spm_package.sh`) + `SHA256SUMS.txt` -> GitHub Release, SwiftPM mirror (`Abeta-dev/kmp-spm` via `SPM_REPO_TOKEN`), & `proxy.golang.org` warmup |
| [snapshot-publish.yml](../../.github/workflows/snapshot-publish.yml) | Push (`main` `kmp/**`) or `workflow_dispatch` | Publishes multiplatform `*-SNAPSHOT` builds to **Cloudsmith Open Source** on `macos-latest` automatically whenever `CLOUDSMITH_API_KEY` is configured |
| [wiki-sync.yml](../../.github/workflows/wiki-sync.yml) | Push (`wiki/**`) | Mirrors `wiki/*.md` to the GitHub repository wiki |

## 2. Automated Module Code Owner & Architecture Gate

Every module in `abeta-dev/kmp` has a dedicated Code Owner persona in `agents/owners/<module>-owner.md` and a matching entry in `.github/CODEOWNERS`.

```bash
# Run deterministic architecture, BOM, ABI, wire-parity & owner checks
./scripts/check_architecture_and_owners.sh

# Generate a PR-scoped module-owner assignment & checklist report
./scripts/check_architecture_and_owners.sh --pr-diff origin/main --report-out module-owner-report.md
```

What `check_architecture_and_owners.sh` enforces automatically:
1. **Uncle Bob Dependency Rule**: validates `projects.*` dependencies in every `kmp/*/build.gradle.kts`, Layer 0 `database` isolation (depends only on `core`, zero Compose/Ktor), Layer 1 import isolation (`design-tokens`, `sdui-contracts`, `ui-atoms`), headless `watch-companion` (zero Compose UI), and pure Go `bff/internal/domain`.
2. **Gradle BOM, Catalog & ABI**: zero hardcoded dependency versions in module `build.gradle.kts`, all **11 publishable modules** (including `database`) registered in `internal.versions.toml`, `kmp-bom`, `build.gradle.kts`, `settings.gradle.kts`, and `kmp/<mod>/api/<mod>.api`.
3. **1:1 KMP <-> Go BFF Wire Parity**: asserts exact set equality across all **18 `ActionTypes`** and **14 `SnippetTypes`**.
4. **Module Owner Coverage**: confirms all **17 owner agents** (including `database-owner.md`) exist in `agents/owners/` with valid metadata and matching `.github/CODEOWNERS` paths.
5. **SOLID / KISS File-Size Guardrail**: flags production `.kt`/`.go` files exceeding `750 LOC`.

## 3. Enabling Optional Multi-Model AI PR Review in CI

`module-owner-architecture-review.yml` passes deterministically on forks and public PRs **without** requiring any LLM API key. Maintainers can optionally enable advisory multi-model diff reviews by setting:
- Repository Variable `AI_REVIEW_PROVIDER`: `anthropic` | `gemini` | `openai`
- Matching Repository Secret: `ANTHROPIC_API_KEY` (optional `ANTHROPIC_MODEL`), `GEMINI_API_KEY` (optional `GEMINI_MODEL`), or `OPENAI_API_KEY` (optional `OPENAI_MODEL`)

## 4. Local Pre-Push & Release Runbook

```bash
# 1. Full 5-stage monorepo verification gate
./scripts/verify_all.sh

# 2. Intentional public API changes -> update BCV descriptors across all 11 modules
(cd kmp && ./gradlew apiDump --no-daemon)

# 3. Full Dokka v2 HTML docs (also verifies HMPP appleMain + apple64Main metadata)
(cd kmp && ./gradlew dokkaGenerateHtml --no-daemon)

# 4. macOS Apple XCFrameworks + WasmJS production webpack (sequential to avoid LTO OOM)
(cd kmp && ./gradlew \
  :watch-companion:assembleAbetaWatchKitXCFramework \
  :sdui-layout:assembleAbetaSduiKitXCFramework \
  :storybook:assembleAbetaStorybookKitXCFramework \
  :storybook:wasmJsBrowserProductionWebpack \
  --max-workers=1 --no-daemon \
  -Dorg.gradle.jvmargs="-Xmx6g -Dfile.encoding=UTF-8" \
  -Dkotlin.daemon.jvm.options="-Xmx6g")

# 5. Package Apple XCFrameworks + verify SwiftPM Package.swift locally
./scripts/package_xcframeworks.sh --tag v0.1.0 --out-dir dist/xcframeworks
./scripts/verify_spm_package.sh dist/xcframeworks/Package.swift --expected-tag v0.1.0

# 6. Verify all 13 Maven artifacts locally (zero credentials required)
(cd kmp && ./gradlew publishToMavenLocal -Pabeta.kmpLocalBuild=false --no-daemon)

# 7. Cut SemVer releases (triggers .github/workflows/release.yml — see docs/publishing-and-hosting.md)
git tag -a v0.1.0 -m "Release v0.1.0"
git push origin v0.1.0
# For Go BFF module proxy (proxy.golang.org):
git tag -a bff/v0.1.0 -m "Release Go BFF v0.1.0"
git push origin bff/v0.1.0
```

## 5. CI Troubleshooting Notes
- **macOS without Xcode iOS/watchOS simulator runtimes**: `kmp/build.gradle.kts` detects installed runtimes via `xcrun simctl list runtimes available` and falls back to `linkDebugTestIosSimulatorArm64` / `linkDebugTestWatchosSimulatorArm64` so `./gradlew check` never fails on missing simulator images.
- **Kotlin/Native LTO OOM**: Always pass `--max-workers=1` and `-Xmx6g` when assembling multiple Compose Release XCFrameworks in one invocation.
- **Brotli / Android SDK**: `kmp/platforms/{android,wearos}` templates live outside `settings.gradle.kts` so Linux/macOS CI without `ANDROID_HOME` stays 100% green.
