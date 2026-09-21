package com.davexh.shrinky.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.davexh.shrinky.CropVm
import com.davexh.shrinky.PdfVm
import com.davexh.shrinky.ShrinkVm
import com.davexh.shrinky.engine.Saver
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppRoot(shrink: ShrinkVm, crop: CropVm, pdf: PdfVm) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState(pageCount = { 3 })
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) {
        Saver.setFolder(ctx, it)
    }
    val pickFolder = { folderPicker.launch(null) }
    val bounce = spring<Float>(dampingRatio = 0.7f, stiffness = 300f)

    Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
        Row(
            Modifier.padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Shrinky", style = Type.headline)
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(9.dp).clip(CircleShape).background(p.accent))
        }

        // The pill follows the pager (swipe pages and it slides), and dragging the pill scrolls the pager.
        SlidingSegments(
            labels = listOf("Shrink", "Crop", "PDF"),
            position = pager.currentPage + pager.currentPageOffsetFraction,
            modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 12.dp),
            height = 48.dp,
            onTap = { i -> scope.launch { pager.animateScrollToPage(i, animationSpec = bounce) } },
            onDrag = { f ->
                val size = pager.layoutInfo.pageSize
                if (size > 0) pager.dispatchRawDelta(f * size)
            },
            onRelease = {
                val target = (pager.currentPage + pager.currentPageOffsetFraction).roundToInt().coerceIn(0, 2)
                scope.launch { pager.animateScrollToPage(target, animationSpec = bounce) }
            },
        )

        HorizontalPager(
            state = pager,
            modifier = Modifier.weight(1f),
            beyondViewportPageCount = 2,
            flingBehavior = PagerDefaults.flingBehavior(
                state = pager,
                snapAnimationSpec = spring(dampingRatio = 0.8f, stiffness = 320f),
            ),
            key = { it },
        ) { page ->
            when (page) {
                0 -> ShrinkScreen(shrink, pickFolder)
                1 -> CropScreen(crop, pickFolder)
                else -> PdfScreen(pdf, pickFolder)
            }
        }
    }
}
