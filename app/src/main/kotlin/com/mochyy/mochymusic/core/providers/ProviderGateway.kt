package com.mochyy.mochymusic.core.providers

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeout

enum class ProviderCapability {
    STREAMING,
    LYRICS_WORD,
    LYRICS_LINE,
    CANVAS,
    ARTIST_BIO,
    AUDIO_RECOGNITION,
    SCROBBLING,
    SOURCE_MODULE
}

enum class ProviderHealthStatus {
    HEALTHY,
    DEGRADED,
    PAUSED
}

data class ProviderSpec(
    val id: String,
    val displayName: String,
    val capability: ProviderCapability,
    val required: Boolean,
    val timeoutMs: Long = 5_000L,
    val maxConsecutiveFailures: Int = 3
)

data class ProviderHealthState(
    val spec: ProviderSpec,
    val enabled: Boolean = true,
    val status: ProviderHealthStatus = ProviderHealthStatus.HEALTHY,
    val consecutiveFailures: Int = 0,
    val lastErrorMessage: String? = null,
    val lastUpdatedEpochMs: Long = 0L
)

/**
 * Unified non-blocking Provider Gateway & Session Circuit Breaker Registry.
 * Wraps all 16 `:providers:*` modules and 3 `:modules:*` source bridges with deterministic
 * timeouts, failure circuit breaking, health telemetry, and fallback chains so no optional
 * provider can ever stall core Media3/Innertube playback.
 */
object ProviderGateway {

    val defaultSpecs: List<ProviderSpec> = listOf(
        ProviderSpec("innertube", "YouTube Music (Innertube)", ProviderCapability.STREAMING, required = true, timeoutMs = 10_000L, maxConsecutiveFailures = 5),
        ProviderSpec("betterlyrics", "BetterLyrics", ProviderCapability.LYRICS_WORD, required = false, timeoutMs = 4_500L),
        ProviderSpec("lrclib", "LRCLIB", ProviderCapability.LYRICS_LINE, required = false, timeoutMs = 4_500L),
        ProviderSpec("kugou", "KuGou Lyrics", ProviderCapability.LYRICS_WORD, required = false, timeoutMs = 4_500L),
        ProviderSpec("musixmatch", "Musixmatch", ProviderCapability.LYRICS_LINE, required = false, timeoutMs = 4_500L),
        ProviderSpec("simpmusic", "SimpMusic Lyrics", ProviderCapability.LYRICS_LINE, required = false, timeoutMs = 4_500L),
        ProviderSpec("youlyplus", "YouLyPlus", ProviderCapability.LYRICS_WORD, required = false, timeoutMs = 4_500L),
        ProviderSpec("paxsenixlyrics", "Paxsenix Lyrics", ProviderCapability.LYRICS_WORD, required = false, timeoutMs = 4_500L),
        ProviderSpec("directlyrics", "DirectLyrics", ProviderCapability.LYRICS_LINE, required = false, timeoutMs = 4_500L),
        ProviderSpec("genius", "Genius", ProviderCapability.LYRICS_LINE, required = false, timeoutMs = 4_500L),
        ProviderSpec("canvas", "Spotify Canvas", ProviderCapability.CANVAS, required = false, timeoutMs = 4_000L),
        ProviderSpec("applecanvas", "Apple Motion Canvas", ProviderCapability.CANVAS, required = false, timeoutMs = 4_000L),
        ProviderSpec("artistvideo", "Artist Video Backdrop", ProviderCapability.CANVAS, required = false, timeoutMs = 4_000L),
        ProviderSpec("wikipedia", "Wikipedia & Wikidata", ProviderCapability.ARTIST_BIO, required = false, timeoutMs = 4_000L),
        ProviderSpec("shazamkit", "ShazamKit Audio Recognition", ProviderCapability.AUDIO_RECOGNITION, required = false, timeoutMs = 8_000L),
        ProviderSpec("lastfm", "Last.fm / Libre.fm Scrobbler", ProviderCapability.SCROBBLING, required = false, timeoutMs = 5_000L),
        ProviderSpec("applemusic", "Apple Music Module", ProviderCapability.SOURCE_MODULE, required = false, timeoutMs = 6_000L),
        ProviderSpec("jiosaavn", "JioSaavn Module", ProviderCapability.SOURCE_MODULE, required = false, timeoutMs = 6_000L),
        ProviderSpec("monochrome", "Monochrome Lossless Module", ProviderCapability.SOURCE_MODULE, required = false, timeoutMs = 6_000L),
    )

