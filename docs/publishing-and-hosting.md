# Open Package Hosting & Release Publishing Guide

`abeta-dev/kmp` distributes all **12 Gradle artifacts** (10 KMP library modules + `bom` + `dependencies`), **2 Apple Release XCFrameworks**, and the **Go 1.26.0 SDUI BFF module** through **100% free, open-to-download registries** — with **zero consumer authentication tokens required**.

---

## 1. Distribution Channels Matrix

| Channel | Cost | Consumer Auth Required? | Artifacts Hosted | Automated Workflow |
| :--- | :--- | :---: | :--- | :--- |
| **Maven Central** (`central.sonatype.com`) | **Free forever (OSS)** | **No** (`mavenCentral()`) | All 10 KMP modules (`jvm`, `ios*`, `macosArm64`, `watchos*`, `wasmJs`) + `bom` + `dependencies` | [release.yml](../.github/workflows/release.yml) (`v*` tags on `macos-latest`) |
| **Cloudsmith Open Source** (`cloudsmith.com/open-source`) | **Free OSS tier** | **No** (`dl.cloudsmith.io/public/...`) | JFrog-style universal Maven registry — stable mirrors & automated `-SNAPSHOT` builds | [release.yml](../.github/workflows/release.yml) & [snapshot-publish.yml](../.github/workflows/snapshot-publish.yml) |
| **GitHub Releases + SwiftPM** | **Free** | **No** | `AbetaSduiKit-<tag>.xcframework.zip`, `AbetaWatchKit-<tag>.xcframework.zip`, `SHA256SUMS.txt`, generated `Package.swift` | [release.yml](../.github/workflows/release.yml) + [package_xcframeworks.sh](../scripts/package_xcframeworks.sh) |
| **Go Module Proxy** (`proxy.golang.org`) | **Free (automatic)** | **No** | `github.com/abeta-dev/kmp/bff` | [release.yml](../.github/workflows/release.yml) (`bff/v*` tags) |
| **Custom Maven2 Mirror** (GitLab Package Registry / Reposilite) | **Free** | **No** (for public repos) | Optional mirror via `CUSTOM_MAVEN_URL` | [release.yml](../.github/workflows/release.yml) |

> **Why GitHub Packages is opt-in only & why JitPack is not used:**
> - **GitHub Packages (Maven)** forces every downstream developer to supply a GitHub Personal Access Token even for public repositories. It remains available as an opt-in mirror (`ENABLE_GITHUB_PACKAGES_MIRROR=true`), never as the primary channel.
> - **JitPack** builds only on Linux and cannot compile Apple iOS, macOS, or watchOS `.klib` artifacts.

---

## 2. Consumer Setup (Zero Credentials Required)

### A. Gradle KMP — Stable Releases via Maven Central (Recommended)

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
    versionCatalogs {
        create("abetaKmp") {
            from("dev.abeta.kmp:dependencies:0.1.0")
        }
    }
}
```

```kotlin
// build.gradle.kts (commonMain)
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project.dependencies.platform("dev.abeta.kmp:bom:0.1.0"))
            implementation("dev.abeta.kmp:design-tokens")
            implementation("dev.abeta.kmp:sdui-contracts")
            implementation("dev.abeta.kmp:ui-atoms")
            implementation("dev.abeta.kmp:action-engine")
            implementation("dev.abeta.kmp:network")
            implementation("dev.abeta.kmp:sdui-layout")
            // Optional headless wearable companion:
            // implementation("dev.abeta.kmp:watch-companion")
        }
    }
}
```

### B. Gradle KMP — Nightly `-SNAPSHOT` or Mirror via Cloudsmith Open Source (Anonymous)

No username or token is needed to download public artifacts from Cloudsmith:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
        maven("https://dl.cloudsmith.io/public/abeta-dev/kmp/maven/")
    }
}
```

```kotlin
// build.gradle.kts (commonMain)
commonMain.dependencies {
    implementation(project.dependencies.platform("dev.abeta.kmp:bom:0.1.0-SNAPSHOT"))
    implementation("dev.abeta.kmp:sdui-layout")
}
```

### C. Apple iOS & watchOS — Swift Package Manager (GitHub Releases)

Every `v*` GitHub Release attaches:
- `AbetaSduiKit-<tag>.xcframework.zip` (`iosArm64` + `iosSimulatorArm64`)
- `AbetaWatchKit-<tag>.xcframework.zip` (`watchosArm64` + `watchosSimulatorArm64` + `watchosDeviceArm64`)
- `SHA256SUMS.txt`
- A ready-to-use `Package.swift` pre-filled with the release URLs and exact SwiftPM SHA-256 checksums.

### D. Go 1.26.0 SDUI BFF — `proxy.golang.org`

```bash
go get github.com/abeta-dev/kmp/bff@v0.1.0
```

---

## 3. One-Time Maintainer Setup (GitHub Secrets & Variables)

`release.yml` and `snapshot-publish.yml` automatically detect which open channels are configured and run on **`macos-latest`** so every Apple target (`ios*`, `macosArm64`, `watchos*`) publishes alongside JVM & WasmJS.

### Channel 1: Maven Central (Sonatype Central Portal)

