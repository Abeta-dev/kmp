# Open Package Hosting & Release Publishing Guide

`abeta-dev/kmp` distributes all **13 Gradle artifacts** (**11 KMP library modules** including Room 3 `database` + `bom` + `dependencies`), **2 Apple Release XCFrameworks** (`AbetaSduiKit` + `AbetaWatchKit` via SwiftPM), and the **Go 1.26.0 SDUI BFF module** through **100% free, open-to-download registries** — with **zero consumer authentication tokens required**.

---

## 1. Distribution Channels Matrix

| Channel | Cost | Consumer Auth Required? | Artifacts Hosted | Automated Workflow |
| :--- | :--- | :---: | :--- | :--- |
| **Maven Central** (`central.sonatype.com`) | **Free forever (OSS)** | **No** (`mavenCentral()`) | All **11 KMP modules** (`core`, `state`, `analytics`, `database`, `design-tokens`, `sdui-contracts`, `ui-atoms`, `action-engine`, `network`, `sdui-layout`, `watch-companion`) across `jvm`, `ios*`, `macosArm64`, `watchos*`, `wasmJs` + `bom` + `dependencies` (**13 Gradle modules**) | [release.yml](../.github/workflows/release.yml) (`v*` tags on `macos-latest`) |
| **Cloudsmith Open Source** (`cloudsmith.com/open-source`) | **Free OSS tier** | **No** (`dl.cloudsmith.io/public/...`) | JFrog-style universal Maven registry — stable mirrors & automated `main` `-SNAPSHOT` builds | [release.yml](../.github/workflows/release.yml) & [snapshot-publish.yml](../.github/workflows/snapshot-publish.yml) |
| **Apple SwiftPM (Direct Git URL + GitHub Releases)** | **Free** | **No** | `AbetaSduiKit-<tag>.xcframework.zip`, `AbetaWatchKit-<tag>.xcframework.zip`, `SHA256SUMS.txt`, verified `Package.swift` mirrored to lightweight `Abeta-dev/kmp-spm` repo | [release.yml](../.github/workflows/release.yml) + [package_xcframeworks.sh](../scripts/package_xcframeworks.sh) + [verify_spm_package.sh](../scripts/verify_spm_package.sh) |
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
            from("io.github.abeta-dev:dependencies:0.4.1")
        }
    }
}
```

```kotlin
// build.gradle.kts (commonMain)
// Note: Maven Central coordinates use verified `io.github.abeta-dev:*`; Kotlin imports remain `dev.abeta.kmp.*`.
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project.dependencies.platform("io.github.abeta-dev:bom:0.4.1"))
            implementation("io.github.abeta-dev:core")
            implementation("io.github.abeta-dev:database") // Optional Room 3 KMP local SQLite DB
            implementation("io.github.abeta-dev:design-tokens")
            implementation("io.github.abeta-dev:sdui-contracts")
            implementation("io.github.abeta-dev:ui-atoms")
            implementation("io.github.abeta-dev:action-engine")
            implementation("io.github.abeta-dev:network")
            implementation("io.github.abeta-dev:sdui-layout")
            // Optional headless wearable companion:
            // implementation("io.github.abeta-dev:watch-companion")
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
    implementation(project.dependencies.platform("io.github.abeta-dev:bom:0.4.1-SNAPSHOT"))
    implementation("io.github.abeta-dev:database")
    implementation("io.github.abeta-dev:sdui-layout")
}
```

### C. Pure Swift / Xcode — Swift Package Manager (Direct Git URL)

1. In Xcode, open **File -> Add Package Dependencies...**.
2. Paste `https://github.com/Abeta-dev/kmp-spm` into the search field.
3. Choose **Exact Version** `0.3.0` (or **Up to Next Minor** `0.3.0`).
4. Add **`AbetaSduiKit`** to your iOS target and/or **`AbetaWatchKit`** to your watchOS target.
5. Import directly in Swift:
   ```swift
   import AbetaSduiKit   // iOS / iPadOS Compose + SDUI host
   import AbetaWatchKit  // watchOS headless wearable companion
   ```

