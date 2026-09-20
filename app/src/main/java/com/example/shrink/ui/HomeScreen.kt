package com.example.shrink.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.shrink.Phase
import com.example.shrink.ShrinkViewModel

@Composable
fun HomeScreen(vm: ShrinkViewModel) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { vm.pick(it) }
    val src = vm.source
    val target = vm.targetBytes
    val phase = vm.phase
    val fraction = if (src != null && src.bytes > 0) target.toFloat() / src.bytes else 0f
    val meter by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(500), label = "meter")

    Column(
        Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Shrink", fontFamily = DotFont, fontSize = 28.sp, color = Ink.White)
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(8.dp).clip(CircleShape).background(Ink.Red))
        }

        DotMeter(meter)

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Readout("Now", src?.let { formatSize(it.bytes) } ?: "--")
            Readout("Target", if (target > 0) formatSize(target) else "--", alignEnd = true)
        }

        if (src != null) {
            Text(src.name, color = Ink.Mute, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        PillButton(
            if (src == null) "Choose photo or PDF" else "Change file",
            filled = src == null,
        ) { picker.launch(arrayOf("image/*", "application/pdf")) }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = vm.targetText,
                onValueChange = { v -> vm.targetText = v.filter { it.isDigit() || it == '.' }.take(6) },
                textStyle = TextStyle(color = Ink.White, fontFamily = DotFont, fontSize = 40.sp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                cursorBrush = SolidColor(Ink.Red),
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            UnitToggle(vm.unitMb) { vm.unitMb = it }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("100 KB") { vm.setTarget("100", false) }
            Chip("200 KB") { vm.setTarget("200", false) }
            Chip("500 KB") { vm.setTarget("500", false) }
            Chip("1 MB") { vm.setTarget("1", true) }
        }

        PillButton(
            "Shrink",
            enabled = src != null && target >= 5 * 1024 && phase != Phase.Working,
        ) { vm.shrink() }

        when (phase) {
            Phase.Idle -> Unit
            Phase.Working -> WorkingDots()
            is Phase.Failed -> Text(phase.msg, color = Ink.Red)
            is Phase.Done -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(formatSize(phase.shrunk.bytes.size.toLong()), fontFamily = DotFont, fontSize = 44.sp, color = Ink.White)
                if (phase.shrunk.hitTarget) {
                    Text("Under your target.", color = Ink.Mute)
                } else {
                    Text("This is as small as it gets without breaking the file. Try a bigger target.", color = Ink.Red)
                }
                if (phase.shrunk.rasterized) {
                    Text("Pages are saved as images, so text can't be selected.", color = Ink.Mute)
                }
                PillButton(
                    if (phase.savedAs == null) "Save to Downloads" else "Saved: ${phase.savedAs}",
                    enabled = phase.savedAs == null,
                ) { vm.save() }
            }
        }
    }
}

@Composable
private fun Readout(label: String, value: String, alignEnd: Boolean = false) {
    Column(horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
        Text(label, color = Ink.Mute, fontSize = 13.sp)
        Text(value, color = Ink.White, fontFamily = DotFont, fontSize = 28.sp)
    }
}
