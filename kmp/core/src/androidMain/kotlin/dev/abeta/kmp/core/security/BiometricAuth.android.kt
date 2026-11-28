@file:Suppress("ktlint:standard:filename", "MatchingDeclarationName")

package dev.abeta.kmp.core.security

private object UnavailableBiometricAuthenticator : BiometricAuthenticator {
    override fun availability(): BiometricAvailability = BiometricAvailability.UNAVAILABLE

    override suspend fun authenticate(request: BiometricPromptRequest): BiometricResult =
        BiometricResult.Unavailable(
            "Android BiometricPrompt requires a host FragmentActivity; wire AndroidPlatformBridgeFactory.",
        )
}

/**
 * Creates the default Android [BiometricAuthenticator].
 *
 * Interactive `BiometricPrompt` authentication requires a host `FragmentActivity`, so the
 * context-free default returns [UnavailableBiometricAuthenticator] unless overridden by host glue.
 */
actual fun createPlatformBiometricAuthenticator(): BiometricAuthenticator = UnavailableBiometricAuthenticator
