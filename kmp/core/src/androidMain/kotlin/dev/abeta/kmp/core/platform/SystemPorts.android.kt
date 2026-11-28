@file:Suppress("ktlint:standard:filename", "MatchingDeclarationName")

package dev.abeta.kmp.core.platform

/**
 * Creates the default Android [LocationPort] (host apps bind fused GPS via `AndroidPlatformBridgeFactory`).
 */
actual fun createPlatformLocationPort(): LocationPort = UnavailableLocationPort

/**
 * Creates the default Android [FilePickerPort] (host apps bind `ActivityResultLauncher` via
 * `AndroidPlatformBridgeFactory`).
 */
actual fun createPlatformFilePickerPort(): FilePickerPort = UnavailableFilePickerPort
