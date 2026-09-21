package com.davexh.shrinky.engine

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

class Decoded(val bitmap: Bitmap, val origW: Int, val origH: Int)

object Images {
    /** Decodes with EXIF rotation applied, capped to [maxSide] to save memory. Also reports the true original size. */
    fun decode(cr: ContentResolver, uri: Uri, maxSide: Int): Decoded {
        var intrinsic = 0
        val bmp = ImageDecoder.decodeBitmap(ImageDecoder.createSource(cr, uri)) { d, info, _ ->
            d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val longest = max(info.size.width, info.size.height)
            intrinsic = longest
            if (longest > maxSide) {
                val s = maxSide.toFloat() / longest
                d.setTargetSize(max(1, (info.size.width * s).toInt()), max(1, (info.size.height * s).toInt()))
            }
        }
        val k = intrinsic.toFloat() / max(bmp.width, bmp.height)
        return Decoded(bmp, (bmp.width * k).roundToInt(), (bmp.height * k).roundToInt())
    }

    fun flatten(src: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        Canvas(out).apply { drawColor(Color.WHITE); drawBitmap(src, 0f, 0f, null) }
        return out
    }

    fun scaled(src: Bitmap, s: Float): Bitmap {
        if (s >= 1f) return src
        return Bitmap.createScaledBitmap(
            src,
            (src.width * s).roundToInt().coerceAtLeast(32),
            (src.height * s).roundToInt().coerceAtLeast(32),
            true,
        )
    }

    fun thumb(src: Bitmap, side: Int = 1080): Bitmap {
        val m = max(src.width, src.height)
        if (m <= side) return src
        val s = side.toFloat() / m
        return Bitmap.createScaledBitmap(
            src, (src.width * s).roundToInt().coerceAtLeast(1), (src.height * s).roundToInt().coerceAtLeast(1), true,
        )
    }

    @Suppress("DEPRECATION")
    fun encode(b: Bitmap, fmt: OutFormat, q: Int): ByteArray {
        val cf = when (fmt) {
            OutFormat.JPG -> Bitmap.CompressFormat.JPEG
            OutFormat.PNG -> Bitmap.CompressFormat.PNG
            OutFormat.WEBP ->
                if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
        }
        return ByteArrayOutputStream().also { b.compress(cf, q, it) }.toByteArray()
    }

    /** Cuts [r] (source pixels) out of [src] and scales it to exactly outW x outH, halving first to avoid aliasing. */
    fun crop(src: Bitmap, r: RectF, outW: Int, outH: Int, fmt: OutFormat): Bitmap {
        val l = r.left.roundToInt().coerceIn(0, src.width - 1)
        val t = r.top.roundToInt().coerceIn(0, src.height - 1)
        val rw = r.width().roundToInt().coerceIn(1, src.width - l)
        val rh = r.height().roundToInt().coerceIn(1, src.height - t)
        var region = Bitmap.createBitmap(src, l, t, rw, rh)
        while (region.width >= outW * 2 && region.height >= outH * 2) {
            region = Bitmap.createScaledBitmap(region, region.width / 2, region.height / 2, true)
        }
        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        if (fmt == OutFormat.JPG) c.drawColor(Color.WHITE)
        c.drawBitmap(region, Rect(0, 0, region.width, region.height), Rect(0, 0, outW, outH), Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }

    /**
     * Same centre window from the original and from the result, both at the ORIGINAL pixel scale.
     * A smaller/blurrier result shows up here exactly as it does when the saved file is opened at full size.
     */
    fun zoomPair(orig: Bitmap, result: Bitmap): Pair<Bitmap, Bitmap> {
        val w = minOf(orig.width, 900)
        val h = minOf(orig.height, 675)
        val l = (orig.width - w) / 2
        val t = (orig.height - h) / 2
        val before = Bitmap.createBitmap(orig, l, t, w, h)
        val after = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val k = result.width.toFloat() / orig.width
        val src = Rect(
            (l * k).roundToInt(),
            (t * k).roundToInt(),
            ((l + w) * k).roundToInt().coerceAtMost(result.width),
            ((t + h) * k).roundToInt().coerceAtMost(result.height),
        )
        Canvas(after).drawBitmap(result, src, Rect(0, 0, w, h), Paint(Paint.FILTER_BITMAP_FLAG))
        return before to after
    }
}
