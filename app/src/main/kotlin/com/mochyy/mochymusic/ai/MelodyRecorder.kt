package com.mochyy.mochymusic.ai

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class RecordedMelodySample(
    val wavBytes: ByteArray,
    val durationMs: Long,
    val pitchContourSummary: String,
    val detectedNotes: List<String>,
)

/**
 * Pure PCM & WAV helper + Android AudioRecord melody capture for Humming/Singing search.
 * Caller MUST verify RECORD_AUDIO permission before invoking capture().
 */
object MelodyRecorder {

    const val SAMPLE_RATE_HZ = 16_000
    private const val MAX_DURATION_MS = 8_000L

    private val NOTE_NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    fun frequencyToNoteName(freqHz: Double): String? {
        if (freqHz < 75.0 || freqHz > 1100.0) return null
        val midi = (69.0 + 12.0 * (ln(freqHz / 440.0) / ln(2.0))).roundToInt()
        if (midi < 24 || midi > 96) return null
        val note = NOTE_NAMES[midi % 12]
        val octave = (midi / 12) - 1
        return "$note$octave"
    }

    /**
     * Builds a valid 16-bit mono PCM RIFF WAV byte array from raw little-endian samples.
     */
    fun encodePcm16ToWav(pcmSamples: ShortArray, sampleRateHz: Int = SAMPLE_RATE_HZ): ByteArray {
        val dataSize = pcmSamples.size * 2
        val totalSize = 44 + dataSize
        val buffer = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)

        // RIFF header
        buffer.put('R'.code.toByte())
        buffer.put('I'.code.toByte())
        buffer.put('F'.code.toByte())
        buffer.put('F'.code.toByte())
        buffer.putInt(36 + dataSize)
        buffer.put('W'.code.toByte())
        buffer.put('A'.code.toByte())
        buffer.put('V'.code.toByte())
        buffer.put('E'.code.toByte())

        // fmt subchunk
        buffer.put('f'.code.toByte())
        buffer.put('m'.code.toByte())
        buffer.put('t'.code.toByte())
        buffer.put(' '.code.toByte())
        buffer.putInt(16) // PCM chunk size
        buffer.putShort(1) // PCM format
        buffer.putShort(1) // Mono
        buffer.putInt(sampleRateHz)
        buffer.putInt(sampleRateHz * 2) // byteRate
        buffer.putShort(2) // blockAlign
        buffer.putShort(16) // bitsPerSample

        // data subchunk
        buffer.put('d'.code.toByte())
        buffer.put('a'.code.toByte())
        buffer.put('t'.code.toByte())
        buffer.put('a'.code.toByte())
        buffer.putInt(dataSize)
        for (sample in pcmSamples) {
            buffer.putShort(sample)
        }
        return buffer.array()
    }

    /**
     * Estimates pitch (Hz) for a short mono 16kHz frame via normalized autocorrelation.
     */
    fun estimatePitchHz(frame: ShortArray, count: Int, sampleRateHz: Int = SAMPLE_RATE_HZ): Double? {
        if (count < 512) return null
        var energy = 0.0
        for (i in 0 until count) {
            val s = frame[i] / 32768.0
            energy += s * s
        }
        val rms = sqrt(energy / count)
        if (rms < 0.015) return null // silence / room noise

        val minLag = sampleRateHz / 900 // ~900 Hz max vocal hum
        val maxLag = (sampleRateHz / 80).coerceAtMost(count / 2) // ~80 Hz min hum
        var bestLag = -1
        var bestCorr = 0.0

        for (lag in minLag..maxLag) {
            var corr = 0.0
            var normA = 0.0
            var normB = 0.0
            val limit = count - lag
            var i = 0
            while (i < limit) {
                val a = frame[i].toDouble()
                val b = frame[i + lag].toDouble()
                corr += a * b
                normA += a * a
                normB += b * b
                i += 2 // 2x stride for low CPU overhead
            }
            val denom = sqrt(normA * normB)
            if (denom > 0.0) {
                val normalized = corr / denom
                if (normalized > bestCorr) {
                    bestCorr = normalized
                    bestLag = lag
                }
            }
        }
        if (bestLag <= 0 || bestCorr < 0.52) return null
        return sampleRateHz.toDouble() / bestLag.toDouble()
    }

    @SuppressLint("MissingPermission")
    fun capture(
        shouldContinue: () -> Boolean,
        onLevelUpdate: (level: Float, latestNote: String?, elapsedMs: Long) -> Unit,
    ): RecordedMelodySample {
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(4096)

        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBuf * 2,
        )
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            throw IllegalStateException("Microphone hardware could not be initialized.")
        }

        val frameSize = 1024
        val frame = ShortArray(frameSize)
        val rawSamplesOut = ByteArrayOutputStream()
        val detectedNotes = mutableListOf<String>()
        val startTime = System.currentTimeMillis()

        try {
            recorder.startRecording()
            while (shouldContinue()) {
                val elapsed = System.currentTimeMillis() - startTime
                if (elapsed >= MAX_DURATION_MS) break

                val read = recorder.read(frame, 0, frameSize)
                if (read > 0) {
                    // Store little-endian shorts
                    val bb = ByteBuffer.allocate(read * 2).order(ByteOrder.LITTLE_ENDIAN)
                    var sumSq = 0.0
                    for (i in 0 until read) {
                        val v = frame[i]
                        bb.putShort(v)
                        val norm = v / 32768.0
                        sumSq += norm * norm
                    }
                    rawSamplesOut.write(bb.array())

                    val rms = sqrt(sumSq / read).toFloat()
                    val level = (rms * 4.5f).coerceIn(0f, 1f)
                    val freq = estimatePitchHz(frame, read)
                    val note = freq?.let { frequencyToNoteName(it) }
                    if (note != null && detectedNotes.lastOrNull() != note && detectedNotes.size < 28) {
                        detectedNotes.add(note)
                    }
                    onLevelUpdate(level, note, elapsed)
                }
            }
        } finally {
            runCatching { recorder.stop() }
            runCatching { recorder.release() }
        }

        val totalBytes = rawSamplesOut.toByteArray()
        val sampleCount = totalBytes.size / 2
        val shorts = ShortArray(sampleCount)
        ByteBuffer.wrap(totalBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)

        val wavBytes = encodePcm16ToWav(shorts, SAMPLE_RATE_HZ)
        val durationMs = ((sampleCount.toDouble() / SAMPLE_RATE_HZ) * 1000.0).toLong()
        val contour = if (detectedNotes.isNotEmpty()) {
            "Detected pitch sequence: ${detectedNotes.joinToString(" -> ")}"
        } else {
            "Soft hum / rhythmic phrase captured (${durationMs / 1000.0}s)"
        }

        return RecordedMelodySample(
            wavBytes = wavBytes,
            durationMs = durationMs,
            pitchContourSummary = contour,
            detectedNotes = detectedNotes,
        )
    }
}
