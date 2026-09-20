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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.shrink.Phase
import com.example.shrink.ShrinkViewModel
import com.example.shrink.engine.Kind
import com.example.shrink.engine.OutFormat

@Composable
fun HomeScreen(vm: ShrinkViewModel) {
    val p = LocalPalette.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { vm.pick(it) }
    val src = vm.source
    val phase = vm.phase
    val target = vm.targetBytes
    val done = phase as? Phase.Done
    val result = done?.shrunk
    val scroll = rememberScrollState()

    val shown = result?.bytes?.size?.toLong() ?: target
    val fraction = if (src != null && src.bytes > 0) shown.toFloat() / src.bytes else 0f
    val meter by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(500), label = "meter")

    // Bring the result into view when a new one lands.
    LaunchedEffect(result) {
        if (result != null) {
            withFrameNanos { }
            scroll.animateScrollTo(scroll.maxValue)
        }
    }

    Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
        Column(
            Modifier.weight(1f).verticalScroll(scroll)
                .padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(Modifier.padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Shrink", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, color = p.text)
                Spacer(Modifier.width(8.dp))
                Box(Modifier.size(9.dp).clip(CircleShape).background(p.accent))
            }

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
                        Text(src.name, color = p.text, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            (if (src.kind == Kind.PDF) "PDF" else "Photo") + ", " + formatSize(src.bytes),
                            color = p.mute, style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                } else {
                    Text("Pick a photo or PDF to get started.", color = p.mute, style = MaterialTheme.typography.bodyMedium)
                }
                PillButton(
                    if (src == null) "Choose file" else "Change file",
                    style = if (src == null) PillStyle.Filled else PillStyle.Outlined,
                ) { picker.launch(arrayOf("image/*", "application/pdf")) }
            }

            SectionCard("Target size") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SizeInput(vm.targetText, vm::onTargetText, Modifier.weight(1f))
                    Segmented(listOf("KB", "MB"), if (vm.unitMb) 1 else 0, Modifier.width(112.dp)) { vm.onUnit(it == 1) }
                }
                Text("Type any size you need, like 350 KB or 1.5 MB.", color = p.mute, style = MaterialTheme.typography.bodySmall)
            }

            if (src?.kind == Kind.IMAGE) {
                SectionCard("Output format") {
                    Segmented(OutFormat.entries.map { it.label }, vm.format.ordinal) { vm.onFormat(OutFormat.entries[it]) }
                    Text(
                        when (vm.format) {
                            OutFormat.JPG -> "Smallest files. Transparent areas turn white."
                            OutFormat.PNG -> "Lossless, so the only way to shrink is lowering resolution. Keeps transparency."
                            OutFormat.WEBP -> "Small files with good quality. Keeps transparency."
                        },
                        color = p.mute, style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            when (phase) {
                Phase.Idle -> Unit
                Phase.Working -> SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        WorkingDots()
                        Text("Shrinking your file", color = p.mute, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                is Phase.Failed -> SectionCard { Text(phase.msg, color = p.accent, style = MaterialTheme.typography.bodyMedium) }
                is Phase.Done -> {
                    val s = phase.shrunk
                    val before = s.before
                    val after = s.after
                    if (before != null && after != null) {
                        SectionCard("Preview") {
                            CompareView(before.asImageBitmap(), after.asImageBitmap())
                            Text("Drag the handle to compare.", color = p.mute, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    SectionCard("What changed") {
                        when {
                            s.unchanged -> Text("Already under your target, so the file was left as is.", color = p.mute, style = MaterialTheme.typography.bodyMedium)
                            !s.hitTarget -> Text("This is as small as it gets without breaking the file. Try a bigger target.", color = p.accent, style = MaterialTheme.typography.bodyMedium)
                        }
                        if (src != null) {
                            StatRow("File size", formatSize(src.bytes), formatSize(s.bytes.size.toLong()), delta(src.bytes.toDouble(), s.bytes.size.toDouble()))
                        }
                        if (s.origW > 0) {
                            Divider()
                            val d = delta(s.origW.toDouble() * s.origH, s.newW.toDouble() * s.newH)
                            StatRow(
                                "Resolution", "${s.origW} \u00d7 ${s.origH}", "${s.newW} \u00d7 ${s.newH}",
                                if (d == "no change" || d.isEmpty()) d else "$d pixels",
                            )
                        }
                        if (s.rasterized) {
                            Divider()
                            StatRow("Pages", "${s.pages}")
                            StatRow("Page quality", "${s.dpi} dpi")
                            Text("Pages are saved as images, so text can't be selected.", color = p.mute, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        // Sticky action bar: one primary action that follows the flow.
        Column(
            Modifier.fillMaxWidth().background(p.bg).padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when {
                done?.savedAs != null -> {
                    PillButton("Saved to Downloads", enabled = false, style = PillStyle.Outlined) {}
                    Text(
                        "Downloads/Shrink/${done.savedAs}",
                        Modifier.fillMaxWidth(), color = p.mute, style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                done != null -> PillButton("Save to Downloads") { vm.save() }
                phase == Phase.Working -> PillButton("Shrinking...", enabled = false) {}
                else -> PillButton("Shrink", enabled = src != null && target >= 5 * 1024) { vm.shrink() }
            }
        }
    }
}

@Composable
private fun Readout(label: String, value: String, alignEnd: Boolean = false, warn: Boolean = false) {
    val p = LocalPalette.current
    Column(horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
        Text(label, color = p.mute, style = MaterialTheme.typography.bodyMedium)
        Text(
            value, color = if (warn) p.accent else p.text,
            style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Medium,
        )
    }
}
