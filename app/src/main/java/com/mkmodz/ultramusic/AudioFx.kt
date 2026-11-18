package com.mkmodz.ultramusic

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.PresetReverb
import android.media.audiofx.Virtualizer
import android.media.audiofx.Visualizer
import androidx.compose.runtime.*
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow

/** Real audio effects attached to the ExoPlayer audio session + live FFT spectrum. */
object AudioFx {
    private var eq: Equalizer? = null
    private var bass: BassBoost? = null
    private var virt: Virtualizer? = null
    private var reverb: PresetReverb? = null
    private var viz: Visualizer? = null
    private var sessionId = 0

    var enabled by mutableStateOf(true)
    var supported by mutableStateOf(true)
    var bandCount by mutableIntStateOf(0)
    var minLevel by mutableIntStateOf(-1500)
    var maxLevel by mutableIntStateOf(1500)
    var bandFreqs by mutableStateOf<List<Int>>(emptyList())
    var bandLevels by mutableStateOf<List<Int>>(emptyList())
    var bassStrength by mutableIntStateOf(0)     // 0..1000
    var virtStrength by mutableIntStateOf(0)     // 0..1000
    var treble by mutableIntStateOf(0)           // -1000..1000 millibel on upper bands
    var reverbPreset by mutableIntStateOf(0)
    var presetName by mutableStateOf("Normal")

    var bars by mutableStateOf(FloatArray(32))
    var vizActive by mutableStateOf(false)
    var vizWanted = false

    val presetNames = listOf("Normal", "Rock", "Pop", "Jazz", "Classical", "Dance", "Custom")
    val reverbNames = listOf("Off", "Small room", "Medium room", "Large room", "Medium hall", "Large hall", "Plate")

    private val curves = mapOf(
        "Normal" to floatArrayOf(0f, 0f, 0f, 0f, 0f),
        "Rock" to floatArrayOf(0.6f, 0.3f, -0.2f, 0.3f, 0.6f),
        "Pop" to floatArrayOf(-0.2f, 0.3f, 0.5f, 0.3f, -0.2f),
        "Jazz" to floatArrayOf(0.3f, 0.1f, -0.2f, 0.2f, 0.4f),
        "Classical" to floatArrayOf(0.5f, 0.3f, -0.2f, 0.3f, 0.4f),
        "Dance" to floatArrayOf(0.7f, 0.4f, 0f, 0.3f, 0.5f)
    )

    fun init() {
        val s = Prefs.sp
        enabled = s.getBoolean("fx_enabled", true)
        bassStrength = s.getInt("fx_bass", 0)
        virtStrength = s.getInt("fx_virt", 0)
        treble = s.getInt("fx_treble", 0)
        reverbPreset = s.getInt("fx_reverb", 0)
        presetName = s.getString("fx_preset", "Normal") ?: "Normal"
    }

    fun persist() {
        Prefs.sp.edit()
            .putBoolean("fx_enabled", enabled)
            .putInt("fx_bass", bassStrength)
            .putInt("fx_virt", virtStrength)
            .putInt("fx_treble", treble)
            .putInt("fx_reverb", reverbPreset)
            .putString("fx_preset", presetName)
            .putString("eq_levels", bandLevels.joinToString(","))
            .apply()
    }

    @Synchronized
    fun attach(id: Int) {
        if (id == 0) return
        if (id == sessionId && eq != null) return
        releaseEffects()
        sessionId = id
        try {
            val e = Equalizer(0, id)
            eq = e
            bandCount = e.numberOfBands.toInt()
            val r = e.bandLevelRange
            minLevel = r[0].toInt()
            maxLevel = r[1].toInt()
            bandFreqs = (0 until bandCount).map { e.getCenterFreq(it.toShort()) / 1000 }
            val saved = (Prefs.sp.getString("eq_levels", "") ?: "").split(",").mapNotNull { it.toIntOrNull() }
            bandLevels = if (saved.size == bandCount) saved else List(bandCount) { 0 }
            supported = true
        } catch (ex: Exception) {
            supported = false
        }
        try { bass = BassBoost(0, id) } catch (ex: Exception) { bass = null }
        try { virt = Virtualizer(0, id) } catch (ex: Exception) { virt = null }
        try { reverb = PresetReverb(0, id) } catch (ex: Exception) { reverb = null }
        applyAll()
        if (vizWanted) startViz()
    }

