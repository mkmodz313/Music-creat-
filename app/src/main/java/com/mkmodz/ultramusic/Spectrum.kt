package com.mkmodz.ultramusic

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

/**
 * Real-time spectrum. The player's decoded PCM audio is tapped (TeeAudioProcessor) and an FFT is
 * computed on it, so the bars always follow the real music. No fake animation, no microphone.
 */
@OptIn(UnstableApi::class)
object Spectrum {
    private const val N = 1024
    private val re = DoubleArray(N)
    private val im = DoubleArray(N)
    private val win = DoubleArray(N) { 0.5 - 0.5 * cos(2.0 * PI * it / (N - 1)) }
    private val ring = FloatArray(N)
    private var fill = 0
    private var channels = 2
    private var encoding = C.ENCODING_PCM_16BIT
    private var lastPublish = 0L

    val sink = object : TeeAudioProcessor.AudioBufferSink {
        override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) {
            channels = max(1, channelCount)
            this@Spectrum.encoding = encoding
            fill = 0
        }

        override fun handleBuffer(buffer: ByteBuffer) {
            val b = buffer.asReadOnlyBuffer().order(ByteOrder.nativeOrder())
            val bytes = when (encoding) {
                C.ENCODING_PCM_16BIT -> 2
                C.ENCODING_PCM_FLOAT -> 4
                else -> return
            }
            val frame = bytes * channels
            while (b.remaining() >= frame) {
                var sum = 0f
                for (c in 0 until channels) {
                    sum += if (bytes == 2) b.getShort() / 32768f else b.getFloat()
                }
                push(sum / channels)
            }
        }
    }

    private fun push(v: Float) {
        ring[fill++] = v
        if (fill >= N) {
            fill = 0
            val now = System.nanoTime()
            if (now - lastPublish > 28_000_000L) {
                lastPublish = now
                compute()
            }
        }
    }

    private fun compute() {
        for (i in 0 until N) { re[i] = ring[i] * win[i]; im[i] = 0.0 }
        fft(re, im)
        val half = N / 2
        val old = AudioFx.bars
        val out = FloatArray(32)
        for (b in 0 until 32) {
            val lo = max(1, half.toDouble().pow(b / 32.0).toInt())
            val hi = max(lo + 1, half.toDouble().pow((b + 1) / 32.0).toInt()).coerceAtMost(half)
            var sum = 0.0
            for (k in lo until hi) sum += hypot(re[k], im[k])
            val mag = (sum / (hi - lo)) / (N / 4.0)
            val db = 20.0 * log10(mag + 1e-9) + b * 0.45
            val level = ((db + 72.0) / 52.0).toFloat().coerceIn(0f, 1f)
            out[b] = max(level, old[b] * 0.80f)
        }
        AudioFx.bars = out
    }

    /** Called by the UI while paused so the bars fall back to zero. */
    fun decay() {
        val old = AudioFx.bars
        if (old.all { it < 0.01f }) return
        AudioFx.bars = FloatArray(32) { old[it] * 0.8f }
    }

    private fun fft(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while ((j and bit) != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }
        var len = 2
        while (len <= n) {
            val ang = -2.0 * PI / len
            val wr = cos(ang)
            val wi = sin(ang)
            var i = 0
            while (i < n) {
                var cr = 1.0
                var ci = 0.0
                for (k in 0 until len / 2) {
                    val a = i + k
                    val b = i + k + len / 2
                    val vr = re[b] * cr - im[b] * ci
                    val vi = re[b] * ci + im[b] * cr
                    re[b] = re[a] - vr
                    im[b] = im[a] - vi
                    re[a] = re[a] + vr
                    im[a] = im[a] + vi
                    val ncr = cr * wr - ci * wi
                    ci = cr * wi + ci * wr
                    cr = ncr
                }
                i += len
            }
            len = len shl 1
        }
    }
}
