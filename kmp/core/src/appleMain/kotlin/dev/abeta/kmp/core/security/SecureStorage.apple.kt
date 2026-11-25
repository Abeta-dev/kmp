@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
@file:Suppress("ktlint:standard:filename", "MatchingDeclarationName")

package dev.abeta.kmp.core.security

import dev.abeta.kmp.core.storage.InMemoryKeyValueStore
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSDictionary
import platform.Foundation.NSLock
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecItemUpdate
import platform.Security.errSecDuplicateItem
import platform.Security.errSecItemNotFound
import platform.Security.errSecMissingEntitlement
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitAll
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnAttributes
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

internal expect fun encodeUtf8NsData(value: String): NSData?

internal expect fun decodeUtf8NsData(data: NSData): String?

internal expect fun createMutableCfDictionary(): CFMutableDictionaryRef?

/**
 * Apple Keychain Services [SecureKeyValueStore] backed by `SecItem*` APIs, with a graceful
 * in-memory fallback when running unsigned headless test binaries lacking Keychain entitlements.
 */
@Suppress("TooManyFunctions")
private class AppleKeychainSecureStore(
    private val serviceName: String,
) : SecureKeyValueStore {
    private val lock = NSLock()
    private val fallbackStore = InMemoryKeyValueStore()
    private var useFallbackOnly = false

    override val storageBackend: String
        get() = withLock { if (useFallbackOnly) "AppleKeychainFallbackInMemory" else "AppleKeychain" }

    override val isHardwareOrOsEncrypted: Boolean
        get() = withLock { !useFallbackOnly }

    override fun getString(key: String): String? = withLock { getStringLocked(key) }

    override fun putString(
        key: String,
        value: String,
    ) = withLock {
        if (useFallbackOnly) {
            fallbackStore.putString(key, value)
            return@withLock
        }
        val encoded = encodeUtf8NsData(value)
        if (encoded != null) {
            writeKeychainItemLocked(key = key, rawValue = value, encoded = encoded)
        }
    }

    override fun remove(key: String) =
        withLock {
            if (useFallbackOnly) {
                fallbackStore.remove(key)
                return@withLock
            }
            val status = withKeychainQuery(account = key) { dictRef -> SecItemDelete(dictRef) }
            if (shouldUseFallback(status)) {
                useFallbackOnly = true
                fallbackStore.remove(key)
            }
        }

    override fun clear() =
        withLock {
            if (useFallbackOnly) {
                fallbackStore.clear()
                return@withLock
            }
            val status = withKeychainQuery(account = null) { dictRef -> SecItemDelete(dictRef) }
            if (shouldUseFallback(status)) {
                useFallbackOnly = true
                fallbackStore.clear()
            }
        }

    override fun contains(key: String): Boolean = withLock { getStringLocked(key) != null }

    override fun keys(): Set<String> =
        withLock {
            if (useFallbackOnly) return@withLock fallbackStore.keys()
            memScoped {
                val resultRef = alloc<CFTypeRefVar>()
                val status =
                    withKeychainQuery(
                        account = null,
                        cfEntries =
                            listOf(
                                kSecReturnAttributes to kCFBooleanTrue,
                                kSecMatchLimit to kSecMatchLimitAll,
                            ),
                    ) { dictRef ->
                        SecItemCopyMatching(dictRef, resultRef.ptr)
                    }
                if (shouldUseFallback(status)) {
                    useFallbackOnly = true
                    return@memScoped fallbackStore.keys()
                }
                if (status != errSecSuccess) return@memScoped emptySet()
                val list = CFBridgingRelease(resultRef.value) as? List<*> ?: return@memScoped emptySet()
                list
                    .mapNotNull { entry ->
                        val dict = entry as? NSDictionary
                        dict?.objectForKey(ATTR_ACCOUNT_KEY) as? String
                    }.toSet()
            }
        }

    private fun getStringLocked(key: String): String? {
        if (useFallbackOnly) return fallbackStore.getString(key)
        return memScoped {
            val resultRef = alloc<CFTypeRefVar>()
            val status =
                withKeychainQuery(
                    account = key,
                    cfEntries = listOf(kSecReturnData to kCFBooleanTrue, kSecMatchLimit to kSecMatchLimitOne),
                ) { dictRef ->
                    SecItemCopyMatching(dictRef, resultRef.ptr)
                }
            if (shouldUseFallback(status)) {
                useFallbackOnly = true
                return@memScoped fallbackStore.getString(key)
            }
            if (status != errSecSuccess) return@memScoped null
            val data = CFBridgingRelease(resultRef.value) as? NSData ?: return@memScoped null
            decodeUtf8NsData(data)
        }
    }

    private fun writeKeychainItemLocked(
        key: String,
        rawValue: String,
        encoded: NSData,
    ) {
        val addStatus = addKeychainItem(key = key, encoded = encoded)
        if (shouldUseFallback(addStatus)) {
            useFallbackOnly = true
            fallbackStore.putString(key, rawValue)
        } else if (addStatus == errSecDuplicateItem) {
            updateExistingItemLocked(key = key, rawValue = rawValue, encoded = encoded)
        }
    }

    private fun addKeychainItem(
        key: String,
        encoded: NSData,
    ): Int =
        withKeychainQuery(
            account = key,
            cfEntries = listOf(kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly),
            objectEntries = listOf(kSecValueData to encoded),
        ) { dictRef ->
            SecItemAdd(dictRef, null)
        }

    private fun updateExistingItemLocked(
        key: String,
        rawValue: String,
        encoded: NSData,
    ) {
        val updateDict = createMutableCfDictionary()
        val retainedEncoded = CFBridgingRetain(encoded)
        val updateStatus =
            try {
                if (updateDict != null && retainedEncoded != null) {
                    CFDictionarySetValue(updateDict, kSecValueData, retainedEncoded)
                }
                withKeychainQuery(account = key) { queryRef ->
                    SecItemUpdate(queryRef, updateDict)
                }
            } finally {
                retainedEncoded?.let { CFRelease(it) }
                updateDict?.let { CFRelease(it) }
            }
        if (shouldUseFallback(updateStatus)) {
            useFallbackOnly = true
            fallbackStore.putString(key, rawValue)
        } else if (updateStatus == errSecItemNotFound) {
            val retryStatus = addKeychainItem(key = key, encoded = encoded)
            if (shouldUseFallback(retryStatus)) {
                useFallbackOnly = true
                fallbackStore.putString(key, rawValue)
            }
        }
    }

    private inline fun <T> withLock(block: () -> T): T {
        lock.lock()
        return try {
            block()
        } finally {
            lock.unlock()
        }
    }

    private fun <R> withKeychainQuery(
        account: String?,
        cfEntries: List<Pair<CFStringRef?, CFTypeRef?>> = emptyList(),
        objectEntries: List<Pair<CFStringRef?, Any?>> = emptyList(),
        block: (CFDictionaryRef?) -> R,
    ): R {
        val dict = createMutableCfDictionary()
        val retainedObjects = retainQueryValues(account = account, objectEntries = objectEntries)
        return try {
            dict?.let { target ->
                populateQueryDict(target = target, cfEntries = cfEntries, retainedObjects = retainedObjects)
            }
            block(dict)
        } finally {
            retainedObjects.forEach { (_, ref) -> ref?.let { CFRelease(it) } }
            dict?.let { CFRelease(it) }
        }
    }

    private fun retainQueryValues(
        account: String?,
        objectEntries: List<Pair<CFStringRef?, Any?>>,
    ): List<Pair<CFStringRef?, CFTypeRef?>> =
        buildList {
            add(kSecAttrService to CFBridgingRetain(serviceName))
            if (account != null) {
                add(kSecAttrAccount to CFBridgingRetain(account))
            }
            objectEntries.forEach { (key, value) ->
                add(key to value?.let { CFBridgingRetain(it) })
            }
        }

    private fun populateQueryDict(
        target: CFMutableDictionaryRef,
        cfEntries: List<Pair<CFStringRef?, CFTypeRef?>>,
        retainedObjects: List<Pair<CFStringRef?, CFTypeRef?>>,
    ) {
        CFDictionarySetValue(target, kSecClass, kSecClassGenericPassword)
        (cfEntries + retainedObjects).forEach { (key, ref) ->
            if (key != null && ref != null) {
                CFDictionarySetValue(target, key, ref)
            }
        }
    }

    private companion object {
        const val ATTR_ACCOUNT_KEY = "acct"

        fun shouldUseFallback(status: Int): Boolean = status == errSecMissingEntitlement
    }
}

/** Creates an Apple Keychain-backed [SecureKeyValueStore] for iOS, iPadOS, macOS, and watchOS. */
actual fun createSecureKeyValueStore(serviceName: String): SecureKeyValueStore = AppleKeychainSecureStore(serviceName)
