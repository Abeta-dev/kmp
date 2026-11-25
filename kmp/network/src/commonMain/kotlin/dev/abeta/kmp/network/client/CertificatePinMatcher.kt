@file:Suppress("ktlint:standard:filename", "MatchingDeclarationName", "MagicNumber")

package dev.abeta.kmp.network.client

/**
 * Pure multiplatform TLS certificate-pin matching & ASN.1 SubjectPublicKeyInfo (SPKI) extraction
 * helpers shared across JVM and Apple Darwin HTTP engines.
 */
internal object CertificatePinMatcher {
    private const val ASN1_SEQUENCE_TAG = 0x30
    private const val ASN1_VERSION_TAG = 0xA0
    private const val EXPECTED_PRECEDING_TBS_FIELDS = 5
    private const val LONG_FORM_MASK = 0x80
    private const val LENGTH_MASK = 0x7F
    private const val BITS_PER_BYTE = 8

    /** Returns `true` when [host] matches a literal hostname or single-label wildcard `*.domain` pattern. */
    fun matchesHostname(
        host: String,
        pattern: String,
    ): Boolean {
        val cleanHost = host.trim().trimEnd('.').lowercase()
        val cleanPattern = pattern.trim().trimEnd('.').lowercase()
        return when {
            cleanHost.isEmpty() || cleanPattern.isEmpty() -> {
                false
            }

            cleanPattern.startsWith("*.") -> {
                val suffix = cleanPattern.removePrefix("*.")
                val dotIndex = cleanHost.indexOf('.')
                dotIndex > 0 && cleanHost.substring(dotIndex + 1) == suffix
            }

            else -> {
                cleanHost == cleanPattern
            }
        }
    }

    /** Returns all configured pins matching [host]. */
    fun pinsForHost(
        host: String,
        certificatePins: Map<String, List<String>>,
    ): Set<String> =
        certificatePins
            .asSequence()
            .filter { (pattern, pins) -> pins.isNotEmpty() && matchesHostname(host, pattern) }
            .flatMap { (_, pins) -> pins.asSequence() }
            .map { pin -> pin.trim() }
            .filter { pin -> pin.startsWith("sha256/") }
            .toSet()

    /**
     * Extracts raw DER-encoded `SubjectPublicKeyInfo` bytes from an X.509 DER certificate,
     * or returns `null` when malformed.
     */
    fun extractSpkiDerOrNull(certDer: ByteArray): ByteArray? {
        val tbsBounds = readTbsBounds(certDer)
        val spkiCursor = tbsBounds?.let { advanceToSpkiCursor(certDer, it) }
        val spkiBounds =
            spkiCursor?.let { cursor ->
                readTlv(certDer, cursor)?.takeIf { bounds ->
                    bounds.end <= tbsBounds.end && (certDer[cursor].toInt() and 0xFF) == ASN1_SEQUENCE_TAG
                }
            }
        return if (spkiCursor != null && spkiBounds != null) {
            certDer.copyOfRange(spkiCursor, spkiBounds.end)
        } else {
            null
        }
    }

    private fun readTbsBounds(certDer: ByteArray): TlvBounds? =
        readTlv(certDer, 0)?.let { outer ->
            readTlv(certDer, outer.contentStart)?.takeIf { tbs ->
                (certDer[outer.contentStart].toInt() and 0xFF) == ASN1_SEQUENCE_TAG && tbs.end <= outer.end
            }
        }

    private fun advanceToSpkiCursor(
        certDer: ByteArray,
        tbsBounds: TlvBounds,
    ): Int? {
        var cursor: Int? = tbsBounds.contentStart
        val tbsEnd = tbsBounds.end
        if (cursor != null && cursor < tbsEnd && (certDer[cursor].toInt() and 0xFF) == ASN1_VERSION_TAG) {
            cursor = readTlv(certDer, cursor)?.end
        }
        repeat(EXPECTED_PRECEDING_TBS_FIELDS) {
            cursor = cursor?.takeIf { it < tbsEnd }?.let { readTlv(certDer, it)?.end }
        }
        return cursor
    }

    private data class TlvBounds(
        val contentStart: Int,
        val end: Int,
    )

    private fun readTlv(
        bytes: ByteArray,
        offset: Int,
    ): TlvBounds? {
        val header = if (offset >= 0 && offset + 2 <= bytes.size) parseLength(bytes, offset + 1) else null
        return header?.let { (contentStart, length) ->
            val endLong = contentStart.toLong() + length
            if (length >= 0L && endLong <= bytes.size.toLong()) {
                TlvBounds(contentStart = contentStart, end = endLong.toInt())
            } else {
                null
            }
        }
    }

    private fun parseLength(
        bytes: ByteArray,
        lengthOffset: Int,
    ): Pair<Int, Long>? {
        val lengthByte = bytes[lengthOffset].toInt() and 0xFF
        val afterLengthByte = lengthOffset + 1
        return if ((lengthByte and LONG_FORM_MASK) == 0) {
            afterLengthByte to lengthByte.toLong()
        } else {
            val count = lengthByte and LENGTH_MASK
            if (count in 1..4 && afterLengthByte + count <= bytes.size) {
                var parsed = 0L
                repeat(count) { idx ->
                    parsed = (parsed shl BITS_PER_BYTE) or (bytes[afterLengthByte + idx].toInt() and 0xFF).toLong()
                }
                (afterLengthByte + count) to parsed
            } else {
                null
            }
        }
    }
}
