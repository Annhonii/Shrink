package com.davexh.shrinky.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.davexh.shrinky.engine.Saver
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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

/** Keeps the last non-null value so exit animations still have something to draw. */
class Holder<T>(var value: T? = null)

@Composable
fun <T : Any> rememberLast(value: T?): T? {
    val h = remember { Holder<T>() }
    if (value != null) h.value = value
    return h.value
}

// ---------- motion ----------

private val Bouncy = spring<Float>(dampingRatio = 0.5f, stiffness = 500f)

/** Press feedback: a springy squish. Must be the OUTERMOST modifier so the whole element scales. */
@Composable
fun Modifier.tap(enabled: Boolean = true, action: () -> Unit): Modifier {
    var pressed by remember { mutableStateOf(false) }
    val current by rememberUpdatedState(action)
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, Bouncy, label = "tap")
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (pressed) 0.85f else 1f }
        .semantics(mergeDescendants = true) { role = Role.Button; onClick { current(); true } }
        .pointerInput(enabled) {
            if (enabled) {
                detectTapGestures(
                    onPress = { pressed = true; tryAwaitRelease(); pressed = false },
                    onTap = { current() },
                )
            }
        }
}

/** Springy expand + fade for sections that come and go inside the card. */
@Composable
fun Reveal(visible: Boolean, content: @Composable ColumnScope.() -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(spring(stiffness = 300f)) + expandVertically(spring(dampingRatio = 0.72f, stiffness = 320f)),
        exit = fadeOut(spring(stiffness = 500f)) + shrinkVertically(spring(stiffness = 500f)),
    ) { Column(content = content) }
}

// ---------- layout ----------

/** One card per tool. Sections inside are separated by Rule(). */
@Composable
fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(28.dp)
    Column(
        Modifier.fillMaxWidth()
            .background(p.card, shape)
            .border(1.dp, p.stroke, shape)
            .clip(shape)
            .padding(20.dp),
        content = content,
    )
}

@Composable
fun Section(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (title != null) Text(title, color = p.mute, style = Type.label)
        content()
    }
}

@Composable
fun Rule() {
    val p = LocalPalette.current
    Box(Modifier.padding(vertical = 16.dp).fillMaxWidth().height(1.dp).background(p.stroke))
}

/** Scrolling body + sticky action bar. Scrolls to the end once [scrollTo] becomes non-null/changes. */
@Composable
fun ToolScaffold(
    scrollTo: Any?,
    bar: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val p = LocalPalette.current
    val scroll = rememberScrollState()
    LaunchedEffect(scrollTo) {
        if (scrollTo != null) {
            delay(320) // let the result section finish expanding
            scroll.animateScrollTo(scroll.maxValue, spring(dampingRatio = 0.85f, stiffness = 200f))
        }
    }
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(scroll)
                .padding(horizontal = 20.dp).padding(top = 4.dp, bottom = 16.dp),
            content = content,
        )
        Column(
            Modifier.fillMaxWidth().background(p.bg)
                .padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 12.dp),
            content = bar,
        )
    }
}

private enum class Bar { Busy, Saved, Save, Primary }

/** One primary action that follows the flow: do the thing, then Save, then Saved. */
@Composable
fun ActionBar(
    busy: Boolean,
    hasResult: Boolean,
    savedAs: String?,
    primary: String,
    primaryEnabled: Boolean,
    onPrimary: () -> Unit,
    onSave: () -> Unit,
) {
    val p = LocalPalette.current
    val state = when {
        busy -> Bar.Busy
        savedAs != null -> Bar.Saved
        hasResult -> Bar.Save
        else -> Bar.Primary
    }
    AnimatedContent(
        targetState = state,
        modifier = Modifier.fillMaxWidth(),
        transitionSpec = {
            (fadeIn(spring(stiffness = 300f)) + scaleIn(spring(dampingRatio = 0.6f, stiffness = 400f), initialScale = 0.9f)) togetherWith
                fadeOut(spring(stiffness = 600f))
        },
        label = "action-bar",
    ) { s ->
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (s) {
                Bar.Busy -> PillButton("Working...", enabled = false) {}
                Bar.Saved -> {
                    PillButton("Saved", enabled = false, style = PillStyle.Outlined) {}
                    Text(
                        savedAs.orEmpty(), Modifier.fillMaxWidth(), color = p.mute, style = Type.small,
                        textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Bar.Save -> PillButton("Save") { onSave() }
                Bar.Primary -> PillButton(primary, enabled = primaryEnabled) { onPrimary() }
            }
        }
    }
}

@Composable
fun StatusSection(busy: Boolean, failure: String?) {
    val p = LocalPalette.current
    Reveal(busy || failure != null) {
        Rule()
        if (busy) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                WorkingDots()
                Text("Working", color = p.mute)
            }
        } else if (failure != null) {
            Text(failure, color = p.accent)
        }
    }
}

