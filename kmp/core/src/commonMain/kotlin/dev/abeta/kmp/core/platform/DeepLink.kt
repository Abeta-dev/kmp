@file:Suppress("ktlint:standard:filename", "MatchingDeclarationName")

package dev.abeta.kmp.core.platform

private const val HEX_RADIX = 16
private const val HEX_PAIR_LEN = 2

/**
 * Parsed destination screen and query parameters extracted from an app deep link or Universal/App Link.
 */
data class DeepLinkTarget(
    val rawUri: String,
    val scheme: String,
    val host: String?,
    val screenId: String,
    val params: Map<String, String> = emptyMap(),
)

/**
 * Pure multiplatform parser normalizing custom-scheme (`abeta://store-home`) and HTTPS App/Universal
 * links (`https://app.abeta.dev/screens/store-home?promo=FRESH10`) into SDUI screen identifiers.
 */
object DeepLinkParser {
    private val ignoredRoutePrefixes = setOf("screen", "screens", "s")

    /** Parses [uri] into a [DeepLinkTarget], or returns `null` when invalid or missing a screen id. */
    fun parse(uri: String): DeepLinkTarget? {
        val trimmed = uri.trim()
        val schemeIndex = trimmed.indexOf("://")
        val target: DeepLinkTarget? =
            if (schemeIndex <= 0) {
                null
            } else {
                val scheme = trimmed.substring(0, schemeIndex).lowercase()
                val afterScheme = trimmed.substring(schemeIndex + 3)
                val withoutFragment = afterScheme.substringBefore('#')
                val queryStart = withoutFragment.indexOf('?')
                val authorityAndPath =
                    if (queryStart >= 0) withoutFragment.substring(0, queryStart) else withoutFragment
                val query =
                    if (queryStart >= 0) withoutFragment.substring(queryStart + 1) else ""
                val (host, pathSegments) = splitAuthorityAndPath(authorityAndPath)
                resolveScreenId(scheme = scheme, host = host, pathSegments = pathSegments)?.let { screenId ->
                    DeepLinkTarget(
                        rawUri = trimmed,
                        scheme = scheme,
                        host = host,
                        screenId = screenId,
                        params = parseQueryParams(query),
                    )
                }
            }
        return target
    }

    private fun splitAuthorityAndPath(authorityAndPath: String): Pair<String?, List<String>> {
        val slashIndex = authorityAndPath.indexOf('/')
        val authority =
            if (slashIndex >= 0) authorityAndPath.substring(0, slashIndex) else authorityAndPath
        val rawPath = if (slashIndex >= 0) authorityAndPath.substring(slashIndex + 1) else ""
        val host = extractHost(authority)
        val segments = rawPath.split('/').filter { it.isNotBlank() }
        return host to segments
    }

    private fun extractHost(authority: String): String? {
        val hostAndPort = authority.substringAfterLast('@')
        val hostOnly =
            if (hostAndPort.startsWith("[")) {
                val closeBracket = hostAndPort.indexOf(']')
                if (closeBracket > 0) hostAndPort.substring(0, closeBracket + 1) else hostAndPort
            } else {
                hostAndPort.substringBefore(':')
            }
        return hostOnly.takeIf { it.isNotBlank() }
    }

    private fun resolveScreenId(
        scheme: String,
        host: String?,
        pathSegments: List<String>,
    ): String? {
        val filteredSegments = pathSegments.dropWhile { it.lowercase() in ignoredRoutePrefixes }
        val isCustomHostScreen =
            scheme != "http" &&
                scheme != "https" &&
                !host.isNullOrBlank() &&
                host.lowercase() !in ignoredRoutePrefixes
        return when {
            filteredSegments.isNotEmpty() -> {
                percentDecode(filteredSegments.first(), plusAsSpace = false)
            }

            isCustomHostScreen -> {
                percentDecode(host, plusAsSpace = false)
            }

            else -> {
                null
            }
        }?.takeIf { it.isNotBlank() }
    }

    private fun parseQueryParams(query: String): Map<String, String> {
        if (query.isBlank()) return emptyMap()
        val result = linkedMapOf<String, String>()
        query.split('&').forEach { pair ->
            if (pair.isNotEmpty()) {
                val eqIndex = pair.indexOf('=')
                if (eqIndex > 0) {
                    val key = percentDecode(pair.substring(0, eqIndex), plusAsSpace = true)
                    val value = percentDecode(pair.substring(eqIndex + 1), plusAsSpace = true)
                    if (key.isNotEmpty()) {
                        result[key] = value
                    }
                } else {
                    val key = percentDecode(pair, plusAsSpace = true)
                    if (key.isNotEmpty()) {
                        result[key] = ""
                    }
                }
            }
        }
        return result
    }

    private fun percentDecode(
        value: String,
        plusAsSpace: Boolean,
    ): String {
        val bytes = ArrayList<Byte>(value.length)
        var i = 0
        while (i < value.length) {
            val ch = value[i]
            if (plusAsSpace && ch == '+') {
                bytes.add(' '.code.toByte())
                i += 1
            } else if (ch == '%' && i + HEX_PAIR_LEN < value.length) {
                val hex = value.substring(i + 1, i + 1 + HEX_PAIR_LEN)
                val parsed = hex.toIntOrNull(HEX_RADIX)
                if (parsed != null) {
                    bytes.add(parsed.toByte())
                    i += 1 + HEX_PAIR_LEN
                } else {
                    bytes.addAll(ch.toString().encodeToByteArray().toList())
                    i += 1
                }
            } else {
                bytes.addAll(ch.toString().encodeToByteArray().toList())
                i += 1
            }
        }
        return bytes.toByteArray().decodeToString()
    }
}
