---
description: Add a new SDUI screen end-to-end across Go BFF, golden tests, KMP sample-app, Storybook LiveBffStory, and docs
---

# Workflow: Add SDUI Screen End-to-End (`/add-screen-e2e`)

1. Load `skills/abeta-kmp-bff/SKILL.md` and `skills/abeta-kmp-demo/SKILL.md`.
2. Add a focused builder file `bff/internal/infra/screen_<name>.go` and register it in `bff/internal/infra/screen_repo.go`.
3. Update `bff/internal/handler/openapi.go` if new routes/parameters are exposed, and add golden or repository test coverage under `bff/internal/infra/`.
4. Add the matching offline screen in `kmp/sample-app/src/main/kotlin/dev/abeta/kmp/sample/` (`Sample<Name>Screen.kt`), wire it in `SampleScreens.kt` and `SampleContainer.DEFAULT_SCREENS`, and register it in `kmp/storybook/src/commonMain/kotlin/dev/abeta/kmp/storybook/LiveBffStory.kt`.
5. Run `./scripts/verify_all.sh` and confirm both Go BFF and KMP `--headless` / `SampleWiringTest` pass.
