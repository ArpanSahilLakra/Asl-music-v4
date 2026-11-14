package com.mochyy.mochymusic.api.ytmusic

import android.content.Context
import com.mochyy.mochymusic.constants.DefaultFastApiBackendUrl
import com.mochyy.mochymusic.constants.FastApiBackendUrlKey
import com.mochyy.mochymusic.utils.dataStore
import com.mochyy.mochymusic.utils.get
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

sealed interface DiscoveryResult<out T> {
    data class Success<T>(val data: T) : DiscoveryResult<T>
    data class Error(
        val httpCode: Int?,
        val message: String,
        val isRateLimit: Boolean = false,
        val isNotFound: Boolean = false,
        val isNetworkError: Boolean = false,
    ) : DiscoveryResult<Nothing>
}

@Singleton
class YtMusicBackendClient @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    init {
        instance = this
    }

    internal val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .build()
    }

    companion object {
        @Volatile
        var instance: YtMusicBackendClient? = null
    }

    @Volatile
    private var overrideBaseUrl: String? = null

    fun setOverrideBaseUrlForTesting(url: String?) {
        overrideBaseUrl = url
    }

    fun currentBaseUrl(): String {
        overrideBaseUrl?.takeIf { it.isNotBlank() }?.let { return it.trimEnd('/') }
        val stored = runCatching {
            context.dataStore.get(FastApiBackendUrlKey, DefaultFastApiBackendUrl)
        }.getOrDefault(DefaultFastApiBackendUrl)
        return stored.trim().ifBlank { DefaultFastApiBackendUrl }.trimEnd('/')
    }

    suspend fun checkHealth(): Boolean = withContext(Dispatchers.IO) {
        val base = currentBaseUrl()
        val url = "$base/api/health".toHttpUrlOrNull() ?: return@withContext false
        val request = Request.Builder().url(url).get().build()
        runCatching {
            httpClient.newCall(request).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }

    suspend fun search(
        query: String,
        filter: String? = null,
        limit: Int = 20,
        offset: Int = 0,
    ): DiscoveryResult<YtSearchResponseDto> {
        val params = linkedMapOf(
            "q" to query.trim(),
            "limit" to limit.toString(),
            "offset" to offset.toString(),
        )
        if (!filter.isNullOrBlank()) {
            params["filter"] = filter
        }
        return get("/api/search", params, YtSearchResponseDto.serializer())
    }

    suspend fun getSuggestions(query: String): DiscoveryResult<YtSuggestionsResponseDto> {
        return get(
            path = "/api/suggestions",
            params = mapOf("q" to query.trim()),
            serializer = YtSuggestionsResponseDto.serializer(),
        )
    }

    suspend fun getSong(videoId: String): DiscoveryResult<YtSongDetailDto> {
        return get(
            path = "/api/song/${videoId.trim()}",
            params = emptyMap(),
            serializer = YtSongDetailDto.serializer(),
        )
    }

    suspend fun getLyrics(videoId: String): DiscoveryResult<YtLyricsResponseDto> {
        return get(
            path = "/api/lyrics/${videoId.trim()}",
            params = emptyMap(),
            serializer = YtLyricsResponseDto.serializer(),
        )
    }

    suspend fun getAlbum(browseId: String): DiscoveryResult<YtAlbumDetailDto> {
        return get(
            path = "/api/album/${browseId.trim()}",
            params = emptyMap(),
            serializer = YtAlbumDetailDto.serializer(),
        )
    }

    suspend fun getArtist(browseId: String): DiscoveryResult<YtArtistDetailDto> {
        return get(
            path = "/api/artist/${browseId.trim()}",
            params = emptyMap(),
            serializer = YtArtistDetailDto.serializer(),
        )
    }

    suspend fun getPlaylist(
        playlistId: String,
        limit: Int = 50,
        offset: Int = 0,
    ): DiscoveryResult<YtPlaylistDetailDto> {
        val cleanId = playlistId.trim().removePrefix("VL")
        return get(
            path = "/api/playlist/$cleanId",
            params = mapOf(
                "limit" to limit.toString(),
                "offset" to offset.toString(),
            ),
            serializer = YtPlaylistDetailDto.serializer(),
        )
    }

    suspend fun getRadio(
        videoId: String,
        limit: Int = 25,
    ): DiscoveryResult<YtRadioResponseDto> {
        return get(
            path = "/api/radio/${videoId.trim()}",
            params = mapOf("limit" to limit.toString()),
            serializer = YtRadioResponseDto.serializer(),
        )
    }

    private suspend fun <T> get(
        path: String,
        params: Map<String, String>,
        serializer: KSerializer<T>,
    ): DiscoveryResult<T> = withContext(Dispatchers.IO) {
        val base = currentBaseUrl()
        val parsedBase = "$base${if (path.startsWith("/")) path else "/$path"}".toHttpUrlOrNull()
            ?: return@withContext DiscoveryResult.Error(
                httpCode = null,
                message = "Invalid FastAPI backend URL: $base",
                isNetworkError = true,
            )

        val urlBuilder = parsedBase.newBuilder()
        params.forEach { (k, v) ->
            if (v.isNotEmpty()) {
                urlBuilder.addQueryParameter(k, v)
            }
        }

        val request = Request.Builder()
            .url(urlBuilder.build())
            .get()
            .header("Accept", "application/json")
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    val decoded = runCatching {
                        json.decodeFromString(serializer, body)
                    }.getOrElse { err ->
                        return@withContext DiscoveryResult.Error(
                            httpCode = response.code,
                            message = err.localizedMessage ?: "Failed to parse discovery response.",
                        )
                    }
                    return@withContext DiscoveryResult.Success(decoded)
                }

                val detail = runCatching {
                    json.decodeFromString(FastApiErrorEnvelope.serializer(), body).detail
                }.getOrNull()

                return@withContext DiscoveryResult.Error(
                    httpCode = response.code,
                    message = detail ?: "Discovery backend returned HTTP ${response.code}.",
                    isRateLimit = response.code == 429,
                    isNotFound = response.code == 404,
                )
            }
        } catch (e: IOException) {
            return@withContext DiscoveryResult.Error(
                httpCode = null,
                message = e.localizedMessage ?: "Unable to reach FastAPI discovery backend at $base.",
                isNetworkError = true,
            )
        } catch (e: Exception) {
            return@withContext DiscoveryResult.Error(
                httpCode = null,
                message = e.localizedMessage ?: "Unexpected error querying discovery backend.",
                isNetworkError = true,
            )
        }
    }
}