    fun applyAll() {
        try {
            eq?.let { e ->
                for (i in 0 until bandCount) {
                    val extra = if (i >= bandCount / 2) treble else 0
                    val lvl = (bandLevels.getOrElse(i) { 0 } + extra).coerceIn(minLevel, maxLevel)
                    e.setBandLevel(i.toShort(), lvl.toShort())
                }
                e.enabled = enabled
            }
        } catch (ex: Exception) { }
        try {
            bass?.let { b ->
                if (b.strengthSupported) b.setStrength(bassStrength.toShort())
                b.enabled = enabled && bassStrength > 0
            }
        } catch (ex: Exception) { }
        try {
            virt?.let { v ->
                if (v.strengthSupported) v.setStrength(virtStrength.toShort())
                v.enabled = enabled && virtStrength > 0
            }
        } catch (ex: Exception) { }
        try {
            reverb?.let { r ->
                r.preset = reverbPreset.toShort()
                r.enabled = enabled && reverbPreset != 0
            }
        } catch (ex: Exception) { }
    }

    fun setBand(i: Int, level: Int) {
        bandLevels = bandLevels.toMutableList().also { if (i in it.indices) it[i] = level }
        presetName = "Custom edit"
        applyAll()
    }

    fun applyPreset(name: String) {
        if (bandCount == 0) return
        if (name == "Custom") {
            val s = Prefs.sp.getString("custom_eq", null)?.split(",")?.mapNotNull { it.toIntOrNull() }
            if (s != null && s.size == bandCount) bandLevels = s
        } else {
            val c = curves[name] ?: return
            bandLevels = List(bandCount) { i ->
                val t = if (bandCount <= 1) 0f else i.toFloat() / (bandCount - 1)
                (interp(c, t) * maxLevel).toInt()
            }
        }
        presetName = name
        persist()
        applyAll()
    }

    fun saveCustom() {
        Prefs.sp.edit().putString("custom_eq", bandLevels.joinToString(",")).apply()
        presetName = "Custom"
        persist()
    }

    private fun interp(c: FloatArray, t: Float): Float {
        val x = t * (c.size - 1)
        val i = x.toInt().coerceAtMost(c.size - 2)
        val f = x - i
        return c[i] * (1 - f) + c[i + 1] * f
    }

    // ---------- live spectrum ----------
    @Synchronized
    fun startViz() {
        stopViz()
        if (sessionId == 0) return
        try {
            val v = Visualizer(sessionId)
            v.enabled = false
            v.captureSize = Visualizer.getCaptureSizeRange()[1].coerceAtMost(1024)
            v.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                override fun onWaveFormDataCapture(vis: Visualizer?, waveform: ByteArray?, samplingRate: Int) {}
                override fun onFftDataCapture(vis: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                    if (fft != null) processFft(fft)
                }
            }, Visualizer.getMaxCaptureRate() / 2, false, true)
            v.enabled = true
            viz = v
            vizActive = true
        } catch (ex: Exception) {
            vizActive = false
        }
    }

    @Synchronized
    fun stopViz() {
        try { viz?.enabled = false; viz?.release() } catch (ex: Exception) { }
        viz = null
        vizActive = false
        bars = FloatArray(32)
    }

    private fun processFft(fft: ByteArray) {
        val n = fft.size / 2
        val old = bars
        val out = FloatArray(32)
        for (b in 0 until 32) {
            val lo = max(1, n.toDouble().pow(b / 32.0).toInt())
            val hi = max(lo + 1, n.toDouble().pow((b + 1) / 32.0).toInt()).coerceAtMost(n)
            var sum = 0.0
            var cnt = 0
            for (k in lo until hi) {
                val re = fft[2 * k].toInt()
                val im = fft[2 * k + 1].toInt()
                sum += hypot(re.toDouble(), im.toDouble())
                cnt++
            }
            val avg = if (cnt > 0) sum / cnt else 0.0
            val level = (ln(1.0 + avg) / ln(1.0 + 181.0)).toFloat().coerceIn(0f, 1f)
            out[b] = max(level, old[b] * 0.82f)
        }
        bars = out
    }

    private fun releaseEffects() {
        try { eq?.release() } catch (ex: Exception) { }
        try { bass?.release() } catch (ex: Exception) { }
        try { virt?.release() } catch (ex: Exception) { }
        try { reverb?.release() } catch (ex: Exception) { }
        eq = null; bass = null; virt = null; reverb = null
    }

    fun releaseAll() {
        stopViz()
        releaseEffects()
        sessionId = 0
    }
}
