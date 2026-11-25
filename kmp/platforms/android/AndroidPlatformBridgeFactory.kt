package dev.abeta.kmp.platforms.android

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.OpenableColumns
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import dev.abeta.kmp.core.error.ForbiddenError
import dev.abeta.kmp.core.error.UnexpectedError
import dev.abeta.kmp.core.platform.AudioCapturePort
import dev.abeta.kmp.core.platform.ClipboardPort
import dev.abeta.kmp.core.platform.ConnectivityMonitor
import dev.abeta.kmp.core.platform.ConnectivityStatus
import dev.abeta.kmp.core.platform.ExternalLauncherPort
import dev.abeta.kmp.core.platform.FilePickerPort
import dev.abeta.kmp.core.platform.FilePickerRequest
import dev.abeta.kmp.core.platform.HapticEngine
import dev.abeta.kmp.core.platform.HapticStyle
import dev.abeta.kmp.core.platform.LocationPort
import dev.abeta.kmp.core.platform.OsPermission
import dev.abeta.kmp.core.platform.PermissionPort
import dev.abeta.kmp.core.platform.PermissionStatus
import dev.abeta.kmp.core.platform.PickedFile
import dev.abeta.kmp.core.platform.PlatformCapabilities
import dev.abeta.kmp.core.platform.PlatformInfo
import dev.abeta.kmp.core.platform.PlatformKind
import dev.abeta.kmp.core.platform.PushNotificationPort
import dev.abeta.kmp.core.platform.SharePort
import dev.abeta.kmp.core.platform.ShareRequest
import dev.abeta.kmp.core.platform.SpeechToTextPort
import dev.abeta.kmp.core.platform.UnavailableAudioCapturePort
import dev.abeta.kmp.core.platform.UnavailableLocationPort
import dev.abeta.kmp.core.platform.UnavailableSpeechToTextPort
import dev.abeta.kmp.core.platform.validateExternalUrl
import dev.abeta.kmp.core.result.AppResult
import dev.abeta.kmp.core.security.BiometricAuthenticator
import dev.abeta.kmp.core.security.BiometricAvailability
import dev.abeta.kmp.core.security.BiometricPromptRequest
import dev.abeta.kmp.core.security.BiometricResult
import dev.abeta.kmp.core.security.SecureKeyValueStore
import dev.abeta.kmp.core.storage.KeyValueStore
import dev.abeta.kmp.layout.platform.PlatformBridgeBundle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.security.KeyStore
import java.util.Locale
import java.util.TimeZone
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.coroutines.resume

/**
 * Drop-in Android SDK implementation building a full [PlatformBridgeBundle] for Phone, Tablet,
 * Foldable, ChromeOS, and Android TV targets inside a host Android Gradle app (`compileSdk >= 35`).
 */
object AndroidPlatformBridgeFactory {
    @Suppress("LongParameterList")
    fun create(
        activity: FragmentActivity,
        permissionRequester: suspend (androidPermission: String, rationale: String?) -> Boolean,
        filePickerLauncher: suspend (mimeTypes: List<String>, allowMultiple: Boolean) -> List<Uri> = { _, _ ->
            emptyList()
        },
        pushTokenProvider: suspend () -> String? = { null },
        locationPort: LocationPort = UnavailableLocationPort,
        audioCapturePort: AudioCapturePort = UnavailableAudioCapturePort,
        speechToTextPort: SpeechToTextPort = UnavailableSpeechToTextPort,
    ): PlatformBridgeBundle {
        val appContext = activity.applicationContext
        val capabilities = resolveCapabilities(appContext)
        return PlatformBridgeBundle(
            capabilities = capabilities,
            hapticEngine = AndroidHapticEngine(appContext),
            clipboardPort = AndroidClipboardPort(appContext),
            externalLauncher = AndroidExternalLauncher(appContext),
            sharePort = AndroidSharePort(activity),
            connectivityMonitor = AndroidConnectivityMonitor(appContext),
            secureStore = AndroidKeyStoreSecureStore(appContext),
            biometricAuthenticator = AndroidBiometricAuthenticator(activity),
            permissionPort = AndroidPermissionPort(activity, permissionRequester),
            locationPort = locationPort,
            filePickerPort = AndroidFilePickerPort(appContext, filePickerLauncher),
            pushNotificationPort =
                object : PushNotificationPort {
                    override suspend fun currentToken(): AppResult<String?> =
                        AppResult.Success(runCatching { pushTokenProvider() }.getOrNull())

                    override suspend fun registerForPush(): AppResult<String> =
                        runCatching { pushTokenProvider() }.fold(
                            onSuccess = { token ->
                                if (!token.isNullOrBlank()) {
                                    AppResult.Success(token)
                                } else {
                                    AppResult.Failure(ForbiddenError("push", "Push token unavailable."))
                                }
                            },
                            onFailure = {
                                AppResult.Failure(
                                    UnexpectedError(
                                        message = it.message ?: "Push failed",
                                        cause = it,
                                    ),
                                )
                            },
                        )
                },
            audioCapturePort = audioCapturePort,
            speechToTextPort = speechToTextPort,
        )
    }

