package dev.abeta.kmp.core.security

/**
 * Semantic classification for sensitive fields when masking UI display or redacting logs/telemetry.
 */
enum class SensitiveCategory {
    FINANCIAL,
    CONTRACT,
    PII,
    CREDENTIAL,
}

/**
 * Configuration controlling how values are masked for UI rendering or diagnostics.
 */
data class MaskingOptions(
    val isMasked: Boolean = true,
    val maskPattern: String = DEFAULT_MASK_PATTERN,
    val visibleSuffixLength: Int = 0,
    val category: SensitiveCategory = SensitiveCategory.PII,
) {
    companion object {
        const val DEFAULT_MASK_PATTERN: String = "••••••"
    }
}

/**
 * Pure multiplatform utility for masking sensitive display values and redacting secrets from logs/maps.
 */
class SensitiveDataRedactor(
    sensitiveKeys: Set<String> = DEFAULT_SENSITIVE_KEYS,
    private val replacement: String = DEFAULT_REDACTED_TOKEN,
    private val customPatterns: List<Regex> = emptyList(),
) {
    private val normalizedKeys: Set<String> = sensitiveKeys.map { it.trim().lowercase() }.toSet()

    /** Returns `true` when [key] matches a registered sensitive field name. */
    fun isSensitiveKey(key: String): Boolean {
        val normalized = key.trim().lowercase()
        return normalized in normalizedKeys || normalizedKeys.any { normalized.contains(it) }
    }

    /** Redacts authorization tokens, secret key-value pairs, and custom regex patterns inside [text]. */
    fun redactText(text: String): String {
        var sanitized = BEARER_REGEX.replace(text, "Bearer $replacement")
        sanitized = KEY_VALUE_SECRET_REGEX.replace(sanitized) { match -> "${match.groupValues[1]}=$replacement" }
        for (pattern in customPatterns) {
            sanitized = pattern.replace(sanitized, replacement)
        }
        return sanitized
    }

    /** Returns a copy of [properties] with any sensitive key values replaced by [replacement]. */
    fun redactMap(properties: Map<String, String>): Map<String, String> =
        properties.mapValues { (key, value) ->
            if (isSensitiveKey(key)) replacement else redactText(value)
        }

    companion object {
        const val DEFAULT_REDACTED_TOKEN: String = "[REDACTED]"
        private const val EMPTY_DISPLAY_PLACEHOLDER = "—"

        val DEFAULT_SENSITIVE_KEYS: Set<String> =
            setOf(
                "password",
                "passwd",
                "secret",
                "token",
                "access_token",
                "refresh_token",
                "authorization",
                "api_key",
                "apikey",
                "otp",
                "pin",
                "cvv",
                "ssn",
                "pan",
                "aadhaar",
            )

        private val BEARER_REGEX = Regex("(?i)Bearer\\s+[A-Za-z0-9._~+/=-]+")
        private val KEY_VALUE_SECRET_REGEX =
            Regex(
                "(?i)\\b(password|passwd|secret|token|access_token|refresh_token|" +
                    "api_key|otp|authorization)=([^\\s,;]+)",
            )

        /** Shared default redactor instance with standard credential and PII patterns. */
        val Default: SensitiveDataRedactor = SensitiveDataRedactor()

        /**
         * Masks [value] according to [options], optionally preserving the trailing
         * [MaskingOptions.visibleSuffixLength] characters for partial user recognition.
         */
        fun maskValue(
            value: String?,
            options: MaskingOptions = MaskingOptions(),
        ): String {
            val suffixLen = options.visibleSuffixLength.coerceAtLeast(0)
            return when {
                value == null -> if (options.isMasked) options.maskPattern else EMPTY_DISPLAY_PLACEHOLDER
                !options.isMasked -> value
                suffixLen == 0 || value.length <= suffixLen -> options.maskPattern
                else -> options.maskPattern + value.takeLast(suffixLen)
            }
        }
    }
}