Or in a SwiftPM `Package.swift`:

```swift
dependencies: [
    .package(url: "https://github.com/Abeta-dev/kmp-spm", from: "0.4.1"),
]
```

### D. Go 1.26.0 SDUI BFF — `proxy.golang.org`

```bash
go get github.com/abeta-dev/kmp/bff@v0.6.0
```

---

## 3. Complete Maintainer Setup Playbook (Zero Ambiguity)

`release.yml` and `snapshot-publish.yml` automatically detect which channels are configured via GitHub Secrets/Variables and run on **`macos-latest`** so every Apple target (`ios*`, `macosArm64`, `watchos*`) publishes alongside JVM & WasmJS.

---

### Section A: Maven Central (Sonatype Central Portal) — Exact Step-by-Step Setup

Follow these 6 exact steps once per repository:

#### Step 1: Sign in to Sonatype Central Portal
1. Open [https://central.sonatype.com](https://central.sonatype.com).
2. Click **Sign In** and authenticate with **GitHub OAuth** (recommended so your GitHub org/account is linked automatically).

#### Step 2: Verify Your Maven Namespace (Verified Default: `io.github.abeta-dev`)

| Option | Namespace | Verification Method | Coordinate Impact |
| :--- | :--- | :--- | :--- |
| **Option 1 (Active Default in this repo)** | `io.github.abeta-dev` | In Central Portal -> **Namespaces -> Add Namespace**, enter `io.github.abeta-dev`. Central Portal gives you a verification key string. Create a **temporary empty public GitHub repo** under `Abeta-dev` named exactly after that verification key, click **Verify**, then delete the temporary repo once verified. | **Zero code changes** — matches `io.github.abeta-dev:*` already configured across `kmp/` (`GROUP=io.github.abeta-dev`, Kotlin packages remain `dev.abeta.kmp.*`). |
| **Option 2 (Optional Custom Domain)** | `dev.abeta` | Add the **DNS TXT record** shown by Central Portal to your domain `abeta.dev` (e.g., `@ TXT <verification-key>`), wait for DNS propagation, and click **Verify Namespace**. | Requires updating `GROUP` / `group` in `kmp/gradle.properties`, `kmp/build.gradle.kts`, `kmp/kmp-bom/build.gradle.kts`, `kmp/settings.gradle.kts`, and `kmp/dependencyManagement/gradle/internal.versions.toml` if migrating coordinates. |

#### Step 3: Generate a Central Portal User Token
1. In Central Portal, click your profile avatar in the top-right corner -> **View Account**.
2. Click **Generate User Token** -> **OK**.
3. Copy both the **Username** and **Password** immediately (Central Portal only displays them once).

#### Step 4: Generate & Publish Your GPG Signing Key
Maven Central rejects any artifact that is not PGP-signed with a publicly discoverable key. Run on your local machine:

```bash
# 1. Generate a 4096-bit RSA signing key
gpg --full-generate-key
# Select: (1) RSA and RSA -> 4096 bits -> 0 (does not expire) or 2y -> Name + Email -> Passphrase

# 2. List your secret keys and copy the 8-character or 16-character KEY_ID
gpg --list-secret-keys --keyid-format=long

# 3. Publish your PUBLIC key to both major keyservers (Central Portal checks these)
gpg --keyserver keys.openpgp.org --send-keys <KEY_ID>
gpg --keyserver keyserver.ubuntu.com --send-keys <KEY_ID>

# 4. Export your ASCII-armored PRIVATE key to paste into GitHub Secrets
gpg --export-secret-keys --armor <KEY_ID>
```

> Copy the entire output from `-----BEGIN PGP PRIVATE KEY BLOCK-----` through `-----END PGP PRIVATE KEY BLOCK-----` (including blank lines).

#### Step 5: Add the Exact 5 GitHub Secrets
Open your GitHub repository -> **Settings -> Secrets and variables -> Actions -> Secrets -> New repository secret** and add all 5 secrets:

| Secret Name | Required? | Exact Value |
| :--- | :---: | :--- |
| `MAVEN_CENTRAL_USERNAME` | **Yes** | Central Portal User Token username from Step 3 |
| `MAVEN_CENTRAL_PASSWORD` | **Yes** | Central Portal User Token password from Step 3 |
| `GPG_SIGNING_KEY` | **Yes** | Full ASCII-armored private key from `gpg --export-secret-keys --armor <KEY_ID>` |
| `GPG_SIGNING_KEY_ID` | Recommended | Last 8 characters of your GPG key ID (e.g., `1A2B3C4D`) |
| `GPG_SIGNING_PASSWORD` | If set | Passphrase used in Step 4 (leave empty / omit if unprotected) |

#### Step 6: Push Tag `v0.3.0` to Auto-Build, Sign & Release
```bash
git tag -a v0.3.0 -m "Release v0.3.0"
git push origin v0.3.0
```
[release.yml](../.github/workflows/release.yml) automatically runs on **`macos-latest`**, signs all **13 Gradle modules** (**11 KMP libraries** including `:database` + `:kmp-bom` + `:dependencyManagement`), uploads the deployment bundle to Central Portal via `publishAndReleaseToMavenCentral`, and automatically releases it to `mavenCentral()`!

---

### Section B: Cloudsmith Open Source (Free JFrog Alternative — Zero Consumer Auth)

Cloudsmith gives open-source projects a free, fast, JFrog-style universal Maven repository with **anonymous public downloads** and instant `-SNAPSHOT` hosting (Maven Central snapshots are separate and slower to propagate).

1. Apply for a free OSS organization at [https://cloudsmith.com/open-source](https://cloudsmith.com/open-source).
2. Create a **Public** repository named **`kmp`** inside your organization **`abeta-dev`** (full slug: `abeta-dev/kmp`).
3. Create an API key / service token with write access to `abeta-dev/kmp`.
4. In GitHub -> **Settings -> Secrets and variables -> Actions**, add:

| Kind | Name | Required? | Default / Value |
| :--- | :--- | :---: | :--- |
| **Secret** | `CLOUDSMITH_API_KEY` | **Yes** | Cloudsmith API key or write service token |
| **Variable** | `CLOUDSMITH_ORG` | Optional | Defaults to `abeta-dev` |
| **Variable** | `CLOUDSMITH_REPO` | Optional | Defaults to `kmp` |
| **Variable** | `CLOUDSMITH_USER` | Optional | Defaults to `token` (or your Cloudsmith username) |

Once `CLOUDSMITH_API_KEY` is saved:
- **Zero-auth public Maven URL** is live immediately at `https://dl.cloudsmith.io/public/abeta-dev/kmp/maven/`.
- Every push to `main` touching `kmp/**` triggers [snapshot-publish.yml](../.github/workflows/snapshot-publish.yml) on `macos-latest` to publish fresh multiplatform `-SNAPSHOT` artifacts.
- Every `v*` tag automatically mirrors stable releases to Cloudsmith alongside Maven Central.

---

### Section C: Apple Swift Package Manager (SwiftPM) — Direct Git URL & Xcode Setup

#### Why Both Maven Central and SwiftPM Exist
- **KMP Gradle apps** (even when targeting iOS, iPadOS, macOS, and watchOS) **only need Maven Central** (`mavenCentral()`). Gradle downloads every Apple `.klib` (`iosArm64`, `iosSimulatorArm64`, `macosArm64`, `watchosArm64`, `watchosSimulatorArm64`, `watchosDeviceArm64`) directly from Maven Central and links them into your own shared KMP framework.
- **Pure Swift / Xcode teams** (apps written in SwiftUI/UIKit without Gradle) consume prebuilt binary XCFrameworks (`AbetaSduiKit.xcframework` & `AbetaWatchKit.xcframework`) directly inside Xcode via **Swift Package Manager (SwiftPM)**.

#### Path 1 (Recommended): Direct Git URL via Lightweight Mirror Repo `Abeta-dev/kmp-spm`

Why a separate mirror repo? SwiftPM clones the Git repository whose URL you paste into Xcode. Mirroring only the generated `Package.swift` into a lightweight repo (`Abeta-dev/kmp-spm`) means Xcode downloads a ~1 KB manifest and pulls the release `.xcframework.zip` binaries directly from GitHub Releases — instead of cloning the full KMP + Go monorepo!

1. **Create the lightweight public mirror repository**:
   - Create an empty **public** GitHub repository named **`Abeta-dev/kmp-spm`** (initialize with a README or leave empty).
2. **Create a scoped Fine-Grained Personal Access Token (PAT)**:
   - Open GitHub -> **Settings -> Developer settings -> Personal access tokens -> Fine-grained tokens -> Generate new token**.
   - Under **Repository access**, select **Only select repositories** -> choose **`Abeta-dev/kmp-spm`** ONLY.
   - Under **Repository permissions**, grant **`Contents: Read and write`** (no other permissions required).
   - Generate and copy the token.
3. **Add the GitHub Secret (and optional Variable) on `Abeta-dev/kmp`**:
   - Go to `Abeta-dev/kmp` -> **Settings -> Secrets and variables -> Actions**:

| Kind | Name | Required? | Default / Value |
| :--- | :--- | :---: | :--- |
| **Secret** | `SPM_REPO_TOKEN` | **Yes** | Fine-grained PAT scoped strictly to `Abeta-dev/kmp-spm` (`Contents: Read & Write`) |
| **Variable** | `SPM_REPO` | Optional | Defaults to `Abeta-dev/kmp-spm` |

4. **Push a release tag (`v0.3.0`) on `Abeta-dev/kmp`**:
   When you push `v0.3.0`, [release.yml](../.github/workflows/release.yml) automatically:
   1. Builds Release **`AbetaSduiKit.xcframework`** (`iosArm64` + `iosSimulatorArm64`) and **`AbetaWatchKit.xcframework`** (`watchosArm64` + `watchosSimulatorArm64` + `watchosDeviceArm64`) on **`macos-latest`**.
   2. Zips both XCFrameworks, computes SwiftPM SHA-256 checksums (`SHA256SUMS.txt`), and renders `Package.swift` from [`kmp/platforms/apple/Package.template.swift`](../kmp/platforms/apple/Package.template.swift) via [`scripts/package_xcframeworks.sh`](../scripts/package_xcframeworks.sh).
   3. Verifies `Package.swift` syntax, checksums, and manifest integrity using [`scripts/verify_spm_package.sh`](../scripts/verify_spm_package.sh).
   4. Uploads both `.xcframework.zip` archives, `SHA256SUMS.txt`, and `Package.swift` to the **`Abeta-dev/kmp`** GitHub Release.
   5. Commits the verified `Package.swift` and pushes SemVer tag **`0.3.0`** (without leading `v`, matching SwiftPM conventions) to **`Abeta-dev/kmp-spm`**!
5. **Consume in Xcode**:
   - Open **File -> Add Package Dependencies...**
   - Paste **`https://github.com/Abeta-dev/kmp-spm`**
   - Choose **`0.3.0`**
   - Add **`AbetaSduiKit`** (iOS) or **`AbetaWatchKit`** (watchOS) to your app target
   - Use `import AbetaSduiKit` or `import AbetaWatchKit`!

#### Path 2 (Zero Extra Repository — Manual Release Download)

If you prefer not to create `Abeta-dev/kmp-spm`, every `v*` GitHub Release on `Abeta-dev/kmp` still attaches:
- `AbetaSduiKit-<tag>.xcframework.zip`
- `AbetaWatchKit-<tag>.xcframework.zip`
- `SHA256SUMS.txt`
- A ready-to-use `Package.swift` with exact release URLs and SwiftPM checksums

Pure Swift teams can download `Package.swift` from the release assets (or copy its `.binaryTarget(name:url:checksum:)` entries directly into their own `Package.swift` / local Swift package).

---

### Section D: Go Module Proxy (`proxy.golang.org`)

No secrets, tokens, or account registration are needed for Go consumers:

1. Tag the Go BFF module under its subdirectory tag prefix `bff/`:
   ```bash
   git tag -a bff/v0.6.0 -m "Release Go SDUI BFF v0.5.0"
   git push origin bff/v0.6.0
   ```
2. [release.yml](../.github/workflows/release.yml) verifies `gofmt`, `go vet ./...`, and `go test -race -cover ./...`, creates the GitHub Release, and automatically warms `https://proxy.golang.org/github.com/abeta-dev/kmp/bff/@v/v0.5.0.info`.
3. Any Go developer worldwide can immediately install it with:
   ```bash
   go get github.com/abeta-dev/kmp/bff@v0.6.0
   ```

---

### Section E (Optional): Custom Public Maven2 Mirror (GitLab / Reposilite)

If your organization hosts a public GitLab Package Registry or Reposilite mirror:

| Kind | Name | Example |
| :--- | :--- | :--- |
| **Variable** | `CUSTOM_MAVEN_URL` | `https://gitlab.com/api/v4/projects/<PROJECT_ID>/packages/maven` |
| **Variable** | `CUSTOM_MAVEN_USER` | `Private-Token` or registry user |
| **Secret** | `CUSTOM_MAVEN_PASSWORD` | Deploy token / password |

---

## 4. Local Publishing & Packaging Verification (Zero Secrets)

Verify all **13 Maven artifacts** (POMs, `.module` Gradle Module Metadata, `.jar`, `.klib`, sources & javadoc jars) and Apple SwiftPM packaging locally before tagging:

```bash
# 1. Verify all 13 stable artifacts (io.github.abeta-dev:*:0.3.0, incl. :database) in ~/.m2/repository
(cd kmp && ./gradlew publishToMavenLocal -Pabeta.kmpLocalBuild=false --no-daemon)

# 2. Verify snapshot version override (0.3.0-SNAPSHOT, including BOM constraints)
(cd kmp && ./gradlew publishToMavenLocal \
  -Pabeta.kmpLocalBuild=false \
  -Pabeta.publishVersion=0.3.0-SNAPSHOT \
  --no-daemon)

# 3. Assemble & package Apple Release XCFrameworks + verify SwiftPM Package.swift (macOS)
(cd kmp && ./gradlew \
  :watch-companion:assembleAbetaWatchKitReleaseXCFramework \
  :sdui-layout:assembleAbetaSduiKitReleaseXCFramework \
  --max-workers=1 --no-daemon \
  -Dorg.gradle.jvmargs="-Xmx6g -Dfile.encoding=UTF-8" \
  -Dkotlin.daemon.jvm.options="-Xmx6g")
./scripts/package_xcframeworks.sh --tag v0.3.0 --out-dir dist/xcframeworks
./scripts/verify_spm_package.sh dist/xcframeworks/Package.swift --expected-tag v0.3.0
```

---

## 5. Cutting a Production Release

```bash
# 1. Cut a full KMP (13 Gradle modules) + Apple XCFramework + SwiftPM mirror release
git tag -a v0.3.0 -m "Release v0.3.0"
git push origin v0.3.0

# 2. Cut a Go BFF module release (GitHub Release + automatic proxy.golang.org indexing)
git tag -a bff/v0.6.0 -m "Release Go SDUI BFF v0.5.0"
git push origin bff/v0.6.0
```

You can also trigger `Release` or `Snapshot Publish (Cloudsmith Open Source)` manually via **GitHub Actions -> Run workflow** (`workflow_dispatch`) to target a specific registry (`maven-central`, `cloudsmith`, `github-packages`, `xcframeworks-only`) or override the version.