    private fun resolveCapabilities(context: Context): PlatformCapabilities {
        val animatorScale =
            runCatching {
                android.provider.Settings.Global.getFloat(
                    context.contentResolver,
                    android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
                    1f,
                )
            }.getOrDefault(1f)
        val nightModeMask = context.resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK
        val bioStatus =
            runCatching {
                BiometricManager.from(context).canAuthenticate(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL,
                )
            }.getOrDefault(BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE)
        return PlatformCapabilities(
            platform = PlatformInfo(PlatformKind.ANDROID, "Android ${Build.VERSION.RELEASE}"),
            osVersion = Build.VERSION.RELEASE ?: Build.VERSION.SDK_INT.toString(),
            localeTag = Locale.getDefault().toLanguageTag(),
            timezoneId = TimeZone.getDefault().id,
            isReducedMotionPreferred = animatorScale == 0f,
            isDarkModePreferred = nightModeMask == android.content.res.Configuration.UI_MODE_NIGHT_YES,
            supportsHaptics = true,
            supportsBiometrics = bioStatus == BiometricManager.BIOMETRIC_SUCCESS,
            supportsClipboard = true,
            supportsExternalUrl = true,
            supportsShareSheet = true,
        )
    }
}

/**
 * Android `SharedPreferences`-backed [KeyValueStore] avoiding JVM `java.util.prefs.Preferences.userRoot()`
 * crashes on Android phones, tablets, and Wear OS.
 */
class AndroidSharedPreferencesKeyValueStore(
    context: Context,
    preferencesName: String = "dev.abeta.kmp.settings",
) : KeyValueStore {
    private val prefs = context.applicationContext.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)

    override fun getString(key: String): String? = prefs.getString(key, null)

    override fun putString(
        key: String,
        value: String,
    ) {
        prefs.edit().putString(key, value).apply()
    }

    override fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }

    override fun contains(key: String): Boolean = prefs.contains(key)

    override fun keys(): Set<String> = prefs.all.keys.toSet()
}

private class AndroidHapticEngine(
    private val context: Context,
) : HapticEngine {
    override fun perform(style: HapticStyle): AppResult<Unit> =
        runCatching {
            val vibrator: Vibrator? =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.getSystemService(VibratorManager::class.java)?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                }
            if (vibrator?.hasVibrator() == true && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val effectId =
                    when (style) {
                        HapticStyle.TICK -> VibrationEffect.EFFECT_TICK
                        HapticStyle.CLICK, HapticStyle.SUCCESS -> VibrationEffect.EFFECT_CLICK
                        HapticStyle.WARNING, HapticStyle.ERROR -> VibrationEffect.EFFECT_DOUBLE_CLICK
                        HapticStyle.HEAVY -> VibrationEffect.EFFECT_HEAVY_CLICK
                    }
                vibrator.vibrate(VibrationEffect.createPredefined(effectId))
            }
        }.fold(
            onSuccess = { AppResult.Success(Unit) },
            onFailure = { AppResult.Failure(UnexpectedError(message = "Haptic feedback failed", cause = it)) },
        )
}

private class AndroidClipboardPort(
    private val context: Context,
) : ClipboardPort {
    override fun copy(
        text: String,
        label: String?,
    ): AppResult<Unit> =
        runCatching {
            val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            manager.setPrimaryClip(ClipData.newPlainText(label ?: "abeta", text))
        }.fold(
            onSuccess = { AppResult.Success(Unit) },
            onFailure = { AppResult.Failure(UnexpectedError(message = "Clipboard copy failed", cause = it)) },
        )

    override fun readText(): AppResult<String?> =
        runCatching {
            val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            manager.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
        }.fold(
            onSuccess = { AppResult.Success(it) },
            onFailure = { AppResult.Failure(UnexpectedError(message = "Clipboard read failed", cause = it)) },
        )
}

/**
 * Android [ExternalLauncherPort] opening validated external URLs via `Intent.ACTION_VIEW`.
 */
