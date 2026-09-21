package com.davexh.shrinky.engine

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntSize

/**
 * The photo sits under a fixed frame (aspect = width:height). The user pans and pinches the photo;
 * [sourceRect] turns whatever is inside the frame back into source-pixel coordinates.
 */
class CropState {
    var image by mutableStateOf<Bitmap?>(null)
    var view by mutableStateOf(IntSize.Zero)
    var aspect by mutableFloatStateOf(0f)
    var zoom by mutableFloatStateOf(1f)
    var offset by mutableStateOf(Offset.Zero)

    /** Frame size in view pixels, centered in the view. Zero when no valid size was entered. */
    val frame: Size
        get() {
            if (aspect <= 0f || view.width == 0 || view.height == 0) return Size.Zero
            val w = minOf(view.width * 0.88f, view.height * 0.88f * aspect)
            return Size(w, w / aspect)
        }

    /** View pixels per source pixel. */
    val scale: Float
        get() {
            val b = image ?: return 1f
            val f = frame
            return if (f == Size.Zero) {
                minOf(view.width.toFloat() / b.width, view.height.toFloat() / b.height)
            } else {
                maxOf(f.width / b.width, f.height / b.height) * zoom
            }
        }

    private fun clamp(o: Offset, s: Float): Offset {
        val b = image ?: return Offset.Zero
        val f = frame
        if (f == Size.Zero) return Offset.Zero
        val mx = ((b.width * s - f.width) / 2f).coerceAtLeast(0f)
        val my = ((b.height * s - f.height) / 2f).coerceAtLeast(0f)
        return Offset(o.x.coerceIn(-mx, mx), o.y.coerceIn(-my, my))
    }

    fun transform(centroid: Offset, pan: Offset, zoomChange: Float) {
        if (frame == Size.Zero) return
        val old = zoom
        val next = (old * zoomChange).coerceIn(1f, 8f)
        val r = next / old
        val c = centroid - Offset(view.width / 2f, view.height / 2f)
        zoom = next
        offset = clamp(c - (c - offset) * r + pan, scale)
    }

    fun reset() { zoom = 1f; offset = Offset.Zero }

    fun setAspect(w: Int, h: Int) {
        val a = if (w > 0 && h > 0) w.toFloat() / h else 0f
        if (a != aspect) { aspect = a; reset() }
    }

    fun sourceRect(): RectF? {
        val b = image ?: return null
        val f = frame
        if (f == Size.Zero) return null
        val s = scale
        val w = f.width / s
        val h = f.height / s
        val left = ((b.width * s / 2f - offset.x - f.width / 2f) / s).coerceIn(0f, (b.width - w).coerceAtLeast(0f))
        val top = ((b.height * s / 2f - offset.y - f.height / 2f) / s).coerceIn(0f, (b.height - h).coerceAtLeast(0f))
        return RectF(left, top, left + w, top + h)
    }
}
