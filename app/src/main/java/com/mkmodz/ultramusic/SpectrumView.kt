package com.mkmodz.ultramusic

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke

@Composable
fun SpectrumView(bars: FloatArray, style: Int, modifier: Modifier = Modifier) {
    val p = Look.p
    Canvas(modifier) {
        val n = bars.size
        val w = size.width
        val h = size.height
        val slot = w / n
        val bw = slot * 0.62f
        val vertical = Brush.verticalGradient(listOf(p.c3, p.c2, p.c1))
        when (style) {
            0 -> for (i in 0 until n) {
                val bh = (bars[i] * h).coerceAtLeast(4f)
                drawRoundRect(
                    brush = vertical,
                    topLeft = Offset(i * slot + (slot - bw) / 2, h - bh),
                    size = Size(bw, bh),
                    cornerRadius = CornerRadius(bw / 2)
                )
            }
            1 -> for (i in 0 until n) {
                val bh = (bars[i] * h / 2f).coerceAtLeast(3f)
                drawRoundRect(
                    brush = vertical,
                    topLeft = Offset(i * slot + (slot - bw) / 2, h / 2 - bh),
                    size = Size(bw, bh * 2),
                    cornerRadius = CornerRadius(bw / 2)
                )
            }
            else -> {
                val path = Path()
                for (i in 0 until n) {
                    val x = i * slot + slot / 2
                    val y = h - (bars[i] * h).coerceAtLeast(4f)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(
                    path,
                    brush = Brush.horizontalGradient(listOf(p.c1, p.c2, p.c3)),
                    style = Stroke(width = 5f, cap = StrokeCap.Round)
                )
                for (i in 0 until n) {
                    val x = i * slot + slot / 2
                    val y = h - (bars[i] * h).coerceAtLeast(4f)
                    drawCircle(p.c3, radius = 4f, center = Offset(x, y))
                }
            }
        }
    }
}
