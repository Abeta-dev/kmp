package dev.abeta.kmp.layout.repository

import dev.abeta.kmp.contracts.ScreenManifestResponse
import dev.abeta.kmp.core.error.SerializationError
import dev.abeta.kmp.core.result.AppResult
import dev.abeta.kmp.network.client.ConditionalHttpResult
import dev.abeta.kmp.network.client.HttpTransport
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Repository port for retrieving the BFF SDUI screen version manifest (`/api/v1/screens/manifest`).
 */
fun interface ScreenManifestRepository {
    /** Fetches the latest [ScreenManifestResponse] mapping registered screens to versions and ETags. */
    suspend fun fetchManifest(): AppResult<ScreenManifestResponse>
}

/**
 * Remote [ScreenManifestRepository] fetching `ginmw.Envelope<ScreenManifestResponse>` via
 * conditional `If-None-Match` / `304 Not Modified` requests on [HttpTransport].
 */
class RemoteScreenManifestRepository(
    private val httpClient: HttpTransport,
    private val manifestPath: String = DEFAULT_MANIFEST_PATH,
    private val appVersion: String? = null,
) : ScreenManifestRepository {
    private val stateMutex = Mutex()
    private var lastEtag: String? = null
    private var lastManifest: ScreenManifestResponse? = null

    override suspend fun fetchManifest(): AppResult<ScreenManifestResponse> {
        val resolvedPath = manifestPath.trim().ifEmpty { DEFAULT_MANIFEST_PATH }
        val (validator, cached) = stateMutex.withLock { lastEtag to lastManifest }
        val result =
            httpClient.requestConditional(
                path = resolvedPath,
                deserializer = BffEnvelope.serializer(ScreenManifestResponse.serializer()),
                ifNoneMatch = if (cached != null) validator else null,
                headers = buildClientVersionHeaders(appVersion),
            )
        return when (result) {
            is AppResult.Failure -> result
            is AppResult.Success -> handleConditionalManifest(result.value, resolvedPath, validator, cached)
        }
    }

    private suspend fun handleConditionalManifest(
        response: ConditionalHttpResult<BffEnvelope<ScreenManifestResponse>>,
        resource: String,
        previousValidator: String?,
        cached: ScreenManifestResponse?,
    ): AppResult<ScreenManifestResponse> =
        when (response) {
            is ConditionalHttpResult.NotModified -> {
                if (cached != null) {
                    val refreshedEtag = response.etag?.takeIf { it.isNotBlank() } ?: previousValidator
                    stateMutex.withLock {
                        lastEtag = refreshedEtag
                    }
                    AppResult.Success(cached)
                } else {
                    AppResult.Failure(
                        SerializationError(
                            message = "Unexpected 304 Not Modified without cached manifest for '$resource'.",
                        ),
                    )
                }
            }

            is ConditionalHttpResult.Modified -> {
                val unwrapped = unwrapManifestEnvelope(response.envelope.body, resource)
                if (unwrapped is AppResult.Success) {
                    val nextEtag = response.envelope.etag?.takeIf { it.isNotBlank() }
                    stateMutex.withLock {
                        lastEtag = nextEtag
                        lastManifest = unwrapped.value
                    }
                }
                unwrapped
            }
        }

    private fun unwrapManifestEnvelope(
        envelope: BffEnvelope<ScreenManifestResponse>,
        resource: String,
    ): AppResult<ScreenManifestResponse> =
        when {
            envelope.success && envelope.data != null -> {
                AppResult.Success(envelope.data)
            }

            !envelope.success && envelope.error != null -> {
                AppResult.Failure(mapBffError(envelope.error, resource = resource))
            }

            else -> {
                AppResult.Failure(
                    SerializationError(
                        message = "Missing manifest data in BFF envelope for '$resource'.",
                    ),
                )
            }
        }

    companion object {
        const val DEFAULT_MANIFEST_PATH: String = "/api/v1/screens/manifest"
    }
}
