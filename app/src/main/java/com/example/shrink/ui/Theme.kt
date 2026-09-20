package com.example.shrink.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

/** Nothing-style palette: pure black, white, one red signal. */
object Ink {
    val Black = Color(0xFF000000)
    val White = Color(0xFFFFFFFF)
    val Red = Color(0xFFD71921)
    val Dot = Color(0xFF2B2B2B)
    val Line = Color(0xFF3A3A3A)
    val Dim = Color(0xFF4A4A4A)
    val Mute = Color(0xFF8A8A8A)
}

/**
 * Swap for a dot-matrix face to get the full Nothing look:
 * drop Doto (OFL, Google Fonts) into res/font/doto.ttf and use FontFamily(Font(R.font.doto)).
 */
val DotFont: FontFamily = FontFamily.Monospace

@Composable
fun ShrinkTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Ink.Black, surface = Ink.Black,
            primary = Ink.White, onPrimary = Ink.Black,
            onBackground = Ink.White, onSurface = Ink.White,
        ),
    ) {
        Surface(color = Ink.Black, modifier = Modifier.fillMaxSize(), content = content)
    }
}
