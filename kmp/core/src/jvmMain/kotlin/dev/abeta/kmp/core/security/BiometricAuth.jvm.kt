@file:Suppress("ktlint:standard:filename", "MatchingDeclarationName")

package dev.abeta.kmp.core.security

private class JvmUnsupportedBiometricAuthenticator : BiometricAuthenticator {
    override fun availability(): BiometricAvailability = BiometricAvailability.UNAVAILABLE

    override suspend fun authenticate(request: BiometricPromptRequest): BiometricResult =
        BiometricResult.Unavailable("Biometric authentication is not supported on JVM desktop targets.")
}

/** Creates the JVM desktop [BiometricAuthenticator]. */
actual fun createPlatformBiometricAuthenticator(): BiometricAuthenticator = JvmUnsupportedBiometricAuthenticator()