@Composable
fun SaveSection(name: String, onName: (String) -> Unit, ext: String, pickFolder: () -> Unit) {
    Section("Save as") {
        Field(name, onName, "File name", suffix = ".$ext", style = Type.title)
        PickerRow(Saver.label, "Change", pickFolder)
    }
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
    val bg by animateColorAsState(
        when { !filled -> Color.Transparent; enabled -> p.primary; else -> p.disabled },
        spring(stiffness = 400f), label = "pill-bg",
    )
    val fg = when { !filled -> p.text; enabled -> p.onPrimary; else -> p.mute }
    Box(
        modifier.fillMaxWidth().height(54.dp)
            .tap(enabled) { onClick() }
            .clip(CircleShape)
            .background(bg)
            .then(if (!filled) Modifier.border(1.dp, p.fieldStroke, CircleShape) else Modifier)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = fg, style = Type.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * iOS-style segmented control. [position] is fractional (0f..n-1), so the thumb can follow a
 * finger or a pager mid-swipe. Drag anywhere on the bar to slide the thumb.
 */
@Composable
fun SlidingSegments(
    labels: List<String>,
    position: Float,
    modifier: Modifier = Modifier,
    height: Dp = 44.dp,
    onTap: (Int) -> Unit,
    onDrag: ((Float) -> Unit)? = null,
    onRelease: (() -> Unit)? = null,
) {
    val p = LocalPalette.current
    val density = LocalDensity.current
    var widthPx by remember { mutableIntStateOf(0) }
    val padPx = with(density) { 4.dp.toPx() }
    val n = labels.size
    val segPx = if (widthPx > 0) (widthPx - 2 * padPx) / n else 0f
    val segDp = with(density) { segPx.toDp() }
    val thumbX = (position * segPx).roundToInt()
    val totalPx = (segPx * n).roundToInt()

    Box(
        modifier.fillMaxWidth().height(height)
            .clip(CircleShape)
            .background(p.field)
            .border(1.dp, p.fieldStroke, CircleShape)
            .onSizeChanged { widthPx = it.width }
            .pointerInput(n, segPx) {
                detectTapGestures { o ->
                    if (segPx > 0f) onTap(((o.x - padPx) / segPx).toInt().coerceIn(0, n - 1))
                }
            }
            .then(
                if (onDrag != null) {
                    Modifier.pointerInput(n, segPx) {
                        detectHorizontalDragGestures(
                            onDragEnd = { onRelease?.invoke() },
                            onDragCancel = { onRelease?.invoke() },
                        ) { change, amount ->
                            change.consume()
                            if (segPx > 0f) onDrag(amount / segPx)
                        }
                    }
                } else Modifier,
            ),
    ) {
        Row(Modifier.fillMaxSize().padding(4.dp)) {
            labels.forEach { l ->
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    Text(l, color = p.text, style = Type.label, maxLines = 1)
                }
            }
        }
        // The thumb, with a second copy of the labels (inverted) clipped inside it.
        Box(
            Modifier.padding(4.dp).fillMaxHeight().width(segDp)
                .offset { IntOffset(thumbX, 0) }
                .clip(CircleShape)
                .background(p.primary),
        ) {
            Row(
                Modifier.layout { measurable, constraints ->
                    val placeable = measurable.measure(Constraints.fixed(totalPx, constraints.maxHeight))
                    layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(-thumbX, 0) }
                },
            ) {
                labels.forEach { l ->
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        Text(l, color = p.onPrimary, style = Type.label, maxLines = 1)
                    }
                }
            }
        }
    }
}

