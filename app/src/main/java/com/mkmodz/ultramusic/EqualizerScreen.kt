package com.mkmodz.ultramusic

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MkChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.padding(end = 8.dp).clip(RoundedCornerShape(50))
            .background(if (selected) Look.p.c1 else Glass)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp)
    ) { Text(text, fontSize = 13.sp, color = if (selected) Color.Black else Color.White, fontWeight = FontWeight.SemiBold) }
}

@Composable
fun SectionTitle(t: String) {
    Text(t, color = Look.p.c3, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(top = 20.dp, bottom = 6.dp))
}

@Composable
fun EqualizerScreen(onClose: () -> Unit) {
    val fx = AudioFx
    Box(Modifier.fillMaxSize().background(Look.bg()).clickable(enabled = false) {}) {
        Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Filled.ArrowBack, "Back") }
                Text("Equalizer", fontSize = 22.sp, fontWeight = FontWeight.Black, color = Gold, modifier = Modifier.weight(1f))
                Switch(checked = fx.enabled, onCheckedChange = { fx.enabled = it; fx.persist(); fx.applyAll() })
            }
            if (!fx.supported) {
                Text("This device does not support audio effects.", color = Pink)
            } else if (fx.bandCount == 0) {
                Text("Play any song once, then the equalizer bands appear here.", color = Color.White.copy(alpha = 0.7f))
            }

            SectionTitle("PRESETS")
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                fx.presetNames.forEach { n -> MkChip(n, fx.presetName == n) { fx.applyPreset(n) } }
            }

            if (fx.bandCount > 0) {
                SectionTitle("BANDS (${fx.bandCount} bands provided by this device)")
                for (i in 0 until fx.bandCount) {
                    val f = fx.bandFreqs.getOrElse(i) { 0 }
                    val label = if (f >= 1000) "%.1f kHz".format(f / 1000f) else "$f Hz"
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(label, fontSize = 12.sp, modifier = Modifier.width(64.dp))
                        Slider(
                            value = fx.bandLevels.getOrElse(i) { 0 }.toFloat(),
                            onValueChange = { fx.setBand(i, it.toInt()) },
                            onValueChangeFinished = { fx.persist() },
                            valueRange = fx.minLevel.toFloat()..fx.maxLevel.toFloat(),
                            colors = SliderDefaults.colors(thumbColor = Gold, activeTrackColor = Look.p.c2),
                            modifier = Modifier.weight(1f)
                        )
                        Text("${fx.bandLevels.getOrElse(i) { 0 } / 100} dB", fontSize = 12.sp, modifier = Modifier.width(48.dp))
                    }
                }
                TextButton(onClick = { fx.saveCustom() }) { Text("Save as my custom preset", color = Cyan) }
            }

            SectionTitle("BASS BOOST")
            Slider(
                value = fx.bassStrength.toFloat(), valueRange = 0f..1000f,
                onValueChange = { fx.bassStrength = it.toInt(); fx.applyAll() },
                onValueChangeFinished = { fx.persist() },
                colors = SliderDefaults.colors(thumbColor = Gold, activeTrackColor = Look.p.c2)
            )
            SectionTitle("VIRTUALIZER")
            Slider(
                value = fx.virtStrength.toFloat(), valueRange = 0f..1000f,
                onValueChange = { fx.virtStrength = it.toInt(); fx.applyAll() },
                onValueChangeFinished = { fx.persist() },
                colors = SliderDefaults.colors(thumbColor = Gold, activeTrackColor = Look.p.c2)
            )
            SectionTitle("TREBLE")
            Slider(
                value = fx.treble.toFloat(), valueRange = -1000f..1000f,
                onValueChange = { fx.treble = it.toInt(); fx.applyAll() },
                onValueChangeFinished = { fx.persist() },
                colors = SliderDefaults.colors(thumbColor = Gold, activeTrackColor = Look.p.c2)
            )
            SectionTitle("REVERB")
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                fx.reverbNames.forEachIndexed { i, n ->
                    MkChip(n, fx.reverbPreset == i) { fx.reverbPreset = i; fx.persist(); fx.applyAll() }
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}
