@file:OptIn(ExperimentalWasmJsInterop::class)
@file:Suppress("ktlint:standard:filename", "MatchingDeclarationName")

package dev.abeta.kmp.core.security

@JsFun(
    "(key, value) => { try { if (typeof window !== 'undefined' && window.sessionStorage) { " +
        "window.sessionStorage.setItem(key, value); } } catch (e) {} }",
)
private external fun jsSessionSet(
    key: String,
    value: String,
)

@JsFun(
    "(key) => { try { if (typeof window !== 'undefined' && window.sessionStorage) { " +
        "return window.sessionStorage.getItem(key); } } catch (e) {} return null; }",
)
private external fun jsSessionGet(key: String): String?

@JsFun(
    "(key) => { try { if (typeof window !== 'undefined' && window.sessionStorage) { " +
        "window.sessionStorage.removeItem(key); } } catch (e) {} }",
)
private external fun jsSessionRemove(key: String)

private class WasmSecureStore(
    serviceName: String,
) : SecureKeyValueStore {
    private val prefix = "abeta_sec_${serviceName.replace(Regex("[^A-Za-z0-9._-]"), "_")}:"
    private val trackedKeys = linkedSetOf<String>()

    override val storageBackend: String = "BrowserSessionStorage"
    override val isHardwareOrOsEncrypted: Boolean = false

    override fun getString(key: String): String? = jsSessionGet(prefix + key)

    override fun putString(
        key: String,
        value: String,
    ) {
        trackedKeys.add(key)
        jsSessionSet(prefix + key, value)
    }

    override fun remove(key: String) {
        trackedKeys.remove(key)
        jsSessionRemove(prefix + key)
    }

    override fun clear() {
        trackedKeys.toList().forEach(::remove)
    }

    override fun contains(key: String): Boolean = getString(key) != null

    override fun keys(): Set<String> = trackedKeys.filter(::contains).toSet()
}

private class WasmUnsupportedBiometricAuthenticator : BiometricAuthenticator {
    override fun availability(): BiometricAvailability = BiometricAvailability.UNAVAILABLE

    override suspend fun authenticate(request: BiometricPromptRequest): BiometricResult =
        BiometricResult.Unavailable("Biometric prompt requires host WebAuthn integration.")
}

/** Creates a browser `sessionStorage`-scoped [SecureKeyValueStore] on WasmJS. */
actual fun createSecureKeyValueStore(serviceName: String): SecureKeyValueStore = WasmSecureStore(serviceName)

/** Creates the WasmJS [BiometricAuthenticator]. */
actual fun createPlatformBiometricAuthenticator(): BiometricAuthenticator = WasmUnsupportedBiometricAuthenticator()
