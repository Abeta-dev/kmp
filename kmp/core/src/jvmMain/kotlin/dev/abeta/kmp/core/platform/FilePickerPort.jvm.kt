@file:Suppress("ktlint:standard:filename", "MatchingDeclarationName")

package dev.abeta.kmp.core.platform

import dev.abeta.kmp.core.coroutines.ioDispatcher
import dev.abeta.kmp.core.error.ForbiddenError
import dev.abeta.kmp.core.error.UnexpectedError
import dev.abeta.kmp.core.result.AppResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import java.awt.AWTError
import java.awt.FileDialog
import java.awt.Frame
import java.awt.HeadlessException
import java.io.File
import java.io.FilenameFilter
import java.nio.file.Files

private const val MAX_INLINE_FILE_BYTES = 4L * 1024L * 1024L

private val MIME_PREFIX_EXTENSIONS: Map<String, Set<String>> =
    mapOf(
        "image/" to setOf("png", "jpg", "jpeg", "gif", "webp", "bmp", "svg", "heic"),
        "audio/" to setOf("wav", "mp3", "m4a", "aac", "ogg", "flac"),
        "video/" to setOf("mp4", "mov", "webm", "mkv", "avi"),
        "text/" to setOf("txt", "csv", "md", "html", "css", "js", "xml", "log"),
    )

private val EXACT_MIME_EXTENSIONS: Map<String, Set<String>> =
    mapOf(
        "application/pdf" to setOf("pdf"),
        "application/json" to setOf("json"),
        "application/zip" to setOf("zip"),
        "image/png" to setOf("png"),
        "image/jpeg" to setOf("jpg", "jpeg"),
        "image/webp" to setOf("webp"),
        "text/plain" to setOf("txt", "log", "md"),
        "text/csv" to setOf("csv"),
    )

internal fun matchesRequestedMimeTypes(
    fileName: String,
    mimeTypes: List<String>,
): Boolean {
    val normalizedTypes = mimeTypes.map { it.trim().lowercase() }.filter { it.isNotEmpty() }
    val acceptsAll = normalizedTypes.isEmpty() || normalizedTypes.any { it == "*/*" || it == "*" }
    val extension = fileName.substringAfterLast('.', missingDelimiterValue = "").lowercase()
    return when {
        acceptsAll -> {
            true
        }

        extension.isEmpty() -> {
            false
        }

        else -> {
            normalizedTypes.any { mime ->
                val exactMatch = EXACT_MIME_EXTENSIONS[mime]?.contains(extension) == true
                val wildcardPrefix =
                    if (mime.endsWith("/*")) {
                        MIME_PREFIX_EXTENSIONS[mime.removeSuffix("*")]?.contains(extension) == true
                    } else {
                        false
                    }
                val suffixExtension = mime.substringAfter('/', missingDelimiterValue = "") == extension
                exactMatch || wildcardPrefix || suffixExtension
            }
        }
    }
}

internal class JvmFilePickerPort : FilePickerPort {
    @Suppress("TooGenericExceptionCaught")
    override suspend fun pickFiles(request: FilePickerRequest): AppResult<List<PickedFile>> {
        if (isJvmHeadlessSafely()) {
            return AppResult.Failure(
                ForbiddenError(
                    resource = "file_picker",
                    message = "JVM file picker is unavailable in a headless runtime.",
                ),
            )
        }
        return withContext(ioDispatcher) {
            try {
                val selectedFiles = showFileDialog(request)
                AppResult.Success(selectedFiles.map(::toPickedFile))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (err: HeadlessException) {
                AppResult.Failure(
                    ForbiddenError(
                        resource = "file_picker",
                        message = "JVM file picker is unavailable in headless mode.",
                        cause = err,
                    ),
                )
            } catch (err: AWTError) {
                AppResult.Failure(
                    ForbiddenError(
                        resource = "file_picker",
                        message = "AWT toolkit unavailable for file picker: ${err.message}",
                        cause = err,
                    ),
                )
            } catch (err: Throwable) {
                AppResult.Failure(
                    UnexpectedError(message = "JVM file picker failed: ${err.message}", cause = err),
                )
            }
        }
    }

    private fun showFileDialog(request: FilePickerRequest): List<File> {
        val dialog = FileDialog(null as Frame?, "Select file", FileDialog.LOAD)
        return try {
            dialog.isMultipleMode = request.allowMultiple
            dialog.filenameFilter = FilenameFilter { _, name -> matchesRequestedMimeTypes(name, request.mimeTypes) }
            dialog.isVisible = true
            val multiple = dialog.files?.toList().orEmpty()
            if (multiple.isNotEmpty()) {
                multiple
            } else {
                val dir = dialog.directory
                val file = dialog.file
                if (dir != null && file != null) listOf(File(dir, file)) else emptyList()
            }
        } finally {
            dialog.dispose()
        }
    }

    private fun toPickedFile(file: File): PickedFile {
        val size = runCatching { file.length() }.getOrNull()
        val mimeType = runCatching { Files.probeContentType(file.toPath()) }.getOrNull()
        val bytes =
            if (size != null && size in 0L..MAX_INLINE_FILE_BYTES) {
                runCatching { file.readBytes() }.getOrNull()
            } else {
                null
            }
        return PickedFile(
            name = file.name,
            mimeType = mimeType,
            sizeBytes = size,
            bytes = bytes,
            uriString = file.toURI().toString(),
        )
    }
}

/** Creates a JVM desktop AWT [FileDialog]-backed [FilePickerPort]. */
actual fun createPlatformFilePickerPort(): FilePickerPort = JvmFilePickerPort()

/** Creates the JVM desktop [LocationPort] (defaults to [UnavailableLocationPort]). */
actual fun createPlatformLocationPort(): LocationPort = UnavailableLocationPort
