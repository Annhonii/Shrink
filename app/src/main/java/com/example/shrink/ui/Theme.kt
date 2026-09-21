package com.example.shrink.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp

/** Nothing-style palette: monochrome surfaces with a single red signal. */
@Immutable
class Palette(
    val bg: Color,
    val card: Color,
    val stroke: Color,
    val text: Color,
    val mute: Color,
    val dotOff: Color,
    val dotOn: Color,
    val accent: Color,
    val field: Color,
    val fieldStroke: Color,
    val disabled: Color,
)

val DarkPalette = Palette(
    bg = Color(0xFF000000), card = Color(0xFF131313), stroke = Color(0xFF262626),
    text = Color(0xFFFFFFFF), mute = Color(0xFF8C8C8C),
    dotOff = Color(0xFF2A2A2A), dotOn = Color(0xFFFFFFFF), accent = Color(0xFFD71921),
    field = Color(0xFF0A0A0A), fieldStroke = Color(0xFF3D3D3D), disabled = Color(0xFF2E2E2E),
)

val LightPalette = Palette(
    bg = Color(0xFFF1F1F1), card = Color(0xFFFFFFFF), stroke = Color(0xFFE3E3E3),
    text = Color(0xFF0A0A0A), mute = Color(0xFF6F6F6F),
    dotOff = Color(0xFFDADADA), dotOn = Color(0xFF0A0A0A), accent = Color(0xFFD71921),
    field = Color(0xFFF4F4F4), fieldStroke = Color(0xFFC4C4C4), disabled = Color(0xFFDCDCDC),
)

val LocalPalette = staticCompositionLocalOf { DarkPalette }

/** System font, a handful of sizes. */
object Type {
    val headline = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Medium)
    val input = TextStyle(fontSize = 22.sp, lineHeight = 28.sp)
    val title = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium)
    val label = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
    val body = TextStyle(fontSize = 14.sp, lineHeight = 20.sp)
    val small = TextStyle(fontSize = 12.sp, lineHeight = 16.sp)
}

/** Tiny replacement for Material's Text, so the app doesn't need the Material library. */
@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = LocalPalette.current.text,
    style: TextStyle = Type.body,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    textAlign: TextAlign = TextAlign.Unspecified,
) {
    BasicText(text, modifier, style.copy(color = color, textAlign = textAlign), overflow = overflow, maxLines = maxLines)
}

@Composable
fun ShrinkyTheme(content: @Composable () -> Unit) {
    val p = if (isSystemInDarkTheme()) DarkPalette else LightPalette
    CompositionLocalProvider(LocalPalette provides p) {
        Box(Modifier.fillMaxSize().background(p.bg)) { content() }
    }
}
