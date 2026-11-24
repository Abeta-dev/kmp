@file:OptIn(ExperimentalForeignApi::class)

package dev.abeta.kmp.state.storage

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSHomeDirectory
import platform.Foundation.NSUserDomainMask

internal actual fun resolveAppleDocumentsDirectory(): String {
    val url =
        NSFileManager.defaultManager.URLForDirectory(
            directory = NSDocumentDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = true,
            error = null,
        )
    return url?.path ?: "${NSHomeDirectory()}/Documents"
}
