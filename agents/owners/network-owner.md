---
name: network-owner
module: ":network"
layer: "Layer 2"
owned_paths:
  - "kmp/network/**"
allowed_dependencies:
  - "projects.core"
forbidden_dependencies:
  - "projects.state"
  - "projects.analytics"
  - "projects.designTokens"
  - "projects.sduiContracts"
  - "projects.uiAtoms"
  - "projects.actionEngine"
  - "Layer 3 modules"
required_skills:
  - "skills/abeta-kmp-kit/SKILL.md"
  - "skills/abeta-kmp-platforms/SKILL.md"
  - "skills/abeta-kmp-module-review/SKILL.md"
verification_command: "(cd kmp && ./gradlew :network:spotlessCheck :network:detekt :network:apiCheck :network:jvmTest :network:macosArm64Test :network:compileAppleMainKotlinMetadata :network:compileKotlinWatchosArm64 :network:compileKotlinWasmJs --no-daemon)"
---

# Module Code Owner Agent: `kmp/network` (`network-owner`)

## Ownership Scope
- **Paths**: `kmp/network/**`
- **Maven Coordinate**: `dev.abeta.kmp:network`
- **Targets**: `jvm`, `iosArm64`, `iosSimulatorArm64`, `macosArm64`, `watchosArm64`, `watchosSimulatorArm64`, `watchosDeviceArm64`, `wasmJs`
- **Core Responsibilities**: `HttpTransport`, `KtorHttpTransport`, `HttpClientFactory`, `NetworkConfig`, `NetworkPorts` (`AuthTokenProvider`, `RetryPolicy`), HTTP/2 ALPN, Brotli (`br`) decompression, bearer token refresh, and shared `CertificatePinMatcher` SPKI/cert pinning across JVM/OkHttp and Apple Darwin `NSURLSession`.

## Non-Negotiable Architecture & Review Gates
1. **Strict Layer 2 Boundary**: May ONLY depend on `projects.core`. NEVER import `sdui-contracts`, `action-engine`, `ui-atoms`, or `sdui-layout`.
2. **Transport Security & Pinning**: Any change to TLS pinning MUST preserve parity between JVM/OkHttp (`PlatformHttpEngine.jvm.kt`) and Apple Darwin (`PlatformHttpEngine.apple.kt` + `apple64Main`/`watchosArm64Main` `ApplePinnedTrustBridge`) and keep `CertificatePinMatcherTest` green.
3. **Apple HMPP 32/64-bit Safety**: Keep `NSURLSessionAuthChallenge*` and `SecTrust*` `NSInteger`/`CFIndex` bindings in `apple64Main` & `watchosArm64Main` so `:network:compileAppleMainKotlinMetadata`, `:network:compileApple64MainKotlinMetadata`, and `dokkaGenerateHtml` pass cleanly.
4. **Resilience & DIP**: Honor `RetryPolicy` (`BackoffPolicy`), typed `AppError` mapping (`TimeoutError`, `RateLimitError`, `NetworkError`), and injectable `HttpClientEngine` for deterministic `MockEngine` tests.
5. **Lint & ABI**: Pass Spotless, Detekt, BCV `apiCheck` (`kmp/network/api/network.api`), `OkHttpBrotliJvmTest`, and `HttpTransportTest`.
