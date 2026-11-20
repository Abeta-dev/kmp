# Contributing to `abeta-dev/kmp`

Thank you for contributing to **Abeta KMP Kit**! We welcome bug reports, documentation improvements, new stateless atomic components, OCP action handlers, and BFF capabilities that respect our architectural boundaries.

---

## Code of Conduct

This project adheres to the [Code of Conduct](CODE_OF_CONDUCT.md). By participating, you are expected to uphold this code.

---

## Development Environment

- **JDK**: Java 21
- **Gradle**: 9.7.0 (via `kmp/gradlew`)
- **Go**: 1.26.0 (for `bff/`)
- **KMP Targets**: `jvm`, `iosArm64`, `iosSimulatorArm64`, `macosArm64`, `wasmJs` across all 10 publishable library modules and `storybook`, plus `watchosArm64`, `watchosSimulatorArm64`, `watchosDeviceArm64` on headless/contract modules (`core`, `state`, `analytics`, `sdui-contracts`, `action-engine`, `network`, `watch-companion`) and drop-in host templates in `kmp/platforms/` (`ANDROID_HOME` required for Android/Wear OS app modules)
- **OS**: macOS, Linux, or Windows (WSL2 recommended for shell scripts)

### Initial Setup

```bash
git clone https://github.com/abeta-dev/kmp.git
cd kmp
./scripts/verify_all.sh
```

---

## Architectural Rules for Pull Requests

Every PR must maintain these design invariants:

1. **Uncle Bob Dependency Rule**:
   - Do not introduce upward or sideways dependencies across layers (`Layer 0` -> `Layer 1` -> `Layer 2` -> `Layer 3`). See [docs/architecture.md](docs/architecture.md).
2. **Atomic / Action Separation**:
   - `design-tokens`, `sdui-contracts`, and `ui-atoms` must remain completely decoupled from `action-engine` and `network`.
   - New actions must be registered via `ActionRegistry` + `ActionPayloadDecoder` + `ActionHandler` (Open/Closed Principle). See [docs/atomic-and-action-separation.md](docs/atomic-and-action-separation.md).
3. **1:1 SDUI Wire Protocol Parity**:
   - Changes to `kmp/sdui-contracts` wire models must include matching Go types and golden JSON tests in `bff/pkg/sdui` and `bff/testdata/golden/{home_screen,form_screen,action_batch,containers_screen,store_home}.json`. See [docs/sdui-wire-protocol.md](docs/sdui-wire-protocol.md).
4. **Version Catalog, BOM & Binary Compatibility Hygiene**:
   - Add external dependencies to `kmp/dependencyManagement/gradle/libs.versions.toml` and internal coordinates to `kmp/dependencyManagement/gradle/internal.versions.toml`. Never hardcode versions in module build scripts.
   - When intentionally changing public KMP APIs, regenerate `.api` descriptors with `(cd kmp && ./gradlew apiDump)` and verify `(cd kmp && ./gradlew apiCheck)`.
5. **Brand & Path Hygiene**:
   - All public coordinates, packages, and docs use `abeta-dev` / `dev.abeta.kmp`.
   - Never commit machine-specific absolute paths.

---

## Pull Request Workflow

1. Create a feature branch from `main`:
   ```bash
   git checkout -b feat/my-feature
   ```
2. Keep commits focused and follow [Conventional Commits](https://www.conventionalcommits.org/) (`feat:`, `fix:`, `docs:`, `refactor:`, `test:`, `chore:`).
3. Format KMP code with Spotless and update ABI dumps if needed:
   ```bash
   (cd kmp && ./gradlew spotlessApply)
   (cd kmp && ./gradlew apiDump) # if public APIs changed
   ```
4. Format Go code with `gofmt`:
   ```bash
   (cd bff && gofmt -w .)
   ```
5. Optionally preview API docs with Dokka v2 (`(cd kmp && ./gradlew :core:dokkaGenerateHtml)` or `./gradlew dokkaGenerateHtml`) and run the full verification suite before opening a PR:
   ```bash
   ./scripts/verify_all.sh
   ```
6. Open a Pull Request against `main` describing:
   - What changed and why
   - Which architectural layers were touched
   - Verification commands executed and observed results

---

## Documentation & Wiki Updates

- Deep architectural guides live in `docs/`.
- GitHub Wiki source pages live in `wiki/` and are synced automatically to the repository wiki on pushes to `main`.
