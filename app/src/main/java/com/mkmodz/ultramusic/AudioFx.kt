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

    private fun releaseEffects() {
        try { eq?.release() } catch (ex: Exception) { }
        try { bass?.release() } catch (ex: Exception) { }
        try { virt?.release() } catch (ex: Exception) { }
        try { reverb?.release() } catch (ex: Exception) { }
        eq = null; bass = null; virt = null; reverb = null
    }

    fun releaseAll() {
        releaseEffects()
        sessionId = 0
    }
}
