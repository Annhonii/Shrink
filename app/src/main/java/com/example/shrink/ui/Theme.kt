package com.example.shrink.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

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

@Composable
fun ShrinkTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val p = if (dark) DarkPalette else LightPalette
    val scheme = if (dark) {
        darkColorScheme(background = p.bg, surface = p.bg, primary = p.text, onPrimary = p.bg, onBackground = p.text, onSurface = p.text)
    } else {
        lightColorScheme(background = p.bg, surface = p.bg, primary = p.text, onPrimary = p.bg, onBackground = p.text, onSurface = p.text)
    }
    CompositionLocalProvider(LocalPalette provides p) {
        // Default typography = system font.
        MaterialTheme(colorScheme = scheme) {
            Surface(color = p.bg, modifier = Modifier.fillMaxSize(), content = content)
        }
    }
}
