// swift-tools-version: 5.9
// Reference SwiftPM manifest template for Abeta KMP Kit Apple Release XCFrameworks & SwiftUI wrappers.
//
// Each `v*` release generates a concrete `Package.swift` with real download URLs and
// SHA-256 checksums via `scripts/package_xcframeworks.sh`, copies `kmp/platforms/apple/Sources`
// alongside it, verifies the manifest via `scripts/verify_spm_package.sh`, uploads all artifacts
// to the GitHub Release, and (when `SPM_REPO_TOKEN` is configured) syncs both `Package.swift`
// and `Sources/` automatically to the Direct-URL SwiftPM mirror repository
// (`https://github.com/Abeta-dev/kmp-spm`).
//
// Xcode Consumption (Zero Credentials Required):
//   1. File -> Add Package Dependencies... -> https://github.com/Abeta-dev/kmp-spm
//   2. Select the SemVer release (e.g. `0.8.0`; both `0.8.0` and `v0.8.0` work in Xcode).
//   3. Link ready-made SwiftUI wrappers or low-level binary frameworks to your Apple targets:
//        - iOS / iPadOS 15+ SwiftUI:  `AbetaSduiSwiftUI` (exports `AbetaIosRootAppView`, `AbetaIosSduiScreenView`)
//        - iOS / iPadOS 15+ UIKit:    `AbetaSduiKit`
//        - watchOS 9+ SwiftUI:        `AbetaWatchSwiftUI` (exports `AbetaWatchSduiView`, `AbetaWatchViewModel`, `AbetaWatchTheme`)
//        - watchOS 9+ Headless Core:  `AbetaWatchKit`
//
// SwiftPM (`Package.swift`) Consumption:
//   dependencies: [
//       .package(url: "https://github.com/Abeta-dev/kmp-spm.git", from: "0.8.0"),
//   ]
//
// Swift Usage:
//   // iOS / iPadOS SwiftUI target:
//   import AbetaSduiSwiftUI
//   AbetaIosRootAppView(initialScreenId: "store-home", bffBaseUrl: nil)
//
//   // watchOS SwiftUI target:
//   import AbetaWatchSwiftUI
//   AbetaWatchSduiView(bffBaseUrl: nil, initialScreenId: "watch-status")

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
        .library(
            name: "AbetaWatchSwiftUI",
            targets: ["AbetaWatchSwiftUI"]
        ),
        .library(
            name: "AbetaSduiSwiftUI",
            targets: ["AbetaSduiSwiftUI"]
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
        .target(
            name: "AbetaWatchSwiftUI",
            dependencies: ["AbetaWatchKit"],
            path: "Sources/AbetaWatchSwiftUI"
        ),
        .target(
            name: "AbetaSduiSwiftUI",
            dependencies: ["AbetaSduiKit"],
            path: "Sources/AbetaSduiSwiftUI"
        ),
    ]
)
