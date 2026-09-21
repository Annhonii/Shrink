package com.davexh.shrinky.ui

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp

@Immutable
class Palette(
    val bg: Color,
    val card: Color,
    val stroke: Color,
    val text: Color,
    val mute: Color,
    val dotOff: Color,
    val dotOn: Color,
    val primary: Color,
    val onPrimary: Color,
    val accent: Color,
    val field: Color,
    val fieldStroke: Color,
    val disabled: Color,
)

private val Signal = Color(0xFFD71921)

// Fallbacks for Android 11 and below (no Monet there).
val DarkPalette = Palette(
    bg = Color(0xFF000000), card = Color(0xFF131313), stroke = Color(0xFF262626),
    text = Color(0xFFFFFFFF), mute = Color(0xFF8C8C8C),
    dotOff = Color(0xFF2A2A2A), dotOn = Color(0xFFFFFFFF),
    primary = Color(0xFFFFFFFF), onPrimary = Color(0xFF000000), accent = Signal,
    field = Color(0xFF0A0A0A), fieldStroke = Color(0xFF3D3D3D), disabled = Color(0xFF2E2E2E),
)

val LightPalette = Palette(
    bg = Color(0xFFF1F1F1), card = Color(0xFFFFFFFF), stroke = Color(0xFFE3E3E3),
    text = Color(0xFF0A0A0A), mute = Color(0xFF6F6F6F),
    dotOff = Color(0xFFDADADA), dotOn = Color(0xFF0A0A0A),
    primary = Color(0xFF0A0A0A), onPrimary = Color(0xFFFFFFFF), accent = Signal,
    field = Color(0xFFF4F4F4), fieldStroke = Color(0xFFC4C4C4), disabled = Color(0xFFDCDCDC),
)

/** Page + accents come from the wallpaper (Monet); the cards stay black (white in light mode). */
@RequiresApi(31)
private fun monet(ctx: Context, dark: Boolean): Palette {
    fun c(id: Int) = Color(ctx.getColor(id))
    return if (dark) {
        Palette(
            bg = c(android.R.color.system_neutral1_900),
            card = Color(0xFF000000),
            stroke = c(android.R.color.system_neutral1_800),
            text = c(android.R.color.system_neutral1_50),
            mute = c(android.R.color.system_neutral1_400),
            dotOff = c(android.R.color.system_neutral1_800),
            dotOn = c(android.R.color.system_accent1_200),
            primary = c(android.R.color.system_accent1_200),
            onPrimary = c(android.R.color.system_accent1_900),
            accent = Signal,
            field = c(android.R.color.system_neutral1_900),
            fieldStroke = c(android.R.color.system_neutral1_600),
            disabled = c(android.R.color.system_neutral1_700),
        )
    } else {
        Palette(
            bg = c(android.R.color.system_neutral1_50),
            card = c(android.R.color.system_neutral1_0),
            stroke = c(android.R.color.system_neutral1_100),
            text = c(android.R.color.system_neutral1_900),
            mute = c(android.R.color.system_neutral1_600),
            dotOff = c(android.R.color.system_neutral1_200),
            dotOn = c(android.R.color.system_accent1_600),
            primary = c(android.R.color.system_accent1_600),
            onPrimary = c(android.R.color.system_accent1_0),
            accent = Signal,
            field = c(android.R.color.system_neutral1_50),
            fieldStroke = c(android.R.color.system_neutral1_300),
            disabled = c(android.R.color.system_neutral1_200),
        )
    }
}

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
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val p = remember(dark) {
        if (Build.VERSION.SDK_INT >= 31) monet(ctx, dark) else if (dark) DarkPalette else LightPalette
    }
    CompositionLocalProvider(LocalPalette provides p) {
        Box(Modifier.fillMaxSize().background(p.bg)) { content() }
    }
}
