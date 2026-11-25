@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
@file:Suppress("ktlint:standard:filename", "MatchingDeclarationName")

package dev.abeta.kmp.network.client

import io.ktor.client.engine.darwin.DarwinClientEngineConfig
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.convert
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFRelease
import platform.Foundation.NSURLAuthenticationMethodServerTrust
import platform.Foundation.NSURLCredential
import platform.Foundation.NSURLSessionAuthChallengeCancelAuthenticationChallenge
import platform.Foundation.NSURLSessionAuthChallengePerformDefaultHandling
import platform.Foundation.NSURLSessionAuthChallengeUseCredential
import platform.Foundation.credentialForTrust
import platform.Foundation.serverTrust
import platform.Security.SecCertificateCopyData
import platform.Security.SecTrustEvaluateWithError
import platform.Security.SecTrustGetCertificateAtIndex
import platform.Security.SecTrustGetCertificateCount
import platform.Security.SecTrustRef

internal actual fun DarwinClientEngineConfig.configureApplePinnedChallenge(certificatePins: Map<String, List<String>>) {
    handleChallenge { _, _, challenge, completionHandler ->
        val space = challenge.protectionSpace
        val hostPins = CertificatePinMatcher.pinsForHost(space.host, certificatePins)
        val trust = space.serverTrust
        when {
            space.authenticationMethod != NSURLAuthenticationMethodServerTrust || hostPins.isEmpty() -> {
                completionHandler(NSURLSessionAuthChallengePerformDefaultHandling.convert(), null)
            }

            trust != null && certificateChainMatchesPins(extractTrustChainDers(trust), hostPins) -> {
                completionHandler(
                    NSURLSessionAuthChallengeUseCredential.convert(),
                    NSURLCredential.credentialForTrust(trust),
                )
            }

            else -> {
                completionHandler(NSURLSessionAuthChallengeCancelAuthenticationChallenge.convert(), null)
            }
        }
    }
}

private fun extractTrustChainDers(trust: SecTrustRef): List<ByteArray> {
    if (!SecTrustEvaluateWithError(trust, null)) return emptyList()
    val certCount = SecTrustGetCertificateCount(trust).toLong()
    return (0L until certCount).mapNotNull { index -> extractCertificateDerAt(trust, index) }
}

private fun extractCertificateDerAt(
    trust: SecTrustRef,
    index: Long,
): ByteArray? {
    val cert = SecTrustGetCertificateAtIndex(trust, index.convert())
    val derData = cert?.let { SecCertificateCopyData(it) } ?: return null
    return try {
        val ptr = CFDataGetBytePtr(derData)
        val lenLong = CFDataGetLength(derData).toLong()
        if (ptr != null && lenLong > 0L && lenLong <= Int.MAX_VALUE.toLong()) {
            ptr.reinterpret<ByteVar>().readBytes(lenLong.toInt())
        } else {
            null
        }
    } finally {
        CFRelease(derData)
    }
}
