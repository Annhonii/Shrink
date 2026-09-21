package com.example.shrink.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.shrink.CropVm
import com.example.shrink.engine.OutFormat

/** The crop tool: type a size, a frame of that shape appears, move/zoom the photo under it. */
@Composable
fun CropScreen(vm: CropVm, pickFolder: () -> Unit) {
    val p = LocalPalette.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { vm.pick(it) }
    val src = vm.src
    val result = vm.result
    val w = vm.wText.toIntOrNull() ?: 0
    val h = vm.hText.toIntOrNull() ?: 0

    ToolScaffold(
        scrollTo = result,
        bar = {
            ActionBar(
                busy = vm.busy, hasResult = result != null, savedAs = vm.save.savedAs,
                primary = "Crop", primaryEnabled = src != null && w > 0 && h > 0,
                onPrimary = vm::crop, onSave = vm::saveResult,
            )
        },
    ) {
        SectionCard("Photo") {
            if (src != null) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(src.name, style = Type.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${src.origW} \u00d7 ${src.origH}", color = p.mute)
                }
            }
            PillButton(
                if (src == null) "Choose photo" else "Change photo",
                style = if (src == null) PillStyle.Filled else PillStyle.Outlined,
            ) { picker.launch(arrayOf("image/*")) }
        }

        SectionCard("Crop size") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Field(vm.wText, vm::onW, "Width", Modifier.weight(1f), keyboard = KeyboardType.Number, suffix = "px")
                Text("\u00d7", color = p.mute, style = Type.title)
                Field(vm.hText, vm::onH, "Height", Modifier.weight(1f), keyboard = KeyboardType.Number, suffix = "px")
            }
        }

        if (src != null) {
            SectionCard("Select area") { CropEditor(vm.editor) }
            SectionCard("Output format") {
                Segmented(OutFormat.entries.map { it.label }, vm.format.ordinal) { vm.onFormat(OutFormat.entries[it]) }
            }
        }

        StatusCards(vm.busy, vm.failure)

        if (result != null) {
            SectionCard("Result") {
                Image(
                    remember(result.preview) { result.preview.asImageBitmap() }, "Cropped photo",
                    Modifier.fillMaxWidth()
                        .aspectRatio((result.w.toFloat() / result.h).coerceIn(0.3f, 3f))
                        .clip(RoundedCornerShape(20.dp)),
                    contentScale = ContentScale.Fit,
                )
                if (src != null) {
                    StatRow("Resolution", "${src.origW} \u00d7 ${src.origH}", "${result.w} \u00d7 ${result.h}")
                }
                StatRow("File size", formatSize(result.bytes.size.toLong()))
            }
            SaveCard(vm.save.name, vm.save::onName, result.ext, pickFolder)
        }
    }
}
