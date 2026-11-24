package dev.abeta.kmp.layout.repository

import dev.abeta.kmp.contracts.ScreenManifestResponse
import dev.abeta.kmp.core.error.SerializationError
import dev.abeta.kmp.core.result.AppResult
import dev.abeta.kmp.network.client.HttpMethodType
import dev.abeta.kmp.network.client.HttpTransport

/**
 * Repository port for retrieving the BFF SDUI screen version manifest (`/api/v1/screens/manifest`).
 */
fun interface ScreenManifestRepository {
    /** Fetches the latest [ScreenManifestResponse] mapping registered screens to versions and ETags. */
    suspend fun fetchManifest(): AppResult<ScreenManifestResponse>
}

/**
 * Remote [ScreenManifestRepository] fetching `ginmw.Envelope<ScreenManifestResponse>` via [HttpTransport].
 */
class RemoteScreenManifestRepository(
    private val httpClient: HttpTransport,
    private val manifestPath: String = DEFAULT_MANIFEST_PATH,
) : ScreenManifestRepository {
    override suspend fun fetchManifest(): AppResult<ScreenManifestResponse> {
        val resolvedPath = manifestPath.trim().ifEmpty { DEFAULT_MANIFEST_PATH }
        val result =
            httpClient.request(
                method = HttpMethodType.GET,
                path = resolvedPath,
                deserializer = BffEnvelope.serializer(ScreenManifestResponse.serializer()),
            )
        return when (result) {
            is AppResult.Failure -> result
            is AppResult.Success -> unwrapManifestEnvelope(result.value, resolvedPath)
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
