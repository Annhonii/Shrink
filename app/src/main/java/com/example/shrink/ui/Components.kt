package com.example.shrink.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.roundToInt

// ---------- formatting ----------

fun formatSize(b: Long): String =
    if (b >= 1_048_576) String.format(Locale.US, "%.1f MB", b / 1_048_576.0)
    else "${(b / 1024).coerceAtLeast(1)} KB"

fun delta(before: Double, after: Double): String {
    if (before <= 0) return ""
    val d = ((after - before) / before * 100).roundToInt()
    return when {
        d > 0 -> "+$d%"
        d < 0 -> "\u2212${-d}%"
        else -> "no change"
    }
}

// ---------- layout ----------

@Composable
fun SectionCard(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(28.dp)
    Column(
        Modifier.fillMaxWidth()
            .background(p.card, shape)
            .border(1.dp, p.stroke, shape)
            .clip(shape)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (title != null) Text(title, style = MaterialTheme.typography.labelLarge, color = p.mute)
        content()
    }
}

@Composable
fun Divider() {
    val p = LocalPalette.current
    Box(Modifier.fillMaxWidth().height(1.dp).background(p.stroke))
}

// ---------- the memorable element: a dot grid that fills to show size ----------

@Composable
fun DotMeter(fraction: Float, cols: Int = 24, rows: Int = 7) {
    val p = LocalPalette.current
    Canvas(Modifier.fillMaxWidth().aspectRatio(cols.toFloat() / rows)) {
        val cell = size.width / cols
        val total = cols * rows
        val filled = (fraction * total).roundToInt().coerceIn(0, total)
        for (i in 0 until total) {
            val c = i / rows
            val r = i % rows
            val color = when {
                i == filled - 1 -> p.accent
                i < filled -> p.dotOn
                else -> p.dotOff
            }
            drawCircle(color, radius = cell * 0.27f, center = Offset(cell * (c + 0.5f), cell * (r + 0.5f)))
        }
    }
}

// ---------- controls ----------

enum class PillStyle { Filled, Outlined }

@Composable
fun PillButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: PillStyle = PillStyle.Filled,
    onClick: () -> Unit,
) {
    val p = LocalPalette.current
    val filled = style == PillStyle.Filled
    val bg = when { !filled -> Color.Transparent; enabled -> p.text; else -> p.disabled }
    val fg = when { !filled -> p.text; enabled -> p.bg; else -> p.mute }
    Box(
        modifier.fillMaxWidth().height(54.dp)
            .clip(CircleShape)
            .background(bg)
            .then(if (!filled) Modifier.border(1.dp, p.fieldStroke, CircleShape) else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = fg, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun Segmented(options: List<String>, selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    val p = LocalPalette.current
    Row(
        modifier.clip(CircleShape).background(p.field)
            .border(1.dp, p.fieldStroke, CircleShape).padding(4.dp),
    ) {
        options.forEachIndexed { i, label ->
            val sel = i == selected
            Box(
                Modifier.weight(1f).clip(CircleShape)
                    .background(if (sel) p.text else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = if (sel) p.bg else p.text, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** Looks like an input: filled well, visible border, placeholder, red ring while focused. */
@Composable
fun SizeInput(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val focus = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(18.dp)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.headlineSmall.copy(color = p.text),
        cursorBrush = SolidColor(p.accent),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
        modifier = modifier.onFocusChanged { focused = it.isFocused },
        decorationBox = { inner ->
            Box(
                Modifier.fillMaxWidth()
                    .background(p.field, shape)
                    .border(if (focused) 2.dp else 1.dp, if (focused) p.accent else p.fieldStroke, shape)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) {
                    Text("Enter size", style = MaterialTheme.typography.headlineSmall, color = p.mute)
                }
                inner()
            }
        },
    )
}

@Composable
fun WorkingDots() {
    val p = LocalPalette.current
    val t = rememberInfiniteTransition(label = "working")
    val pos by t.animateFloat(0f, 5f, infiniteRepeatable(tween(1000, easing = LinearEasing)), label = "pos")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(5) { i ->
            Box(Modifier.size(10.dp).clip(CircleShape).background(if (pos.toInt() == i) p.accent else p.dotOff))
        }
    }
}

// ---------- result ----------

@Composable
fun StatRow(label: String, from: String, to: String? = null, delta: String? = null) {
    val p = LocalPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = p.mute, style = MaterialTheme.typography.bodyMedium)
            if (!delta.isNullOrEmpty()) Text(delta, color = p.accent, style = MaterialTheme.typography.labelLarge)
        }
        Text(
            if (to == null) from else "$from  \u2192  $to",
            color = p.text, style = MaterialTheme.typography.titleMedium,
        )
    }
}

/** Drag the handle to reveal Before (left) against After (right). */
@Composable
fun CompareView(before: ImageBitmap, after: ImageBitmap) {
    val p = LocalPalette.current
    var frac by remember { mutableFloatStateOf(0.5f) }
    Box(
        Modifier.fillMaxWidth()
            .aspectRatio((before.width.toFloat() / before.height).coerceIn(0.6f, 1.8f))
            .clip(RoundedCornerShape(20.dp))
            .background(p.field)
            .pointerInput(Unit) { detectTapGestures { frac = (it.x / size.width).coerceIn(0f, 1f) } }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { frac = (it.x / size.width).coerceIn(0f, 1f) },
                ) { change, _ -> frac = (change.position.x / size.width).coerceIn(0f, 1f) }
            },
    ) {
        Image(after, "After", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Image(
            before, "Before",
            Modifier.fillMaxSize().drawWithContent {
                clipRect(right = size.width * frac) { this@drawWithContent.drawContent() }
            },
            contentScale = ContentScale.Crop,
        )
        Canvas(Modifier.fillMaxSize()) {
            val x = size.width * frac
            drawLine(Color.White, Offset(x, 0f), Offset(x, size.height), strokeWidth = 2.dp.toPx())
            drawCircle(Color.White, 15.dp.toPx(), Offset(x, size.height / 2))
            drawCircle(p.accent, 4.dp.toPx(), Offset(x, size.height / 2))
        }
        Tag("Before", Modifier.align(Alignment.TopStart).padding(10.dp))
        Tag("After", Modifier.align(Alignment.TopEnd).padding(10.dp))
    }
}

@Composable
private fun Tag(text: String, modifier: Modifier) {
    Text(
        text,
        modifier.clip(CircleShape).background(Color(0x99000000)).padding(horizontal = 10.dp, vertical = 4.dp),
        color = Color.White, style = MaterialTheme.typography.labelMedium,
    )
}
