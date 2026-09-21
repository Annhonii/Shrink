package com.example.shrink.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.shrink.CropVm
import com.example.shrink.PdfVm
import com.example.shrink.ShrinkVm
import com.example.shrink.engine.Saver

@Composable
fun AppRoot(shrink: ShrinkVm, crop: CropVm, pdf: PdfVm) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) {
        Saver.setFolder(ctx, it)
    }
    val pickFolder = { folderPicker.launch(null) }

    Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
        Row(Modifier.padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Shrinky", style = Type.headline)
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(9.dp).clip(CircleShape).background(p.accent))
        }
        Segmented(
            listOf("Shrink", "Crop", "PDF"), tab,
            Modifier.padding(horizontal = 20.dp).padding(bottom = 12.dp),
        ) { tab = it }

        when (tab) {
            0 -> ShrinkScreen(shrink, pickFolder)
            1 -> CropScreen(crop, pickFolder)
            else -> PdfScreen(pdf, pickFolder)
        }
    }
}
