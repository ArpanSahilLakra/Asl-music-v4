package com.music.innertube

import com.music.innertube.models.response.PlayerResponse

data class NewPipeAudioStream(
    val itag: Int,
    val url: String,
    val mimeType: String,
    val bitrateBps: Int,
    val codec: String?,
    val qualityLabel: String?,
    val contentLength: Long? = null,
)

/**
 * Lightweight no-op stub for NewPipeExtractor.
 * YouTube stream extraction is disabled in ASL Beats for strict policy compliance,
 * allowing removal of NewPipeExtractor and transitive Mozilla Rhino JS engine (~4.6 MB DEX).
 */
object NewPipeExtractor {
    fun init() {}

    fun getSignatureTimestamp(videoId: String): Result<Int> =
        Result.failure(UnsupportedOperationException("YouTube stream extraction disabled"))

    fun getStreamUrl(
        format: PlayerResponse.StreamingData.Format,
        videoId: String
    ): String? = format.url

    fun getAudioStreams(videoId: String): List<NewPipeAudioStream> = emptyList()

    fun newPipePlayer(videoId: String): List<Pair<Int, String>> = emptyList()
}
