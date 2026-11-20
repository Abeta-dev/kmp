---
description: Add a new SDUI screen, snippet, or action with 1:1 Go BFF and KMP wire parity, golden tests, and demo coverage
---

1. Load `skills/abeta-kmp-kit/SKILL.md`, `skills/abeta-kmp-atomic/SKILL.md`, `skills/abeta-kmp-actions/SKILL.md`, `skills/abeta-kmp-bff/SKILL.md`, and `skills/abeta-kmp-demo/SKILL.md`.
2. Update pure wire contracts in BOTH `kmp/sdui-contracts` (or `kmp/action-engine`) AND `bff/pkg/sdui/*` with matching `snake_case` JSON tags.
3. Wire OCP handlers/renderers via `ActionDecoderRegistry` + `ActionRegistry` or `SnippetRegistry`.
4. Add/update the screen in `bff/internal/infra/screen_<name>.go`, `kmp/sample-app`, `kmp/storybook`, and `bff/testdata/golden/`.
5. Regenerate `.api` dumps if public KMP APIs changed (`(cd kmp && ./gradlew apiDump)`) and run `./scripts/verify_all.sh`.
