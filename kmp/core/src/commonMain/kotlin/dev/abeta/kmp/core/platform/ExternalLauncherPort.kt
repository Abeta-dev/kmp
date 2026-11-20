package dev.abeta.kmp.core.platform

import dev.abeta.kmp.core.error.ValidationError
import dev.abeta.kmp.core.result.AppResult

private val DEFAULT_ALLOWED_URL_SCHEMES = setOf("https", "http", "mailto", "tel")

/**
 * Cross-platform external browser / OS URL launcher port.
 */
interface ExternalLauncherPort {
    /** Returns `true` when [url] has a safe allowed scheme and can be dispatched by the OS. */
    fun canOpenUrl(url: String): Boolean

    /** Opens [url] in the OS browser or matching external handler app. */
    suspend fun openUrl(url: String): AppResult<Unit>
}

/**
 * Validates [url] against [allowedSchemes] (blocking unsafe schemes such as `javascript:` or `file:`).
 */
fun validateExternalUrl(
    url: String,
    allowedSchemes: Set<String> = DEFAULT_ALLOWED_URL_SCHEMES,
): AppResult<String> {
    val trimmed = url.trim()
    val schemeEnd = trimmed.indexOf(':')
    val scheme = if (schemeEnd > 0) trimmed.substring(0, schemeEnd).lowercase() else ""
    return when {
        trimmed.isEmpty() -> {
            AppResult.Failure(ValidationError(field = "url", message = "URL must not be blank."))
        }

        scheme !in allowedSchemes -> {
            AppResult.Failure(
                ValidationError(
                    field = "url",
                    message = "URL scheme '$scheme' is not permitted (allowed: ${allowedSchemes.joinToString()}).",
                ),
            )
        }

        else -> {
            AppResult.Success(trimmed)
        }
    }
}

/**
 * Test double capturing external URL launch invocations.
 */
class RecordingExternalLauncherPort(
    private val canOpen: Boolean = true,
) : ExternalLauncherPort {
    private val _openedUrls = mutableListOf<String>()

    /** Immutable snapshot of successfully validated and opened URLs. */
    val openedUrls: List<String>
        get() = _openedUrls.toList()

    override fun canOpenUrl(url: String): Boolean = canOpen && validateExternalUrl(url) is AppResult.Success

    override suspend fun openUrl(url: String): AppResult<Unit> =
        when (val validated = validateExternalUrl(url)) {
            is AppResult.Failure -> {
                validated
            }

            is AppResult.Success -> {
                _openedUrls.add(validated.value)
                AppResult.Success(Unit)
            }
        }
}

/** Creates the platform-native [ExternalLauncherPort] for the current KMP target. */
expect fun createPlatformExternalLauncher(): ExternalLauncherPort
