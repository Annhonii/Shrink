package com.example.shrink.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.shrink.ShrinkVm
import com.example.shrink.engine.Kind
import com.example.shrink.engine.OutFormat

/** The compress tool. */
@Composable
fun ShrinkScreen(vm: ShrinkVm, pickFolder: () -> Unit) {
    val p = LocalPalette.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { vm.pick(it) }
    val src = vm.source
    val result = vm.result
    val target = vm.targetBytes

    val shown = result?.bytes?.size?.toLong() ?: target
    val fraction = if (src != null && src.bytes > 0) shown.toFloat() / src.bytes else 0f
    val meter by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(500), label = "meter")

    ToolScaffold(
        scrollTo = result,
        bar = {
            ActionBar(
                busy = vm.busy, hasResult = result != null, savedAs = vm.save.savedAs,
                primary = "Shrink", primaryEnabled = src != null && target >= 5 * 1024,
                onPrimary = vm::shrink, onSave = vm::saveResult,
            )
        },
    ) {
        SectionCard {
            DotMeter(meter)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Readout("Original", src?.let { formatSize(it.bytes) } ?: "--")
                if (result != null) {
                    Readout("Result", formatSize(result.bytes.size.toLong()), alignEnd = true, warn = !result.hitTarget)
                } else {
                    Readout("Target", if (target > 0) formatSize(target) else "--", alignEnd = true)
                }
            }
        }

        SectionCard("File") {
            if (src != null) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(src.name, style = Type.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text((if (src.kind == Kind.PDF) "PDF" else "Photo") + ", " + formatSize(src.bytes), color = p.mute)
                }
            }
            PillButton(
                if (src == null) "Choose file" else "Change file",
                style = if (src == null) PillStyle.Filled else PillStyle.Outlined,
            ) { picker.launch(arrayOf("image/*", "application/pdf")) }
        }

        SectionCard("Target size") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Field(vm.targetText, vm::onTargetText, "Size", Modifier.weight(1f), keyboard = KeyboardType.Decimal)
                Segmented(listOf("KB", "MB"), if (vm.unitMb) 1 else 0, Modifier.width(112.dp)) { vm.onUnit(it == 1) }
            }
        }

        if (src?.kind == Kind.IMAGE) {
            SectionCard("Output format") {
                Segmented(OutFormat.entries.map { it.label }, vm.format.ordinal) { vm.onFormat(OutFormat.entries[it]) }
            }
        }

        StatusCards(vm.busy, vm.failure)

        if (result != null) {
            val before = result.before
            val after = result.after
            if (before != null && after != null) {
                SectionCard("Preview") {
                    CompareView(remember(before) { before.asImageBitmap() }, remember(after) { after.asImageBitmap() })
                }
            }
            SectionCard("What changed") {
                when {
                    result.unchanged -> Text("Already under your target, so the file was left as is.", color = p.mute)
                    !result.hitTarget -> Text("This is as small as it gets without breaking the file. Try a bigger target.", color = p.accent)
                }
                if (src != null) {
                    StatRow(
                        "File size", formatSize(src.bytes), formatSize(result.bytes.size.toLong()),
                        delta(src.bytes.toDouble(), result.bytes.size.toDouble()),
                    )
                }
                if (result.origW > 0) {
                    Rule()
                    val d = delta(result.origW.toDouble() * result.origH, result.newW.toDouble() * result.newH)
                    StatRow(
                        "Resolution", "${result.origW} \u00d7 ${result.origH}", "${result.newW} \u00d7 ${result.newH}",
                        if (d == "no change" || d.isEmpty()) d else "$d pixels",
                    )
                }
                if (result.pages > 0) {
                    Rule()
                    StatRow("Pages", "${result.pages}, saved as images")
                    StatRow("Page quality", "${result.dpi} dpi")
                }
            }
            SaveCard(vm.save.name, vm.save::onName, result.ext, pickFolder)
        }
    }
}

@Composable
private fun Readout(label: String, value: String, alignEnd: Boolean = false, warn: Boolean = false) {
    val p = LocalPalette.current
    Column(horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
        Text(label, color = p.mute)
        Text(value, color = if (warn) p.accent else p.text, style = Type.headline)
    }
}
