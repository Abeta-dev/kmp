@file:OptIn(ExperimentalWasmJsInterop::class)
@file:Suppress("ktlint:standard:filename", "MatchingDeclarationName")

package dev.abeta.kmp.core.security

@JsFun(
    "(key, value) => { try { if (typeof window !== 'undefined' && window.sessionStorage) { " +
        "window.sessionStorage.setItem(key, value); return true; } } catch (e) {} return false; }",
)
private external fun jsSessionSet(
    key: String,
    value: String,
): Boolean

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

@JsFun(
    "() => { try { if (typeof window !== 'undefined' && window.sessionStorage) { " +
        "return window.sessionStorage.length; } } catch (e) {} return 0; }",
)
private external fun jsSessionLength(): Int

@JsFun(
    "(index) => { try { if (typeof window !== 'undefined' && window.sessionStorage) { " +
        "return window.sessionStorage.key(index); } } catch (e) {} return null; }",
)
private external fun jsSessionKeyAt(index: Int): String?

private class WasmSecureStore(
    serviceName: String,
) : SecureKeyValueStore {
    private val prefix = "abeta_sec_${serviceName.replace(Regex("[^A-Za-z0-9._-]"), "_")}:"
    private val fallback = linkedMapOf<String, String>()

    override val storageBackend: String = "BrowserSessionStorage"
    override val isHardwareOrOsEncrypted: Boolean = false

    override fun getString(key: String): String? = jsSessionGet(prefix + key) ?: fallback[key]

    override fun putString(
        key: String,
        value: String,
    ) {
        if (jsSessionSet(prefix + key, value)) {
            fallback.remove(key)
        } else {
            jsSessionRemove(prefix + key)
            fallback[key] = value
        }
    }

    override fun remove(key: String) {
        fallback.remove(key)
        jsSessionRemove(prefix + key)
    }

    override fun clear() {
        keys().toList().forEach(::remove)
        fallback.clear()
    }

    override fun contains(key: String): Boolean = getString(key) != null

    override fun keys(): Set<String> {
        val result = linkedSetOf<String>()
        val length = jsSessionLength()
        for (index in 0 until length) {
            val rawKey = jsSessionKeyAt(index) ?: continue
            if (rawKey.startsWith(prefix)) {
                result.add(rawKey.removePrefix(prefix))
            }
        }
        result.addAll(fallback.keys)
        return result
    }
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