class AndroidExternalLauncher(
    private val context: Context,
) : ExternalLauncherPort {
    override fun canOpenUrl(url: String): Boolean = validateExternalUrl(url) is AppResult.Success

    override suspend fun openUrl(url: String): AppResult<Unit> =
        when (val validated = validateExternalUrl(url)) {
            is AppResult.Failure -> validated
            is AppResult.Success ->
                runCatching {
                    val intent =
                        Intent(Intent.ACTION_VIEW, Uri.parse(validated.value)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    context.startActivity(intent)
                }.fold(
                    onSuccess = { AppResult.Success(Unit) },
                    onFailure = { AppResult.Failure(UnexpectedError(message = "Unable to open URL", cause = it)) },
                )
        }
}

/**
 * Android [SharePort] presenting the system share chooser for Phone, Tablet, and Wear OS hosts.
 */
class AndroidSharePort(
    private val context: Context,
) : SharePort {
    override suspend fun share(request: ShareRequest): AppResult<Unit> =
        runCatching {
            val sendIntent =
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, request.formatCombinedText())
                    request.title?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
                }
            val chooser =
                Intent.createChooser(sendIntent, request.chooserTitle ?: request.title).apply {
                    if (context !is Activity) {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                }
            context.startActivity(chooser)
        }.fold(
            onSuccess = { AppResult.Success(Unit) },
            onFailure = {
                AppResult.Failure(UnexpectedError(message = "Unable to present share sheet", cause = it))
            },
        )
}

private class AndroidFilePickerPort(
    private val context: Context,
    private val launcher: suspend (mimeTypes: List<String>, allowMultiple: Boolean) -> List<Uri>,
) : FilePickerPort {
    override suspend fun pickFiles(request: FilePickerRequest): AppResult<List<PickedFile>> =
        try {
            val uris = launcher(request.mimeTypes, request.allowMultiple)
            val files = uris.map { uri -> resolvePickedFile(uri) }
            AppResult.Success(files)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            AppResult.Failure(UnexpectedError(message = "File picker failed", cause = throwable))
        }

    private fun resolvePickedFile(uri: Uri): PickedFile {
        var displayName: String? = null
        var sizeBytes: Long? = null
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0 && !cursor.isNull(nameIndex)) {
                        displayName = cursor.getString(nameIndex)
                    }
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                        sizeBytes = cursor.getLong(sizeIndex)
                    }
                }
            }
        }
        val mimeType = runCatching { context.contentResolver.getType(uri) }.getOrNull()
        return PickedFile(
            name = displayName?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment ?: "file",
            mimeType = mimeType,
            sizeBytes = sizeBytes,
            uriString = uri.toString(),
        )
    }
}

/**
 * Android [ConnectivityMonitor] observing active network capabilities via `ConnectivityManager`.
 */
class AndroidConnectivityMonitor(
    context: Context,
) : ConnectivityMonitor {
    private val manager = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private val _status = MutableStateFlow(readCurrentStatus())
    override val status: StateFlow<ConnectivityStatus> = _status.asStateFlow()

    private val callback =
        object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                _status.value = readCurrentStatus()
            }

            override fun onLost(network: Network) {
                _status.value = readCurrentStatus()
            }

            override fun onCapabilitiesChanged(
                network: Network,
                capabilities: NetworkCapabilities,
            ) {
                _status.value = mapCapabilities(capabilities)
            }
        }

    override fun start() {
        runCatching { manager?.registerDefaultNetworkCallback(callback) }
        _status.value = readCurrentStatus()
    }

    override fun stop() {
        runCatching { manager?.unregisterNetworkCallback(callback) }
    }

    override fun refresh() {
        _status.value = readCurrentStatus()
    }

    private fun readCurrentStatus(): ConnectivityStatus =
        runCatching {
            val active = manager?.activeNetwork ?: return@runCatching ConnectivityStatus.OFFLINE
            val caps = manager.getNetworkCapabilities(active) ?: return@runCatching ConnectivityStatus.OFFLINE
            mapCapabilities(caps)
        }.getOrDefault(ConnectivityStatus.OFFLINE)

    private fun mapCapabilities(caps: NetworkCapabilities): ConnectivityStatus =
        when {
            !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) -> ConnectivityStatus.OFFLINE
            !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) -> ConnectivityStatus.METERED
            else -> ConnectivityStatus.ONLINE
        }
}

private class AndroidPermissionPort(
    private val context: Context,
    private val requester: suspend (String, String?) -> Boolean,
) : PermissionPort {
    override suspend fun checkStatus(permission: OsPermission): PermissionStatus {
        if (permission == OsPermission.NOTIFICATIONS && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return PermissionStatus.GRANTED
        }
        val manifestPerm = toAndroidManifestPermission(permission) ?: return PermissionStatus.UNSUPPORTED
        val granted =
            ContextCompat.checkSelfPermission(context, manifestPerm) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        return if (granted) PermissionStatus.GRANTED else PermissionStatus.DENIED
    }

    override suspend fun request(
        permission: OsPermission,
        rationale: String?,
    ): PermissionStatus {
        if (permission == OsPermission.NOTIFICATIONS && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return PermissionStatus.GRANTED
        }
        val manifestPerm = toAndroidManifestPermission(permission) ?: return PermissionStatus.UNSUPPORTED
        if (checkStatus(permission) == PermissionStatus.GRANTED) return PermissionStatus.GRANTED
        return if (requester(manifestPerm, rationale)) PermissionStatus.GRANTED else PermissionStatus.DENIED
    }

    private fun toAndroidManifestPermission(permission: OsPermission): String? =
        when (permission) {
            OsPermission.LOCATION -> android.Manifest.permission.ACCESS_FINE_LOCATION
            OsPermission.CAMERA -> android.Manifest.permission.CAMERA
            OsPermission.NOTIFICATIONS ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    android.Manifest.permission.POST_NOTIFICATIONS
                } else {
                    null
                }
            OsPermission.STORAGE ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    android.Manifest.permission.READ_MEDIA_IMAGES
                } else {
                    android.Manifest.permission.READ_EXTERNAL_STORAGE
                }
        }
}

