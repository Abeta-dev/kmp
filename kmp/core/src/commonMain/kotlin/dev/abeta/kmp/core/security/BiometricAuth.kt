@file:Suppress("ktlint:standard:filename", "MatchingDeclarationName")

package dev.abeta.kmp.core.security

/**
 * Biometric hardware / enrollment state reported by the host OS.
 */
enum class BiometricAvailability {
    AVAILABLE,
    NOT_ENROLLED,
    UNAVAILABLE,
}

/**
 * Request configuration presented on the OS biometric / device-passcode prompt.
 */
data class BiometricPromptRequest(
    val reason: String,
    val title: String? = null,
    val allowDeviceCredential: Boolean = true,
) {
    init {
        require(reason.isNotBlank()) { "BiometricPromptRequest.reason must not be blank." }
    }
}

/**
 * Typed result returned from [BiometricAuthenticator.authenticate].
 */
sealed interface BiometricResult {
    data object Authenticated : BiometricResult

    data object Cancelled : BiometricResult

    data class Unavailable(
        val reason: String,
    ) : BiometricResult

    data class Failed(
        val reason: String,
    ) : BiometricResult
}

/**
 * Cross-platform OS biometric verification port (FaceID/TouchID, BiometricPrompt).
 */
interface BiometricAuthenticator {
    /** Reports whether biometric authentication can currently be evaluated. */
    fun availability(): BiometricAvailability

    /** Prompts the user for biometric / device credential confirmation. */
    suspend fun authenticate(request: BiometricPromptRequest): BiometricResult
}

/**
 * Controllable [BiometricAuthenticator] test double for deterministic unit and UI tests.
 */
class FakeBiometricAuthenticator(
    private val availabilityState: BiometricAvailability = BiometricAvailability.AVAILABLE,
    private val nextResult: BiometricResult = BiometricResult.Authenticated,
) : BiometricAuthenticator {
    private val _requests = mutableListOf<BiometricPromptRequest>()

    /** Snapshot of all biometric requests submitted to this test double. */
    val requests: List<BiometricPromptRequest>
        get() = _requests.toList()

    override fun availability(): BiometricAvailability = availabilityState

    override suspend fun authenticate(request: BiometricPromptRequest): BiometricResult {
        _requests.add(request)
        return nextResult
    }
}

/** Creates the platform-native [BiometricAuthenticator] for the current KMP target. */
expect fun createPlatformBiometricAuthenticator(): BiometricAuthenticator
