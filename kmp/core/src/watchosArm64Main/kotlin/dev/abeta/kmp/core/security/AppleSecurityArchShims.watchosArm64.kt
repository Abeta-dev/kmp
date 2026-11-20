@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
@file:Suppress("ktlint:standard:filename", "MatchingDeclarationName")

package dev.abeta.kmp.core.security

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ptr
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.LocalAuthentication.LAContext
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthentication
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthenticationWithBiometrics

internal actual fun encodeUtf8NsData(value: String): NSData? =
    NSString
        .create(string = value)
        .dataUsingEncoding(NSUTF8StringEncoding)

internal actual fun decodeUtf8NsData(data: NSData): String? =
    NSString
        .create(data = data, encoding = NSUTF8StringEncoding)
        ?.toString()

internal actual fun createMutableCfDictionary(): CFMutableDictionaryRef? =
    CFDictionaryCreateMutable(
        kCFAllocatorDefault,
        0,
        kCFTypeDictionaryKeyCallBacks.ptr,
        kCFTypeDictionaryValueCallBacks.ptr,
    )

internal actual fun canEvaluateAppleBiometricPolicy(
    context: LAContext,
    allowDeviceCredential: Boolean,
): Boolean {
    val policy =
        if (allowDeviceCredential) {
            LAPolicyDeviceOwnerAuthentication
        } else {
            LAPolicyDeviceOwnerAuthenticationWithBiometrics
        }
    return context.canEvaluatePolicy(policy, error = null)
}

internal actual fun evaluateAppleBiometricPolicy(
    context: LAContext,
    allowDeviceCredential: Boolean,
    reason: String,
    onResult: (success: Boolean, errorCode: Long?, description: String?) -> Unit,
) {
    val policy =
        if (allowDeviceCredential) {
            LAPolicyDeviceOwnerAuthentication
        } else {
            LAPolicyDeviceOwnerAuthenticationWithBiometrics
        }
    context.evaluatePolicy(policy, localizedReason = reason) { success, nsError ->
        onResult(success, nsError?.code?.toLong(), nsError?.localizedDescription)
    }
}
