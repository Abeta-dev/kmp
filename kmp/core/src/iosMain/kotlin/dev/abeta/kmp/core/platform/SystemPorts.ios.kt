@file:Suppress("ktlint:standard:filename", "MatchingDeclarationName")

package dev.abeta.kmp.core.platform

/** Creates the default iOS [FilePickerPort] (host Swift/UIKit apps inject `UIDocumentPickerViewController`). */
actual fun createPlatformFilePickerPort(): FilePickerPort = UnavailableFilePickerPort
