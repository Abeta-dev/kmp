package dev.abeta.kmp.layout.platform

import dev.abeta.kmp.atoms.modifiers.UiHapticKind
import dev.abeta.kmp.contracts.FeedbackEffect
import dev.abeta.kmp.contracts.OsPermissionKind
import dev.abeta.kmp.core.platform.AudioCapturePort
import dev.abeta.kmp.core.platform.ClipboardPort
import dev.abeta.kmp.core.platform.ConnectivityMonitor
import dev.abeta.kmp.core.platform.ConnectivityStatus
import dev.abeta.kmp.core.platform.DesktopSystemPort
import dev.abeta.kmp.core.platform.ExternalLauncherPort
import dev.abeta.kmp.core.platform.FakeAudioCapturePort
import dev.abeta.kmp.core.platform.FakeSpeechToTextPort
import dev.abeta.kmp.core.platform.FilePickerPort
import dev.abeta.kmp.core.platform.HapticEngine
import dev.abeta.kmp.core.platform.HapticStyle
import dev.abeta.kmp.core.platform.LocationPort
import dev.abeta.kmp.core.platform.MutableConnectivityMonitor
import dev.abeta.kmp.core.platform.NoOpHapticEngine
import dev.abeta.kmp.core.platform.OsPermission
import dev.abeta.kmp.core.platform.PermissionPort
import dev.abeta.kmp.core.platform.PlatformCapabilities
import dev.abeta.kmp.core.platform.PushNotificationPort
import dev.abeta.kmp.core.platform.RecordingDesktopSystemPort
import dev.abeta.kmp.core.platform.RecordingExternalLauncherPort
import dev.abeta.kmp.core.platform.RecordingSharePort
import dev.abeta.kmp.core.platform.SharePort
import dev.abeta.kmp.core.platform.SpeechToTextPort
import dev.abeta.kmp.core.platform.UnavailableAudioCapturePort
import dev.abeta.kmp.core.platform.UnavailableDesktopSystemPort
import dev.abeta.kmp.core.platform.UnavailableFilePickerPort
import dev.abeta.kmp.core.platform.UnavailableLocationPort
import dev.abeta.kmp.core.platform.UnavailablePushNotificationPort
import dev.abeta.kmp.core.platform.UnavailableSpeechToTextPort
import dev.abeta.kmp.core.platform.UnsupportedPermissionPort
import dev.abeta.kmp.core.platform.createPlatformClipboardPort
import dev.abeta.kmp.core.platform.createPlatformConnectivityMonitor
import dev.abeta.kmp.core.platform.createPlatformDesktopSystemPort
import dev.abeta.kmp.core.platform.createPlatformExternalLauncher
import dev.abeta.kmp.core.platform.createPlatformHapticEngine
import dev.abeta.kmp.core.platform.createPlatformSharePort
import dev.abeta.kmp.core.platform.currentPlatformCapabilities
import dev.abeta.kmp.core.security.BiometricAuthenticator
import dev.abeta.kmp.core.security.BiometricAvailability
import dev.abeta.kmp.core.security.BiometricResult
import dev.abeta.kmp.core.security.FakeBiometricAuthenticator
import dev.abeta.kmp.core.security.InMemorySecureKeyValueStore
import dev.abeta.kmp.core.security.SecureKeyValueStore
import dev.abeta.kmp.core.security.createPlatformBiometricAuthenticator
import dev.abeta.kmp.core.security.createSecureKeyValueStore

/**
 * Aggregated OS bridge dependency container injected into [dev.abeta.kmp.layout.engine.LayoutController]
 * and Koin graphs, unifying haptics, clipboard, external launcher, share sheet, connectivity,
 * secure key-value storage, biometrics, permissions, location, file picker, push tokens,
 * audio capture, speech-to-text, and desktop system integrations.
 */
