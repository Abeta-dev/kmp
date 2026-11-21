// swift-tools-version: 5.9
// Reference SwiftPM manifest template for Abeta KMP Kit Apple Release XCFrameworks.
//
// Each `v*` release generates a concrete `Package.swift` with real download URLs and
// SHA-256 checksums via `scripts/package_xcframeworks.sh`, verifies it via
// `scripts/verify_spm_package.sh`, uploads it to the GitHub Release, and (when
// `SPM_REPO_TOKEN` is configured) syncs it automatically to the Direct-URL SwiftPM
// mirror repository (`https://github.com/Abeta-dev/kmp-spm`).
//
// Xcode Consumption (Zero Credentials Required):
//   1. File -> Add Package Dependencies... -> https://github.com/Abeta-dev/kmp-spm
//   2. Select the SemVer release (e.g. `0.1.0`; both `0.1.0` and `v0.1.0` work in Xcode).
//   3. Link `AbetaSduiKit` to your iOS/iPadOS 15+ target and/or `AbetaWatchKit` to your
//      watchOS 9+ target.
//
// SwiftPM (`Package.swift`) Consumption:
//   dependencies: [
//       .package(url: "https://github.com/Abeta-dev/kmp-spm.git", from: "0.1.0"),
//   ]
//
// Swift Usage (see `kmp/platforms/apple/AbetaAppleHosts.swift`):
//   // iOS / iPadOS target:
//   import AbetaSduiKit
//   let vc = SduiViewControllerKt.createSduiViewController(screenId: "store-home", bffBaseUrl: nil)
//
//   // watchOS target:
//   import AbetaWatchKit
//   let host = WatchAppleFactoryKt.createAppleWatchCompanionHost(bffBaseUrl: nil)

import PackageDescription

let package = Package(
    name: "AbetaKmpKit",
    platforms: [
        .iOS(.v15),
        .watchOS(.v9),
    ],
    products: [
        .library(
            name: "AbetaSduiKit",
            targets: ["AbetaSduiKit"]
        ),
        .library(
            name: "AbetaWatchKit",
            targets: ["AbetaWatchKit"]
        ),
    ],
    targets: [
        .binaryTarget(
            name: "AbetaSduiKit",
            url: "https://github.com/Abeta-dev/kmp/releases/download/<TAG>/AbetaSduiKit-<TAG>.xcframework.zip",
            checksum: "<SHA256_CHECKSUM_FOR_ABETASDUIKIT_FROM_SHA256SUMS_TXT>"
        ),
        .binaryTarget(
            name: "AbetaWatchKit",
            url: "https://github.com/Abeta-dev/kmp/releases/download/<TAG>/AbetaWatchKit-<TAG>.xcframework.zip",
            checksum: "<SHA256_CHECKSUM_FOR_ABETAWATCHKIT_FROM_SHA256SUMS_TXT>"
        ),
    ]
)
