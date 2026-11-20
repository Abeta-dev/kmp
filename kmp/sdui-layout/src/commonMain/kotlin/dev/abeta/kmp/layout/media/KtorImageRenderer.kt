package dev.abeta.kmp.layout.media

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import dev.abeta.kmp.atoms.atoms.UiImageRenderer
import dev.abeta.kmp.atoms.atoms.resolveContentScale
import dev.abeta.kmp.atoms.tokens.resolve
import dev.abeta.kmp.contracts.ImageAtom
import dev.abeta.kmp.core.error.NetworkError
import dev.abeta.kmp.core.error.SerializationError
import dev.abeta.kmp.core.error.ValidationError
import dev.abeta.kmp.core.resilience.SingleFlight
import dev.abeta.kmp.core.result.AppResult
import dev.abeta.kmp.tokens.ThemeTokens
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.readRawBytes
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal expect fun decodeImageBitmapOrNull(bytes: ByteArray): ImageBitmap?

/**
 * Port for fetching raw encoded image bytes from a remote URL or asset URI.
 */
fun interface ImageBytesFetcher {
    /** Downloads raw image bytes for [url]. */
    suspend fun fetch(url: String): AppResult<ByteArray>
}

/**
 * [ImageBytesFetcher] implementation backed by Ktor [HttpClient].
 */
class KtorImageBytesFetcher(
    private val httpClient: HttpClient,
) : ImageBytesFetcher {
    @Suppress("TooGenericExceptionCaught")
    override suspend fun fetch(url: String): AppResult<ByteArray> {
        if (url.isBlank()) {
            return AppResult.Failure(
                ValidationError(
                    field = "url",
                    message = "Image URL must not be blank.",
                ),
            )
        }
        return try {
            val response = httpClient.get(url)
            if (response.status.isSuccess()) {
                AppResult.Success(response.readRawBytes())
            } else {
                AppResult.Failure(
                    NetworkError(
                        statusCode = response.status.value,
                        message = "Image fetch failed for '$url': HTTP ${response.status.value}",
                    ),
                )
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            AppResult.Failure(
                NetworkError(
                    statusCode = null,
                    message = throwable.message ?: "Failed to fetch image '$url'",
                    cause = throwable,
                ),
            )
        }
    }
}

/**
 * [UiImageRenderer] backed by [ImageBytesFetcher] with single-flight coalescing and a bounded
 * multiplatform LRU in-memory [ImageBitmap] cache.
 */
class KtorImageRenderer(
    private val fetcher: ImageBytesFetcher,
    private val maxCacheEntries: Int = DEFAULT_MAX_CACHE_ENTRIES,
) : UiImageRenderer {
    init {
        require(maxCacheEntries >= 1) { "maxCacheEntries must be >= 1." }
    }

    private val cacheMutex = Mutex()
    private val lruCache = linkedMapOf<String, ImageBitmap>()
    private val inFlight = SingleFlight<String, AppResult<ImageBitmap>>()

    /**
     * Loads and decodes the image at [url], returning a cached [ImageBitmap] on LRU hit.
     */
    suspend fun loadBitmap(url: String): AppResult<ImageBitmap> {
        val cleanUrl = url.trim()
        val cached = if (cleanUrl.isNotEmpty()) getFromCache(cleanUrl) else null
        return when {
            cleanUrl.isEmpty() -> {
                AppResult.Failure(
                    ValidationError(
                        field = "url",
                        message = "Image URL must not be blank.",
                    ),
                )
            }

            cached != null -> {
                AppResult.Success(cached)
            }

            else -> {
                inFlight.execute(cleanUrl) {
                    fetchAndDecodeBitmap(cleanUrl)
                }
            }
        }
    }

    private suspend fun fetchAndDecodeBitmap(cleanUrl: String): AppResult<ImageBitmap> {
        val cached = getFromCache(cleanUrl)
        return if (cached != null) {
            AppResult.Success(cached)
        } else {
            when (val bytesResult = fetcher.fetch(cleanUrl)) {
                is AppResult.Failure -> {
                    bytesResult
                }

                is AppResult.Success -> {
                    val bitmap = decodeImageBitmapOrNull(bytesResult.value)
                    if (bitmap != null) {
                        putInCache(cleanUrl, bitmap)
                        AppResult.Success(bitmap)
                    } else {
                        AppResult.Failure(
                            SerializationError(
                                message = "Unable to decode image bitmap from '$cleanUrl'.",
                            ),
                        )
                    }
                }
            }
        }
    }

    /** Clears all cached bitmaps from memory. */
    suspend fun clearCache() {
        cacheMutex.withLock {
            lruCache.clear()
        }
    }

    internal suspend fun cacheSize(): Int = cacheMutex.withLock { lruCache.size }

    @Composable
    override fun render(
        atom: ImageAtom,
        modifier: Modifier,
    ) {
        val colors = ThemeTokens.colors
        val placeholder = atom.placeholderColor.resolve(fallback = colors.surface.secondary)
        var loadState by remember(atom.url) { mutableStateOf<ImageLoadState>(ImageLoadState.Loading) }

        LaunchedEffect(atom.url) {
            loadState =
                when (val result = loadBitmap(atom.url)) {
                    is AppResult.Success -> ImageLoadState.Loaded(result.value)
                    is AppResult.Failure -> ImageLoadState.Failed
                }
        }

        when (val current = loadState) {
            is ImageLoadState.Loaded -> {
                Image(
                    bitmap = current.bitmap,
                    contentDescription = atom.contentDescription,
                    modifier = modifier,
                    contentScale = resolveContentScale(atom.scaleMode),
                )
            }

            ImageLoadState.Loading,
            ImageLoadState.Failed,
            -> {
                Box(modifier = modifier.background(placeholder))
            }
        }
    }

    private suspend fun getFromCache(key: String): ImageBitmap? =
        cacheMutex.withLock {
            val hit = lruCache.remove(key) ?: return@withLock null
            lruCache[key] = hit
            hit
        }

    private suspend fun putInCache(
        key: String,
        bitmap: ImageBitmap,
    ) {
        cacheMutex.withLock {
            if (key in lruCache) {
                lruCache.remove(key)
            } else if (lruCache.size >= maxCacheEntries) {
                val oldestKey = lruCache.keys.firstOrNull()
                if (oldestKey != null) {
                    lruCache.remove(oldestKey)
                }
            }
            lruCache[key] = bitmap
        }
    }

    private sealed interface ImageLoadState {
        data object Loading : ImageLoadState

        data class Loaded(
            val bitmap: ImageBitmap,
        ) : ImageLoadState

        data object Failed : ImageLoadState
    }

    companion object {
        const val DEFAULT_MAX_CACHE_ENTRIES: Int = 32
    }
}