data class PlatformBridgeBundle(
    val capabilities: PlatformCapabilities,
    val hapticEngine: HapticEngine,
    val clipboardPort: ClipboardPort,
    val externalLauncher: ExternalLauncherPort,
    val sharePort: SharePort,
    val connectivityMonitor: ConnectivityMonitor,
    val secureStore: SecureKeyValueStore,
    val biometricAuthenticator: BiometricAuthenticator,
    val permissionPort: PermissionPort = UnsupportedPermissionPort,
    val locationPort: LocationPort = UnavailableLocationPort,
    val filePickerPort: FilePickerPort = UnavailableFilePickerPort,
    val pushNotificationPort: PushNotificationPort = UnavailablePushNotificationPort,
    val audioCapturePort: AudioCapturePort = UnavailableAudioCapturePort,
    val speechToTextPort: SpeechToTextPort = UnavailableSpeechToTextPort,
    val desktopSystemPort: DesktopSystemPort = UnavailableDesktopSystemPort,
) {
    companion object {
        /** Creates a live [PlatformBridgeBundle] backed by host OS implementations. */
        @Suppress("LongParameterList")
        fun createPlatformDefault(
            secureServiceName: String = "dev.abeta.kmp.secure",
            permissionPort: PermissionPort = UnsupportedPermissionPort,
            locationPort: LocationPort = UnavailableLocationPort,
            filePickerPort: FilePickerPort = UnavailableFilePickerPort,
            pushNotificationPort: PushNotificationPort = UnavailablePushNotificationPort,
            audioCapturePort: AudioCapturePort = UnavailableAudioCapturePort,
            speechToTextPort: SpeechToTextPort = UnavailableSpeechToTextPort,
            desktopSystemPort: DesktopSystemPort = createPlatformDesktopSystemPort(),
        ): PlatformBridgeBundle =
            PlatformBridgeBundle(
                capabilities = currentPlatformCapabilities(),
                hapticEngine = createPlatformHapticEngine(),
                clipboardPort = createPlatformClipboardPort(),
                externalLauncher = createPlatformExternalLauncher(),
                sharePort = createPlatformSharePort(),
                connectivityMonitor = createPlatformConnectivityMonitor(),
                secureStore = createSecureKeyValueStore(secureServiceName),
                biometricAuthenticator = createPlatformBiometricAuthenticator(),
                permissionPort = permissionPort,
                locationPort = locationPort,
                filePickerPort = filePickerPort,
                pushNotificationPort = pushNotificationPort,
                audioCapturePort = audioCapturePort,
                speechToTextPort = speechToTextPort,
                desktopSystemPort = desktopSystemPort,
            )

        /** Creates a deterministic headless/test [PlatformBridgeBundle] without OS UI side effects. */
        fun createTestDefault(
            isOnline: Boolean = true,
            biometricResult: BiometricResult = BiometricResult.Unavailable("Headless host"),
            audioCapturePort: AudioCapturePort = FakeAudioCapturePort(),
            speechToTextPort: SpeechToTextPort = FakeSpeechToTextPort(),
            desktopSystemPort: DesktopSystemPort = RecordingDesktopSystemPort(),
        ): PlatformBridgeBundle =
            PlatformBridgeBundle(
                capabilities = currentPlatformCapabilities(),
                hapticEngine = NoOpHapticEngine,
                clipboardPort =
                    dev.abeta.kmp.core.platform
                        .InMemoryClipboardPort(),
                externalLauncher = RecordingExternalLauncherPort(),
                sharePort = RecordingSharePort(),
                connectivityMonitor =
                    MutableConnectivityMonitor(
                        if (isOnline) ConnectivityStatus.ONLINE else ConnectivityStatus.OFFLINE,
                    ),
                secureStore = InMemorySecureKeyValueStore(),
                biometricAuthenticator =
                    FakeBiometricAuthenticator(
                        availabilityState = BiometricAvailability.UNAVAILABLE,
                        nextResult = biometricResult,
                    ),
                audioCapturePort = audioCapturePort,
                speechToTextPort = speechToTextPort,
                desktopSystemPort = desktopSystemPort,
            )
    }
}

internal fun OsPermissionKind.toCorePermission(): OsPermission =
    when (this) {
        OsPermissionKind.LOCATION -> OsPermission.LOCATION
        OsPermissionKind.NOTIFICATIONS -> OsPermission.NOTIFICATIONS
        OsPermissionKind.CAMERA -> OsPermission.CAMERA
        OsPermissionKind.STORAGE -> OsPermission.STORAGE
    }

internal fun FeedbackEffect.toHapticStyle(): HapticStyle =
    when (this) {
        FeedbackEffect.CELEBRATION -> HapticStyle.SUCCESS
        FeedbackEffect.CART_BOUNCE -> HapticStyle.CLICK
        FeedbackEffect.SHAKE_ERROR -> HapticStyle.ERROR
        FeedbackEffect.HAPTIC_TICK -> HapticStyle.TICK
    }

/**
 * Adapts a Layer 0 [HapticEngine] to Layer 1's pure [UiHapticKind] callback for [dev.abeta.kmp.layout.ui.SduiScreen].
 */
fun HapticEngine.asUiHapticCallback(): (UiHapticKind) -> Unit =
    { kind ->
        val style =
            when (kind) {
                UiHapticKind.TICK -> HapticStyle.TICK
                UiHapticKind.CLICK -> HapticStyle.CLICK
                UiHapticKind.SUCCESS -> HapticStyle.SUCCESS
                UiHapticKind.WARNING -> HapticStyle.WARNING
                UiHapticKind.ERROR -> HapticStyle.ERROR
            }
        perform(style)
    }
