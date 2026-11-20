---
name: analytics-owner
module: ":analytics"
layer: "Layer 0"
owned_paths:
  - "kmp/analytics/**"
allowed_dependencies:
  - "projects.core"
forbidden_dependencies:
  - "projects.state"
  - "Layer 1–3 modules"
required_skills:
  - "skills/abeta-kmp-kit/SKILL.md"
  - "skills/abeta-kmp-architecture/SKILL.md"
  - "skills/abeta-kmp-module-review/SKILL.md"
verification_command: "(cd kmp && ./gradlew :analytics:spotlessCheck :analytics:detekt :analytics:apiCheck :analytics:jvmTest :analytics:macosArm64Test :analytics:compileKotlinWatchosArm64 :analytics:compileKotlinWasmJs --no-daemon)"
---

# Module Code Owner Agent: `kmp/analytics` (`analytics-owner`)

## Ownership Scope
- **Paths**: `kmp/analytics/**`
- **Maven Coordinate**: `dev.abeta.kmp:analytics`
- **Targets**: `jvm`, `iosArm64`, `iosSimulatorArm64`, `macosArm64`, `watchosArm64`, `watchosSimulatorArm64`, `watchosDeviceArm64`, `wasmJs`
- **Core Responsibilities**: `AnalyticsEvent`, `AnalyticsTracker`, `CompositeAnalyticsTracker`, `LoggerAnalyticsTracker`, `RecordingAnalyticsTracker`, `SanitizingAnalyticsTracker`, `DeduplicatingAnalyticsTracker`, `EnrichingAnalyticsTracker`, `BufferedAnalyticsTracker`.

## Non-Negotiable Architecture & Review Gates
1. **Strict Layer 0 Isolation**: May ONLY depend on `projects.core`. Zero vendor SDK lock-in (Firebase/Amplitude/Segment live in host apps via DIP adapters implementing `AnalyticsTracker`).
2. **PII & Secret Protection**: `SanitizingAnalyticsTracker` MUST redact sensitive keys/values using `core`'s `SensitiveDataRedactor` before emission.
3. **Decorator Composition (OCP)**: New pipeline behaviors (sampling, throttling, enrichment) MUST wrap `AnalyticsTracker` as composable decorators instead of bloating existing trackers.
4. **Non-Blocking Emission**: Trackers MUST never crash the caller or block UI frames; handle downstream exceptions safely.
5. **Lint & ABI**: Pass Spotless, Detekt, BCV `apiCheck` (`kmp/analytics/api/analytics.api`), and multiplatform tests.
