@file:Suppress("ktlint:standard:filename", "MatchingDeclarationName")

package dev.abeta.kmp.core.platform

/** Creates the watchOS [FilePickerPort] (defaults to [UnavailableFilePickerPort]). */
actual fun createPlatformFilePickerPort(): FilePickerPort = UnavailableFilePickerPort
