@file:OptIn(ExperimentalWasmJsInterop::class)
@file:Suppress("ktlint:standard:filename", "MatchingDeclarationName")

package dev.abeta.kmp.core.platform

import dev.abeta.kmp.core.error.ForbiddenError
import dev.abeta.kmp.core.error.TimeoutError
import dev.abeta.kmp.core.error.UnexpectedError
import dev.abeta.kmp.core.result.AppResult
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.io.encoding.Base64

private const val DEFAULT_GEOLOCATION_TIMEOUT_MS = 10_000L
private const val DEFAULT_GEOLOCATION_MAX_AGE_MS = 60_000
private const val GEO_ERROR_UNAVAILABLE = 0
private const val GEO_ERROR_PERMISSION_DENIED = 1
private const val GEO_ERROR_TIMEOUT = 3
private const val WASM_MAX_INLINE_FILE_BYTES = 4 * 1024 * 1024

@JsFun(
    "(timeoutMs, maxAgeMs, onSuccess, onError) => { try { " +
        "if (typeof navigator === 'undefined' || !navigator.geolocation || " +
        "typeof navigator.geolocation.getCurrentPosition !== 'function') { " +
        "onError(0, 'Browser Geolocation API is unavailable in this runtime.'); return; } " +
        "navigator.geolocation.getCurrentPosition(" +
        "(pos) => { const c = pos.coords; " +
        "onSuccess(c.latitude, c.longitude, c.accuracy || 0, pos.timestamp || Date.now()); }, " +
        "(err) => { const code = (err && err.code) ? err.code : 2; " +
        "const msg = (err && err.message) ? err.message : 'Geolocation lookup failed.'; " +
        "onError(code, msg); }, " +
        "{ enableHighAccuracy: false, timeout: timeoutMs, maximumAge: maxAgeMs }); " +
        "} catch (e) { onError(2, String(e)); } }",
)
private external fun jsGetCurrentPosition(
    timeoutMs: Int,
    maxAgeMs: Int,
    onSuccess: (Double, Double, Double, Double) -> Unit,
    onError: (Int, String) -> Unit,
)

@JsFun(
    "(accept, allowMultiple, maxBytes, onFile, onComplete, onError) => { try { " +
        "if (typeof document === 'undefined' || !document.createElement || !document.body) { " +
        "onError('Browser DOM is unavailable for file picking.'); return null; } " +
        "const input = document.createElement('input'); " +
        "input.type = 'file'; input.style.display = 'none'; " +
        "if (accept && accept.length > 0) { input.accept = accept; } " +
        "input.multiple = !!allowMultiple; " +
        "let settled = false; " +
        "const cleanup = () => { try { if (input.parentNode) { " +
        "input.parentNode.removeChild(input); } } catch (e) {} }; " +
        "const finish = () => { if (!settled) { settled = true; cleanup(); onComplete(); } }; " +
        "input.addEventListener('cancel', () => { finish(); }); " +
        "input.addEventListener('change', () => { " +
        "const files = input.files ? Array.from(input.files) : []; " +
        "if (files.length === 0) { finish(); return; } " +
        "let pending = files.length; " +
        "const stepDone = () => { pending -= 1; if (pending <= 0) { finish(); } }; " +
        "files.forEach((file) => { " +
        "const name = file.name || 'file'; const mime = file.type || ''; const size = file.size || 0; " +
        "if (file.size <= maxBytes && typeof FileReader !== 'undefined') { " +
        "const reader = new FileReader(); " +
        "reader.onload = () => { " +
        "const res = typeof reader.result === 'string' ? reader.result : ''; " +
        "const comma = res.indexOf(','); " +
        "const b64 = comma >= 0 ? res.slice(comma + 1) : ''; " +
        "onFile(name, mime, size, b64); stepDone(); }; " +
        "reader.onerror = () => { onFile(name, mime, size, ''); stepDone(); }; " +
        "reader.readAsDataURL(file); " +
        "} else { onFile(name, mime, size, ''); stepDone(); } }); }); " +
        "document.body.appendChild(input); input.click(); " +
        "return () => { settled = true; cleanup(); }; " +
        "} catch (e) { onError(String(e)); return null; } }",
)
private external fun jsPickBrowserFiles(
    accept: String,
    allowMultiple: Boolean,
    maxBytes: Int,
    onFile: (String, String, Double, String) -> Unit,
    onComplete: () -> Unit,
    onError: (String) -> Unit,
): JsAny?