/** Segmented pill: tap a segment, or grab the thumb and slide it (it settles on the nearest segment with a bounce). */
@Composable
fun Segmented(options: List<String>, selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    val scope = rememberCoroutineScope()
    val spec = spring<Float>(dampingRatio = 0.65f, stiffness = 420f)
    val anim = remember { Animatable(selected.toFloat()) }
    var drag by remember { mutableStateOf<Float?>(null) }
    val last = options.size - 1
    LaunchedEffect(selected) { anim.animateTo(selected.toFloat(), spec) }

    SlidingSegments(
        labels = options,
        position = drag ?: anim.value,
        modifier = modifier,
        onTap = { onSelect(it) },
        onDrag = { f -> drag = ((drag ?: anim.value) + f).coerceIn(0f, last.toFloat()) },
        onRelease = {
            val d = drag
            if (d != null) {
                val idx = d.roundToInt().coerceIn(0, last)
                scope.launch { anim.snapTo(d); drag = null; anim.animateTo(idx.toFloat(), spec) }
                if (idx != selected) onSelect(idx)
            }
        },
    )
}

@Composable
fun ChipRow(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val p = LocalPalette.current
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed { i, l ->
            val sel = i == selected
            val bg by animateColorAsState(if (sel) p.primary else p.field, spring(stiffness = 500f), label = "chip")
            Box(
                Modifier.tap { onSelect(i) }
                    .clip(CircleShape)
                    .background(bg)
                    .border(1.dp, if (sel) Color.Transparent else p.fieldStroke, CircleShape)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) { Text(l, color = if (sel) p.onPrimary else p.text, style = Type.label) }
        }
    }
}

/** Looks like an input: filled well, visible border, placeholder, red ring that eases in on focus. */
@Composable
fun Field(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboard: KeyboardType = KeyboardType.Text,
    style: TextStyle = Type.input,
    suffix: String? = null,
) {
    val p = LocalPalette.current
    val focus = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    val ring by animateFloatAsState(if (focused) 1f else 0f, spring(stiffness = 500f), label = "ring")
    val shape = RoundedCornerShape(18.dp)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = style.copy(color = p.text),
        cursorBrush = SolidColor(p.accent),
        keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
        modifier = modifier.onFocusChanged { focused = it.isFocused },
        decorationBox = { inner ->
            Row(
                Modifier.fillMaxWidth()
                    .background(p.field, shape)
                    .border((1f + ring).dp, lerp(p.fieldStroke, p.accent, ring), shape)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text(placeholder, color = p.mute, style = style, maxLines = 1)
                    inner()
                }
                if (suffix != null) Text(suffix, color = p.mute, style = style, maxLines = 1)
            }
        },
    )
}

@Composable
fun PickerRow(text: String, action: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier.fillMaxWidth()
            .tap { onClick() }
            .background(p.field, shape)
            .border(1.dp, p.fieldStroke, shape)
            .clip(shape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.width(12.dp))
        Text(action, style = Type.label)
    }
}

@Composable
fun IconTap(label: String, enabled: Boolean = true, action: () -> Unit) {
    val p = LocalPalette.current
    Box(Modifier.size(40.dp).tap(enabled) { action() }.clip(CircleShape), contentAlignment = Alignment.Center) {
        Text(label, color = if (enabled) p.text else p.dotOff, style = Type.title)
    }
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

// ---------- results ----------

@Composable
fun StatRow(label: String, from: String, to: String? = null, delta: String? = null) {
    val p = LocalPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = p.mute)
            if (!delta.isNullOrEmpty()) Text(delta, color = p.accent, style = Type.label)
        }
        Text(if (to == null) from else "$from  \u2192  $to", style = Type.title)
    }
}

/** Drag to reveal Before (left) against After (right). */
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
                ) { change, _ ->
                    change.consume()
                    frac = (change.position.x / size.width).coerceIn(0f, 1f)
                }
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
        color = Color.White, style = Type.small,
    )
}
