@file:Suppress("ktlint:standard:filename", "MatchingDeclarationName")

package dev.abeta.kmp.core.platform

import dev.abeta.kmp.core.error.ForbiddenError
import dev.abeta.kmp.core.result.AppResult

/**
 * Standard runtime permission categories requested by SDUI actions or host flows.
 */
enum class OsPermission {
    LOCATION,
    NOTIFICATIONS,
    CAMERA,
    STORAGE,
}

/**
 * Outcome status returned when checking or requesting an [OsPermission].
 */
enum class PermissionStatus {
    GRANTED,
    DENIED,
    PERMANENTLY_DENIED,
    UNSUPPORTED,
}

/**
 * Dependency-inverted port for querying and requesting host runtime permissions.
 */
interface PermissionPort {
    /** Returns the current [PermissionStatus] without showing a prompt. */
    suspend fun checkStatus(permission: OsPermission): PermissionStatus

    /** Requests [permission] from the OS (with optional [rationale]) and returns the resulting status. */
    suspend fun request(
        permission: OsPermission,
        rationale: String? = null,
    ): PermissionStatus
}

/**
 * Fallback [PermissionPort] for headless environments or desktop platforms without runtime permission prompts.
 */
object UnsupportedPermissionPort : PermissionPort {
    override suspend fun checkStatus(permission: OsPermission): PermissionStatus = PermissionStatus.UNSUPPORTED

    override suspend fun request(
        permission: OsPermission,
        rationale: String?,
    ): PermissionStatus = PermissionStatus.UNSUPPORTED
}

/**
 * Immutable geographic coordinate snapshot supplied by a host [LocationPort].
 */
data class DeviceCoordinates(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float? = null,
    val capturedAtEpochMillis: Long? = null,
)

/**
 * Port abstracting current device location lookups (GPS, fused location, CoreLocation, browser geolocation).
 */
interface LocationPort {
    /** Fetches the current or most recent [DeviceCoordinates], or returns [AppResult.Failure]. */
    suspend fun currentLocation(): AppResult<DeviceCoordinates>
}

/** Default stub [LocationPort] when no host location provider is bound. */
object UnavailableLocationPort : LocationPort {
    override suspend fun currentLocation(): AppResult<DeviceCoordinates> =
        AppResult.Failure(ForbiddenError(resource = "location", message = "Location provider is not bound on host."))
}

/** Creates the platform-native [LocationPort] for the current KMP target. */
expect fun createPlatformLocationPort(): LocationPort

/**
 * Criteria describing a native OS file or media picker request.
 */
data class FilePickerRequest(
    val mimeTypes: List<String> = listOf("*/*"),
    val allowMultiple: Boolean = false,
)

/**
 * Selected file metadata (and optional small payload bytes) returned by [FilePickerPort].
 */
data class PickedFile(
    val name: String,
    val mimeType: String? = null,
    val sizeBytes: Long? = null,
    val bytes: ByteArray? = null,
    val uriString: String? = null,
)

/**
 * Port abstracting native document/image selection dialogs.
 */
interface FilePickerPort {
    /** Opens the host file picker matching [request] and returns the selected files. */
    suspend fun pickFiles(request: FilePickerRequest = FilePickerRequest()): AppResult<List<PickedFile>>
}

/** Default stub [FilePickerPort] when no UI host picker is bound. */
object UnavailableFilePickerPort : FilePickerPort {
    override suspend fun pickFiles(request: FilePickerRequest): AppResult<List<PickedFile>> =
        AppResult.Failure(ForbiddenError(resource = "file_picker", message = "File picker is not bound on host."))
}

/** Creates the platform-native [FilePickerPort] for the current KMP target. */
expect fun createPlatformFilePickerPort(): FilePickerPort

/**
 * Port abstracting push notification token retrieval (APNs, FCM, Web Push).
 */
interface PushNotificationPort {
    /** Returns the active push registration token if available. */
    suspend fun currentToken(): AppResult<String?>

    /** Registers or refreshes the device push token. */
    suspend fun registerForPush(): AppResult<String>
}

/** Default stub [PushNotificationPort] when push services are not configured on the host. */
object UnavailablePushNotificationPort : PushNotificationPort {
    override suspend fun currentToken(): AppResult<String?> = AppResult.Success(null)

    override suspend fun registerForPush(): AppResult<String> =
        AppResult.Failure(
            ForbiddenError(resource = "push", message = "Push notifications are not configured on this host."),
        )
}