    private val _states = MutableStateFlow<Map<String, ProviderHealthState>>(
        defaultSpecs.associate { it.id to ProviderHealthState(spec = it) }
    )
    val states: StateFlow<Map<String, ProviderHealthState>> = _states.asStateFlow()

    fun isUsable(providerId: String): Boolean {
        val st = _states.value[providerId] ?: return true
        return st.enabled && st.status != ProviderHealthStatus.PAUSED
    }

    fun setEnabled(providerId: String, enabled: Boolean) {
        _states.update { current ->
            val existing = current[providerId] ?: return@update current
            if (existing.spec.required && !enabled) return@update current
            current + (providerId to existing.copy(
                enabled = enabled,
                status = if (enabled) ProviderHealthStatus.HEALTHY else existing.status,
                consecutiveFailures = if (enabled) 0 else existing.consecutiveFailures,
                lastUpdatedEpochMs = System.currentTimeMillis()
            ))
        }
    }

    fun resetSessionCircuit(providerId: String) {
        _states.update { current ->
            val existing = current[providerId] ?: return@update current
            current + (providerId to existing.copy(
                status = ProviderHealthStatus.HEALTHY,
                consecutiveFailures = 0,
                lastErrorMessage = null,
                lastUpdatedEpochMs = System.currentTimeMillis()
            ))
        }
    }

    fun recordSuccess(providerId: String) {
        _states.update { current ->
            val existing = current[providerId] ?: return@update current
            if (existing.consecutiveFailures == 0 && existing.status == ProviderHealthStatus.HEALTHY) {
                return@update current
            }
            current + (providerId to existing.copy(
                status = ProviderHealthStatus.HEALTHY,
                consecutiveFailures = 0,
                lastErrorMessage = null,
                lastUpdatedEpochMs = System.currentTimeMillis()
            ))
        }
    }

    fun recordFailure(providerId: String, error: Throwable) {
        _states.update { current ->
            val existing = current[providerId] ?: return@update current
            val nextFailures = existing.consecutiveFailures + 1
            val nextStatus = when {
                !existing.spec.required && nextFailures >= existing.spec.maxConsecutiveFailures ->
                    ProviderHealthStatus.PAUSED
                else -> ProviderHealthStatus.DEGRADED
            }
            current + (providerId to existing.copy(
                status = nextStatus,
                consecutiveFailures = nextFailures,
                lastErrorMessage = error.message ?: error::class.simpleName,
                lastUpdatedEpochMs = System.currentTimeMillis()
            ))
        }
    }

    /**
     * Executes [block] under the provider's timeout budget and circuit breaker.
     * Returns null quietly if the provider is disabled, paused by the circuit breaker, or fails.
     */
    suspend fun <T> callOrNull(providerId: String, block: suspend () -> T?): T? {
        val state = _states.value[providerId]
        if (state != null && (!state.enabled || state.status == ProviderHealthStatus.PAUSED)) {
            return null
        }
        val timeout = state?.spec?.timeoutMs ?: 5_000L
        return try {
            val result = withTimeout(timeout) { block() }
            if (result != null) {
                recordSuccess(providerId)
            }
            result
        } catch (t: TimeoutCancellationException) {
            recordFailure(providerId, t)
            null
        } catch (t: Throwable) {
            recordFailure(providerId, t)
            null
        }
    }

    /**
     * Runs an ordered fallback chain of providers and returns the first non-null result.
     */
    suspend fun <T> runFallbackChain(
        providerOrder: List<String>,
        callProvider: suspend (providerId: String) -> T?
    ): T? {
        for (providerId in providerOrder) {
            val value = callOrNull(providerId) { callProvider(providerId) }
            if (value != null) return value
        }
        return null
    }
}
