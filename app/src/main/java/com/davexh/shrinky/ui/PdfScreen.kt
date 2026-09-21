package com.davexh.shrinky.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.davexh.shrinky.PdfResult
import com.davexh.shrinky.PdfVm

/** Photos to PDF: one photo per page, in the order listed. */
@Composable
fun PdfScreen(vm: PdfVm, pickFolder: () -> Unit) {
    val p = LocalPalette.current
    val add = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { vm.add(it) }
    val result = vm.result
    val shown = rememberLast(result)
    val last = vm.pages.lastIndex

    ToolScaffold(
        scrollTo = result,
        bar = {
            ActionBar(
                busy = vm.busy, hasResult = result != null, savedAs = vm.save.savedAs,
                primary = "Make PDF", primaryEnabled = vm.pages.isNotEmpty(),
                onPrimary = vm::make, onSave = vm::saveResult,
            )
        },
    ) {
        SectionCard {
            Section("Pages") {
                vm.pages.forEachIndexed { i, item ->
                    key(item.id) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Image(
                                remember(item.thumb) { item.thumb.asImageBitmap() }, null,
                                Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)),
                                contentScale = ContentScale.Crop,
                            )
                            Column(Modifier.weight(1f)) {
                                Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = Type.title)
                                Text("Page ${i + 1}", color = p.mute, style = Type.small)
                            }
                            IconTap("\u2191", enabled = i > 0) { vm.move(i, -1) }
                            IconTap("\u2193", enabled = i < last) { vm.move(i, 1) }
                            IconTap("\u00d7") { vm.remove(i) }
                        }
                    }
                }
                PillButton(
                    if (vm.pages.isEmpty()) "Add photos" else "Add more",
                    style = if (vm.pages.isEmpty()) PillStyle.Filled else PillStyle.Outlined,
                ) { add.launch(arrayOf("image/*")) }
            }

            Rule()
            Section("Page size") {
                Segmented(listOf("A4", "Photo size"), if (vm.a4) 0 else 1) { vm.onA4(it == 0) }
            }

            StatusSection(vm.busy, vm.failure)

            Reveal(result != null) {
                if (shown != null) PdfResultBlock(vm, shown, pickFolder)
            }
        }
    }
}

@Composable
private fun ColumnScope.PdfResultBlock(vm: PdfVm, r: PdfResult, pickFolder: () -> Unit) {
    Rule()
    Section("Result") {
        StatRow("Pages", "${r.pages}")
        StatRow("File size", formatSize(r.bytes.size.toLong()))
    }
    Rule()
    SaveSection(vm.save.name, vm.save::onName, "pdf", pickFolder)
}
