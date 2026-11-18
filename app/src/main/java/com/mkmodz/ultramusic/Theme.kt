package com.mkmodz.ultramusic

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

val Gold = Color(0xFFFFC857)
val Purple = Color(0xFF9B5CFF)
val Cyan = Color(0xFF3DE8FF)
val Pink = Color(0xFFFF4DA6)
val Glass = Color(0x22FFFFFF)

data class Palette(val name: String, val top: Color, val bottom: Color, val c1: Color, val c2: Color, val c3: Color)

object Look {
    val palettes = listOf(
        Palette("Royal Gold", Color(0xFF1A0F33), Color(0xFF05030C), Purple, Pink, Gold),
        Palette("Neon Cyber", Color(0xFF021B2B), Color(0xFF02050A), Cyan, Purple, Pink),
        Palette("Rose Galaxy", Color(0xFF3A0A2E), Color(0xFF0A0210), Pink, Purple, Gold),
        Palette("Aurora", Color(0xFF062B24), Color(0xFF030A0C), Cyan, Color(0xFF4DFF9B), Gold),
        Palette("Midnight Gold", Color(0xFF14110A), Color(0xFF000000), Gold, Color(0xFFFFE08A), Color(0xFFFF9F43))
    )

    var themeIndex by mutableIntStateOf(0)
    var bgMode by mutableIntStateOf(0)          // 0 = blurred artwork, 1 = gradient only, 2 = my image
    var globalBg by mutableStateOf<String?>(null)
    var vizStyle by mutableIntStateOf(0)         // 0 bars, 1 mirror, 2 wave

    val p: Palette get() = palettes[themeIndex.coerceIn(0, palettes.size - 1)]

    fun bg(): Brush = Brush.verticalGradient(listOf(p.top, p.bottom))
    fun accent(): Brush = Brush.horizontalGradient(listOf(p.c1, p.c2, p.c3))

    fun load() {
        val s = Prefs.sp
        themeIndex = s.getInt("theme", 0)
        bgMode = s.getInt("bgmode", 0)
        globalBg = s.getString("global_bg", null)
        vizStyle = s.getInt("viz", 0)
    }

    fun save() {
        Prefs.sp.edit()
            .putInt("theme", themeIndex)
            .putInt("bgmode", bgMode)
            .putString("global_bg", globalBg)
            .putInt("viz", vizStyle)
            .apply()
    }
}

@Composable
fun MkTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Gold,
            secondary = Cyan,
            tertiary = Pink,
            background = Color(0xFF05030C),
            surface = Color(0xFF120B22),
            onPrimary = Color.Black
        ),
        content = content
    )
}