1. Sign in at [central.sonatype.com](https://central.sonatype.com) and verify your namespace (`dev.abeta` via a DNS TXT record on `abeta.dev`, or register `io.github.abeta-dev` with instant GitHub verification).
2. In Central Portal -> **Account -> Generate User Token**, create a publishing token (`username` + `password`).
3. Create a GPG signing key and publish the public key:
   ```bash
   gpg --full-generate-key # RSA 4096, no expiry or long expiry
   gpg --list-secret-keys --keyid-format=short
   gpg --keyserver keyserver.ubuntu.com --send-keys <KEY_ID>
   gpg --keyserver keys.openpgp.org --send-keys <KEY_ID>
   gpg --export-secret-keys --armor <KEY_ID> # copy full ASCII-armored block
   ```
4. Add these **Repository Secrets** in GitHub -> **Settings -> Secrets and variables -> Actions -> Secrets**:

| Secret Name | Value |
| :--- | :--- |
| `MAVEN_CENTRAL_USERNAME` | Central Portal user token username |
| `MAVEN_CENTRAL_PASSWORD` | Central Portal user token password |
| `GPG_SIGNING_KEY` | Full ASCII-armored private key (`-----BEGIN PGP PRIVATE KEY BLOCK-----...`) |
| `GPG_SIGNING_KEY_ID` | Short/long GPG key ID (optional if default key in block) |
| `GPG_SIGNING_PASSWORD` | GPG key passphrase (empty if unprotected) |

### Channel 2: Cloudsmith Open Source (Free JFrog Alternative)

1. Create a free Open Source workspace at [cloudsmith.com/open-source](https://cloudsmith.com/open-source) and create a **public** repository (e.g., workspace `abeta-dev`, repository `kmp`).
2. Add the **Repository Secret** and optional **Repository Variables**:

| Kind | Name | Default / Value |
| :--- | :--- | :--- |
| **Secret** | `CLOUDSMITH_API_KEY` | Cloudsmith API key or Entitlement/Service write token |
| **Variable** *(optional)* | `CLOUDSMITH_ORG` | `abeta-dev` |
| **Variable** *(optional)* | `CLOUDSMITH_REPO` | `kmp` |
| **Variable** *(optional)* | `CLOUDSMITH_USER` | `token` (or your Cloudsmith username) |

Once `CLOUDSMITH_API_KEY` is added:
- Every merge to `main` touching `kmp/**` automatically publishes fresh `*-SNAPSHOT` multiplatform artifacts via [snapshot-publish.yml](../.github/workflows/snapshot-publish.yml).
- Every `v*` release tag automatically mirrors stable artifacts to Cloudsmith alongside Maven Central.

### Channel 3 (Optional): Custom Public Maven2 Mirror (e.g., GitLab Package Registry)

| Kind | Name | Example |
| :--- | :--- | :--- |
| **Variable** | `CUSTOM_MAVEN_URL` | `https://gitlab.com/api/v4/projects/<PROJECT_ID>/packages/maven` |
| **Variable** | `CUSTOM_MAVEN_USER` | `Private-Token` or registry user |
| **Secret** | `CUSTOM_MAVEN_PASSWORD` | Deploy token / password |

---

## 4. Local Publishing & Packaging Verification

Verify all 12 Maven artifacts (POMs, `.module` Gradle Module Metadata, `.jar`, `.klib`, sources & javadoc jars) locally with zero secrets:

```bash
# 1. Verify stable coordinates (dev.abeta.kmp:*:0.1.0) in ~/.m2/repository
(cd kmp && ./gradlew publishToMavenLocal -Pabeta.kmpLocalBuild=false --no-daemon)

# 2. Verify snapshot version override (0.1.0-SNAPSHOT, including BOM constraints)
(cd kmp && ./gradlew publishToMavenLocal \
  -Pabeta.kmpLocalBuild=false \
  -Pabeta.publishVersion=0.1.0-SNAPSHOT \
  --no-daemon)

# 3. Assemble & package Apple Release XCFrameworks + SwiftPM Package.swift (macOS)
(cd kmp && ./gradlew \
  :watch-companion:assembleAbetaWatchKitReleaseXCFramework \
  :sdui-layout:assembleAbetaSduiKitReleaseXCFramework \
  --max-workers=1 --no-daemon \
  -Dorg.gradle.jvmargs="-Xmx6g -Dfile.encoding=UTF-8" \
  -Dkotlin.daemon.jvm.options="-Xmx6g")
./scripts/package_xcframeworks.sh --tag v0.1.0 --out-dir dist/xcframeworks
```

---

## 5. Cutting a Production Release

```bash
# 1. Cut a full KMP + Apple XCFramework release (Maven Central + Cloudsmith + GitHub Release)
git tag -a v0.1.0 -m "Release v0.1.0"
git push origin v0.1.0

# 2. Cut a Go BFF module release (GitHub Release + automatic proxy.golang.org indexing)
git tag -a bff/v0.1.0 -m "Release Go SDUI BFF v0.1.0"
git push origin bff/v0.1.0
```

You can also trigger `Release` or `Snapshot Publish (Cloudsmith Open Source)` manually via **GitHub Actions -> Run workflow** (`workflow_dispatch`) to target a specific registry (`maven-central`, `cloudsmith`, `github-packages`, `xcframeworks-only`) or override the version.
