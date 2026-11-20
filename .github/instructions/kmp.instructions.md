---
applyTo: "kmp/**/*.{kt,kts}"
---

# KMP Module Guidelines (`abeta-dev/kmp`)

- Load the module's Code Owner persona in `agents/owners/<module>-owner.md` before modifying files.
- Never hardcode external versions in module `build.gradle.kts`; declare versions in `kmp/dependencyManagement/gradle/libs.versions.toml` and `internal.versions.toml`.
- Keep `design-tokens`, `sdui-contracts`, and `ui-atoms` free of `core`, `action-engine`, `network`, and `sdui-layout`.
- Keep `watch-companion` free of Compose UI.
- In `core` and `network`, place Apple C/ObjC calls with 32/64-bit varying integer types (`NSInteger`, `NSUInteger`, `CFIndex`) in `apple64Main` + `watchosArm64Main` so HMPP metadata & Dokka compile cleanly.
- Honor Spotless (`ktlint`), Detekt (`MaxLineLength <= 120`, `ReturnCount <= 2`, `CyclomaticComplexMethod <= 14`, `LongMethod <= 60`, `TooManyFunctions <= 11`), and Binary Compatibility Validator (`./gradlew apiCheck` / `apiDump`).