private class AndroidBiometricAuthenticator(
    private val activity: FragmentActivity,
) : BiometricAuthenticator {
    override fun availability(): BiometricAvailability {
        val status =
            runCatching {
                BiometricManager.from(activity).canAuthenticate(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL,
                )
            }.getOrDefault(BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE)
        return when (status) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricAvailability.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricAvailability.NOT_ENROLLED
            else -> BiometricAvailability.UNAVAILABLE
        }
    }

    override suspend fun authenticate(request: BiometricPromptRequest): BiometricResult =
        withContext(Dispatchers.Main.immediate) {
            suspendCancellableCoroutine { continuation ->
                val executor = ContextCompat.getMainExecutor(activity)
                val prompt =
                    BiometricPrompt(
                        activity,
                        executor,
                        object : BiometricPrompt.AuthenticationCallback() {
                            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                if (continuation.isActive) continuation.resume(BiometricResult.Authenticated)
                            }

                            override fun onAuthenticationError(
                                errorCode: Int,
                                errString: CharSequence,
                            ) {
                                if (!continuation.isActive) return
                                val cancelled =
                                    errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                                        errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                                        errorCode == BiometricPrompt.ERROR_CANCELED
                                continuation.resume(
                                    if (cancelled) {
                                        BiometricResult.Cancelled
                                    } else {
                                        BiometricResult.Failed(errString.toString())
                                    },
                                )
                            }

                            override fun onAuthenticationFailed() = Unit
                        },
                    )
                val builder =
                    BiometricPrompt.PromptInfo
                        .Builder()
                        .setTitle(request.title ?: "Confirm Identity")
                        .setSubtitle(request.reason)
                        .setAllowedAuthenticators(
                            if (request.allowDeviceCredential) {
                                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
                            } else {
                                BiometricManager.Authenticators.BIOMETRIC_STRONG
                            },
                        )
                if (!request.allowDeviceCredential) {
                    builder.setNegativeButtonText("Cancel")
                }
                val info = builder.build()
                continuation.invokeOnCancellation {
                    runCatching { prompt.cancelAuthentication() }
                }
                prompt.authenticate(info)
            }
        }
}

/**
 * Hardware-backed Android KeyStore AES-256-GCM [SecureKeyValueStore] recovering cleanly from
 * KeyStore key invalidation without crashing the host application.
 */
class AndroidKeyStoreSecureStore(
    context: Context,
    prefsName: String = "dev.abeta.kmp.secure_vault",
    private val alias: String = "abeta_kmp_aes_gcm_master",
) : SecureKeyValueStore {
    private val prefs = context.applicationContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    override val storageBackend: String = "AndroidKeyStoreAesGcm"
    override val isHardwareOrOsEncrypted: Boolean = true

    override fun getString(key: String): String? {
        val blob = prefs.getString(key, null) ?: return null
        return decrypt(blob)
    }

    override fun putString(
        key: String,
        value: String,
    ) {
        val sealed =
            runCatching { encrypt(value) }
                .recoverCatching {
                    resetInvalidatedKey()
                    encrypt(value)
                }.getOrNull() ?: return
        prefs.edit().putString(key, sealed).apply()
    }

    override fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }

    override fun contains(key: String): Boolean = prefs.contains(key)

    override fun keys(): Set<String> = prefs.all.keys.toSet()

    private fun resetInvalidatedKey() {
        runCatching {
            val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (ks.containsAlias(alias)) {
                ks.deleteEntry(alias)
            }
        }
        prefs.edit().clear().apply()
    }

    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec
                .Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, secretKey()) }
        val sealed = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val ivB64 = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val ctB64 = Base64.encodeToString(sealed, Base64.NO_WRAP)
        return "$ivB64:$ctB64"
    }

    private fun decrypt(payload: String): String? =
        runCatching {
            val parts = payload.split(':', limit = 2)
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val ct = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
            cipher.doFinal(ct).toString(Charsets.UTF_8)
        }.getOrNull()
}