@JsFun("(cleanup) => { try { if (cleanup) { cleanup(); } } catch (e) {} }")
private external fun jsInvokeCleanup(cleanup: JsAny)

private class WasmLocationPort : LocationPort {
    override suspend fun currentLocation(): AppResult<DeviceCoordinates> =
        suspendCancellableCoroutine { continuation ->
            jsGetCurrentPosition(
                timeoutMs = DEFAULT_GEOLOCATION_TIMEOUT_MS.toInt(),
                maxAgeMs = DEFAULT_GEOLOCATION_MAX_AGE_MS,
                onSuccess = { lat, lng, accuracy, timestamp ->
                    if (continuation.isActive) {
                        val accuracyMeters = if (accuracy > 0.0) accuracy.toFloat() else null
                        continuation.resume(
                            AppResult.Success(
                                DeviceCoordinates(
                                    latitude = lat,
                                    longitude = lng,
                                    accuracyMeters = accuracyMeters,
                                    capturedAtEpochMillis = timestamp.toLong(),
                                ),
                            ),
                        )
                    }
                },
                onError = { code, message ->
                    if (continuation.isActive) {
                        val error =
                            when (code) {
                                GEO_ERROR_UNAVAILABLE, GEO_ERROR_PERMISSION_DENIED -> {
                                    ForbiddenError(resource = "location", message = message)
                                }

                                GEO_ERROR_TIMEOUT -> {
                                    TimeoutError(
                                        timeoutMillis = DEFAULT_GEOLOCATION_TIMEOUT_MS,
                                        message = message,
                                    )
                                }

                                else -> {
                                    UnexpectedError(message = message)
                                }
                            }
                        continuation.resume(AppResult.Failure(error))
                    }
                },
            )
        }
}

private class WasmFilePickerPort : FilePickerPort {
    override suspend fun pickFiles(request: FilePickerRequest): AppResult<List<PickedFile>> {
        val accept =
            request.mimeTypes
                .map { it.trim() }
                .filter { it.isNotEmpty() && it != "*/*" }
                .joinToString(separator = ",")
        return suspendCancellableCoroutine { continuation ->
            val collected = mutableListOf<PickedFile>()
            val cleanupHandle =
                jsPickBrowserFiles(
                    accept = accept,
                    allowMultiple = request.allowMultiple,
                    maxBytes = WASM_MAX_INLINE_FILE_BYTES,
                    onFile = { name, mimeType, sizeDouble, base64 ->
                        val bytes =
                            if (base64.isNotEmpty()) {
                                runCatching { Base64.decode(base64) }.getOrNull()
                            } else {
                                null
                            }
                        collected.add(
                            PickedFile(
                                name = name,
                                mimeType = mimeType.takeIf { it.isNotBlank() },
                                sizeBytes = sizeDouble.toLong().takeIf { it >= 0L },
                                bytes = bytes,
                                uriString = null,
                            ),
                        )
                    },
                    onComplete = {
                        if (continuation.isActive) {
                            continuation.resume(AppResult.Success(collected.toList()))
                        }
                    },
                    onError = { message ->
                        if (continuation.isActive) {
                            continuation.resume(
                                AppResult.Failure(
                                    ForbiddenError(resource = "file_picker", message = message),
                                ),
                            )
                        }
                    },
                )
            continuation.invokeOnCancellation {
                cleanupHandle?.let(::jsInvokeCleanup)
            }
        }
    }
}

/** Creates a WasmJS browser `navigator.geolocation` [LocationPort]. */
actual fun createPlatformLocationPort(): LocationPort = WasmLocationPort()

/** Creates a WasmJS browser `<input type="file">` [FilePickerPort]. */
actual fun createPlatformFilePickerPort(): FilePickerPort = WasmFilePickerPort()

/** Creates the WasmJS [AudioCapturePort] (defaults to [UnavailableAudioCapturePort]). */
actual fun createPlatformAudioCapturePort(): AudioCapturePort = UnavailableAudioCapturePort
