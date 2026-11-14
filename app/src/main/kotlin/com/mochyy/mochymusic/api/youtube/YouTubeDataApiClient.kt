package com.mochyy.mochymusic.api.youtube

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.mochyy.mochymusic.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeDataApiClient @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        const val BASE_URL = "https://www.googleapis.com/youtube/v3"
        private const val PLACEHOLDER_KEY = "YOUR_YOUTUBE_API_KEY"
    }

    internal val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    private val apiKey: String
        get() = BuildConfig.YOUTUBE_API_KEY.trim()

    private val cachedSha1Bytes: ByteArray? by lazy { computeSigningCertSha1Bytes() }

    fun isKeyConfigured(): Boolean =
        apiKey.isNotEmpty() && apiKey != PLACEHOLDER_KEY

    fun packageName(): String = context.packageName

    fun signingSha1Compact(): String? =
        cachedSha1Bytes?.joinToString("") { "%02X".format(it) }

    fun signingSha1Formatted(): String? =
        cachedSha1Bytes?.joinToString(":") { "%02X".format(it) }

    suspend fun get(
        endpoint: String,
        params: Map<String, String>,
    ): YouTubeDataApiResult<String> = withContext(Dispatchers.IO) {
        if (!isKeyConfigured()) {
            return@withContext YouTubeDataApiResult.MissingKey()
        }

        val cleanEndpoint = endpoint.trimStart('/')
        val urlBuilder = "$BASE_URL/$cleanEndpoint".toHttpUrl().newBuilder()
            .addQueryParameter("key", apiKey)

        params.forEach { (k, v) ->
            if (v.isNotEmpty()) {
                urlBuilder.addQueryParameter(k, v)
            }
        }

        val requestBuilder = Request.Builder()
            .url(urlBuilder.build())
            .get()
            .header("Accept", "application/json")
            .header("X-Android-Package", packageName())

        signingSha1Compact()?.let { certHex ->
            requestBuilder.header("X-Android-Cert", certHex)
        }

        try {
            httpClient.newCall(requestBuilder.build()).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    return@withContext YouTubeDataApiResult.Success(body)
                }
                return@withContext mapHttpError(response.code, body)
            }
        } catch (e: IOException) {
            return@withContext YouTubeDataApiResult.NetworkError(
                e.localizedMessage?.takeIf { it.isNotBlank() }
                    ?: "Network error while contacting YouTube Data API.",
            )
        } catch (e: Exception) {
            return@withContext YouTubeDataApiResult.NetworkError(
                e.localizedMessage?.takeIf { it.isNotBlank() }
                    ?: "Unexpected error contacting YouTube Data API.",
            )
        }
    }

    private fun mapHttpError(httpCode: Int, rawBody: String): YouTubeDataApiResult.Failure {
        val parsed = runCatching {
            json.decodeFromString(YouTubeApiErrorEnvelope.serializer(), rawBody)
        }.getOrNull()

        val errorBody = parsed?.error
        val reason = errorBody?.errors?.firstOrNull()?.reason
        val serverMessage = errorBody?.message?.takeIf { it.isNotBlank() }

        return when {
            reason == "quotaExceeded" ||
                reason == "dailyLimitExceeded" ||
                reason == "rateLimitExceeded" ||
                httpCode == 429 -> {
                YouTubeDataApiResult.QuotaExceeded(
                    serverMessage ?: "YouTube Data API daily quota exceeded. Try again later.",
                )
            }

            reason == "keyInvalid" ||
                reason == "API_KEY_INVALID" ||
                reason == "ipRefererBlocked" ||
                reason == "API_KEY_ANDROID_APP_BLOCKED" ||
                reason == "accessNotConfigured" ||
                (httpCode == 403 && reason in setOf("forbidden", "keyExpired")) -> {
                val certHint = signingSha1Formatted()?.let { sha1 ->
                    " Ensure package ${packageName()} and SHA-1 $sha1 are allowed for this key."
                }.orEmpty()
                YouTubeDataApiResult.KeyRestricted(
                    (serverMessage ?: "YouTube Data API key was rejected ($httpCode).") + certHint,
                )
            }

            httpCode == 404 || reason == "playlistNotFound" || reason == "channelNotFound" -> {
                YouTubeDataApiResult.NotFound(
                    serverMessage ?: "The requested playlist, channel, or video was not found.",
                )
            }

            else -> {
                YouTubeDataApiResult.ApiError(
                    httpCode = httpCode,
                    reason = reason,
                    message = serverMessage ?: "YouTube Data API returned HTTP $httpCode.",
                )
            }
        }
    }

    private fun computeSigningCertSha1Bytes(): ByteArray? {
        return runCatching {
            val pm = context.packageManager
            val certBytes: ByteArray? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val info = pm.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES,
                )
                val signingInfo = info.signingInfo
                val sig = signingInfo?.apkContentsSigners?.firstOrNull()
                    ?: signingInfo?.signingCertificateHistory?.firstOrNull()
                sig?.toByteArray()
            } else {
                @Suppress("DEPRECATION")
                val info = pm.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNATURES,
                )
                @Suppress("DEPRECATION")
                info.signatures?.firstOrNull()?.toByteArray()
            }
            certBytes?.let { MessageDigest.getInstance("SHA-1").digest(it) }
        }.getOrNull()
    }
}
