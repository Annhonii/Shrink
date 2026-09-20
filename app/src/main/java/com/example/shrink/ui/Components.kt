package com.example.shrink.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.animateFloat
import java.util.Locale
import kotlin.math.roundToInt

fun formatSize(b: Long): String =
    if (b >= 1_048_576) String.format(Locale.US, "%.1f MB", b / 1_048_576.0)
    else "${(b / 1024).coerceAtLeast(1)} KB"

/** The one memorable element: a dot grid that fills to show target size vs. original. */
@Composable
fun DotMeter(fraction: Float, cols: Int = 24, rows: Int = 8) {
    Canvas(Modifier.fillMaxWidth().aspectRatio(cols.toFloat() / rows)) {
        val cell = size.width / cols
        val total = cols * rows
        val filled = (fraction * total).roundToInt().coerceIn(0, total)
        for (i in 0 until total) {
            val c = i / rows
            val r = i % rows
            val color = when {
                i == filled - 1 -> Ink.Red
                i < filled -> Ink.White
                else -> Ink.Dot
            }
            drawCircle(color, radius = cell * 0.26f, center = Offset(cell * (c + 0.5f), cell * (r + 0.5f)))
        }
    }
}

@Composable
fun PillButton(text: String, enabled: Boolean = true, filled: Boolean = true, onClick: () -> Unit) {
    val bg = if (!filled) Color.Transparent else if (enabled) Ink.White else Ink.Dim
    val fg = if (filled) Ink.Black else Ink.White
    Box(
        Modifier.fillMaxWidth().height(56.dp).clip(CircleShape).background(bg)
            .then(if (!filled) Modifier.border(1.dp, Ink.Line, CircleShape) else Modifier)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = fg, fontFamily = DotFont, fontSize = 16.sp) }
}

@Composable
fun UnitToggle(mb: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.clip(CircleShape).border(1.dp, Ink.Line, CircleShape)) {
        listOf("KB" to false, "MB" to true).forEach { (label, isMb) ->
            val selected = mb == isMb
            Box(
                Modifier.clip(CircleShape)
                    .background(if (selected) Ink.White else Color.Transparent)
                    .clickable { onChange(isMb) }
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            ) { Text(label, color = if (selected) Ink.Black else Ink.White, fontFamily = DotFont) }
        }
    }
}

@Composable
fun Chip(text: String, onClick: () -> Unit) {
    Box(
        Modifier.clip(CircleShape).border(1.dp, Ink.Line, CircleShape)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
    ) { Text(text, color = Ink.White, fontFamily = DotFont, fontSize = 13.sp) }
}

@Composable
fun WorkingDots() {
    val t = rememberInfiniteTransition(label = "working")
    val pos by t.animateFloat(0f, 5f, infiniteRepeatable(tween(1000, easing = LinearEasing)), label = "pos")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(5) { i ->
            Box(Modifier.size(10.dp).clip(CircleShape).background(if (pos.toInt() == i) Ink.Red else Ink.Dot))
        }
    }
}
