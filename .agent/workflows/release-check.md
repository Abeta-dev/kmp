---
description: Pre-release checklist for cutting a SemVer tag on abeta-dev/kmp
---

# Workflow: Release Readiness Check (`/release-check`)

1. Load `skills/abeta-kmp-cicd/SKILL.md` and `agents/owners/bom-catalog-owner.md`.
2. Verify all **11** publishable library versions (`0.7.0`) in `kmp/dependencyManagement/gradle/internal.versions.toml`, `kmp/gradle.properties`, and `CHANGELOG.md` (and confirm `vars.ENABLE_MAVEN_CENTRAL=true` when targeting Maven Central).
3. Regenerate/verify ABI baselines: `(cd kmp && ./gradlew apiCheck --no-daemon)`.
4. Verify external-resolution mode (`-Pabeta.kmpLocalBuild=false` where published) and run `./scripts/verify_all.sh`.
5. Run `(cd kmp && ./gradlew dokkaGenerateHtml --no-daemon)` and check `.github/workflows/release.yml` readiness before tagging `vX.Y.Z`.
