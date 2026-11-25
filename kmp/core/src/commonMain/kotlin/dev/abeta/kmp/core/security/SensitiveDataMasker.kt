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
    private val sensitiveTokenSequences: List<List<String>> =
        sensitiveKeys.map(::tokenizeKey).filter { it.isNotEmpty() }.distinct()

    /**
     * Returns `true` when [key] matches a registered sensitive field name on exact match or
     * delimiter/camelCase token boundaries (preventing false positives such as `shipping_fee`
     * or `company_name`).
     */
    fun isSensitiveKey(key: String): Boolean {
        val normalized = key.trim().lowercase()
        val tokens = if (normalized in normalizedKeys) emptyList() else tokenizeKey(key)
        val matchesToken =
            tokens.isNotEmpty() &&
                sensitiveTokenSequences.any { pattern -> containsTokenSubsequence(tokens, pattern) }
        return normalized in normalizedKeys || matchesToken
    }

    /** Redacts authorization tokens, secret key-value pairs, JSON secret strings, and custom patterns inside [text]. */
    fun redactText(text: String): String {
        var sanitized = BEARER_REGEX.replace(text, "Bearer $replacement")
        sanitized =
            AUTH_HEADER_SCHEME_REGEX.replace(sanitized) { match ->
                "${match.groupValues[1]}$replacement"
            }
        sanitized =
            API_KEY_SCHEME_REGEX.replace(sanitized) { match ->
                "${match.groupValues[1]}$replacement"
            }
        sanitized =
            JSON_STRING_FIELD_REGEX.replace(sanitized) { match ->
                if (isSensitiveKey(match.groupValues[2])) {
                    "${match.groupValues[1]}$replacement${match.groupValues[JSON_CLOSING_QUOTE_GROUP]}"
                } else {
                    match.value
                }
            }
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
        private const val JSON_CLOSING_QUOTE_GROUP = 4

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
        private val AUTH_HEADER_SCHEME_REGEX =
            Regex("(?i)(Authorization\\s*:\\s*(?:Basic|ApiKey|Token|Digest)\\s+)([^\\s,;\"']+)")
        private val API_KEY_SCHEME_REGEX =
            Regex("(?i)\\b(ApiKey\\s+)([A-Za-z0-9._~+/=-]+)")
        private val JSON_STRING_FIELD_REGEX =
            Regex("(\"([A-Za-z0-9_.-]{1,128})\"\\s*:\\s*\")((?:\\\\.|[^\"\\\\])*)(\")")
        private val KEY_VALUE_SECRET_REGEX =
            Regex(
                "(?i)\\b(password|passwd|secret|token|access_token|refresh_token|" +
                    "api_key|apikey|otp|authorization)=([^\\s,;]+)",
            )

        private fun tokenizeKey(raw: String): List<String> {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return emptyList()
            val tokens = mutableListOf<String>()
            val current = StringBuilder()
            for (index in trimmed.indices) {
                val ch = trimmed[index]
                if (!ch.isLetterOrDigit()) {
                    flushToken(current, tokens)
                    continue
                }
                val isCamelSplit =
                    current.isNotEmpty() &&
                        ch.isUpperCase() &&
                        isCamelBoundary(trimmed = trimmed, index = index, prev = trimmed[index - 1])
                if (isCamelSplit) {
                    flushToken(current, tokens)
                }
                current.append(ch.lowercaseChar())
            }
            flushToken(current, tokens)
            return tokens
        }

        private fun isCamelBoundary(
            trimmed: String,
            index: Int,
            prev: Char,
        ): Boolean =
            prev.isLowerCase() ||
                prev.isDigit() ||
                (prev.isUpperCase() && index + 1 < trimmed.length && trimmed[index + 1].isLowerCase())

        private fun flushToken(
            buffer: StringBuilder,
            tokens: MutableList<String>,
        ) {
            if (buffer.isNotEmpty()) {
                tokens.add(buffer.toString())
                buffer.clear()
            }
        }

        private fun containsTokenSubsequence(
            tokens: List<String>,
            pattern: List<String>,
        ): Boolean {
            if (pattern.isEmpty() || pattern.size > tokens.size) return false
            val maxStart = tokens.size - pattern.size
            return (0..maxStart).any { start ->
                pattern.indices.all { offset -> tokens[start + offset] == pattern[offset] }
            }
        }

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
