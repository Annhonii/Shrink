package com.example.shrink.engine

import android.content.ContentResolver
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

enum class Kind { IMAGE, PDF }

enum class OutFormat(val label: String, val mime: String, val ext: String) {
    JPG("JPG", "image/jpeg", "jpg"),
    PNG("PNG", "image/png", "png"),
    WEBP("WebP", "image/webp", "webp"),
}

class Shrunk(
    val bytes: ByteArray,
    val mime: String,
    val ext: String,
    val hitTarget: Boolean,
    val unchanged: Boolean = false,
    val rasterized: Boolean = false,
    val origW: Int = 0, val origH: Int = 0,
    val newW: Int = 0, val newH: Int = 0,
    val pages: Int = 0, val dpi: Int = 0,
    val before: Bitmap? = null,
    val after: Bitmap? = null,
)

object Engine {
    private const val MIN_Q = 40
    private const val MAX_Q = 95
    private const val MAX_SIDE = 4096
    private const val PREVIEW_SIDE = 1080

    // (render scale, JPEG quality), biggest file first. Binary-searched for the first that fits.
    private val PDF_LADDER = listOf(
        2.0f to 85, 1.6f to 75, 1.3f to 65, 1.0f to 60, 1.0f to 45,
        0.8f to 40, 0.6f to 35, 0.45f to 30, 0.3f to 25,
    )

    fun compress(
        cr: ContentResolver, uri: Uri, kind: Kind, mime: String,
        originalSize: Long, target: Long, format: OutFormat,
    ): Shrunk {
        val sameFormat = kind == Kind.PDF || mime == format.mime ||
            (format == OutFormat.JPG && mime == "image/jpg")
        if (sameFormat && originalSize in 1..target) {
            val bytes = cr.openInputStream(uri)!!.use { it.readBytes() }
            return Shrunk(bytes, mime, if (kind == Kind.PDF) "pdf" else format.ext, hitTarget = true, unchanged = true)
        }
        return when (kind) {
            Kind.IMAGE -> compressImage(cr, uri, target, format)
            Kind.PDF -> compressPdf(cr, uri, target)
        }
    }

    // ---------- Images ----------

    private fun compressImage(cr: ContentResolver, uri: Uri, target: Long, fmt: OutFormat): Shrunk {
        var intrinsic = 0
        val decoded = ImageDecoder.decodeBitmap(ImageDecoder.createSource(cr, uri)) { d, info, _ ->
            d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val longest = max(info.size.width, info.size.height)
            intrinsic = longest
            if (longest > MAX_SIDE) {
                val s = MAX_SIDE.toFloat() / longest
                d.setTargetSize((info.size.width * s).toInt(), (info.size.height * s).toInt())
            }
        }
        // True original dimensions (decoding may have capped the size to save memory).
        val k = intrinsic.toFloat() / max(decoded.width, decoded.height)
        val origW = (decoded.width * k).roundToInt()
        val origH = (decoded.height * k).roundToInt()
        val base = if (fmt == OutFormat.JPG) flatten(decoded) else decoded

        fun done(bytes: ByteArray, bmp: Bitmap, hit: Boolean) = imageResult(bytes, bmp, base, fmt, hit, origW, origH)

        if (fmt == OutFormat.PNG) {
            // PNG is lossless: the only lever is resolution, so binary-search the scale.
            val full = encode(base, fmt, 100)
            if (full.size <= target) return done(full, base, true)
            var lo = 0.04f; var hi = 1f
            var bestBytes: ByteArray? = null; var bestBmp: Bitmap? = null
            repeat(9) {
                val mid = (lo + hi) / 2
                val b = scaled(base, mid)
                val bytes = encode(b, fmt, 100)
                if (bytes.size <= target) { bestBytes = bytes; bestBmp = b; lo = mid } else hi = mid
            }
            val bb = bestBytes
            if (bb != null) return done(bb, bestBmp!!, true)
            val b = scaled(base, 0.04f)
            val bytes = encode(b, fmt, 100)
            return done(bytes, b, bytes.size <= target)
        }

        // JPG / WebP: search quality first, shrink resolution only if quality alone can't reach the target.
        var scale = 1f
        var smallest: ByteArray? = null
        var smallestBmp: Bitmap = base
        for (attempt in 0 until 16) {
            val bmp = scaled(base, scale)
            val atMin = encode(bmp, fmt, MIN_Q)
            if (atMin.size <= target) {
                var lo = MIN_Q; var hi = MAX_Q; var best = atMin
                while (lo < hi) {
                    val mid = (lo + hi + 1) / 2
                    val b = encode(bmp, fmt, mid)
                    if (b.size <= target) { lo = mid; best = b } else hi = mid - 1
                }
                return done(best, bmp, true)
            }
            smallest = atMin
            smallestBmp = bmp
            scale *= 0.85f
        }
        return done(smallest!!, smallestBmp, false)
    }

    private fun imageResult(
        bytes: ByteArray, bmp: Bitmap, base: Bitmap, fmt: OutFormat,
        hit: Boolean, origW: Int, origH: Int,
    ): Shrunk {
        val after = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { thumb(it) }
        return Shrunk(
            bytes, fmt.mime, fmt.ext, hit,
            origW = origW, origH = origH, newW = bmp.width, newH = bmp.height,
            before = thumb(base), after = after,
        )
    }

    private fun scaled(src: Bitmap, s: Float): Bitmap {
        if (s >= 1f) return src
        return Bitmap.createScaledBitmap(
            src,
            (src.width * s).roundToInt().coerceAtLeast(32),
            (src.height * s).roundToInt().coerceAtLeast(32),
            true,
        )
    }

    private fun thumb(src: Bitmap): Bitmap {
        val m = max(src.width, src.height)
        if (m <= PREVIEW_SIDE) return src
        val s = PREVIEW_SIDE.toFloat() / m
        return Bitmap.createScaledBitmap(
            src, (src.width * s).roundToInt().coerceAtLeast(1), (src.height * s).roundToInt().coerceAtLeast(1), true,
        )
    }

    private fun flatten(src: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        Canvas(out).apply { drawColor(Color.WHITE); drawBitmap(src, 0f, 0f, null) }
        return out
    }

    @Suppress("DEPRECATION")
    private fun encode(b: Bitmap, fmt: OutFormat, q: Int): ByteArray {
        val cf = when (fmt) {
            OutFormat.JPG -> Bitmap.CompressFormat.JPEG
            OutFormat.PNG -> Bitmap.CompressFormat.PNG
            OutFormat.WEBP ->
                if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
        }
        return ByteArrayOutputStream().also { b.compress(cf, q, it) }.toByteArray()
    }

    // ---------- PDF ----------

    private fun compressPdf(cr: ContentResolver, uri: Uri, target: Long): Shrunk {
        val pfd = cr.openFileDescriptor(uri, "r") ?: error("Can't open this PDF.")
        pfd.use {
            PdfRenderer(it).use { renderer ->
                val cache = HashMap<Int, ByteArray>()
                fun build(step: Int): ByteArray = cache.getOrPut(step) {
                    val (scale, q) = PDF_LADDER[step]
                    val pages = (0 until renderer.pageCount).map { i ->
                        renderer.openPage(i).use { p ->
                            val w = (p.width * scale).roundToInt().coerceAtLeast(1)
                            val h = (p.height * scale).roundToInt().coerceAtLeast(1)
                            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
                            p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            val jpg = encode(bmp, OutFormat.JPG, q)
                            bmp.recycle()
                            JpegPage(jpg, w, h, p.width.toFloat(), p.height.toFloat())
                        }
                    }
                    PdfWriter.write(pages)
                }

                var lo = 0; var hi = PDF_LADDER.lastIndex
                var best: ByteArray? = null; var bestStep = PDF_LADDER.lastIndex
                while (lo <= hi) {
                    val mid = (lo + hi) / 2
                    val out = build(mid)
                    if (out.size <= target) { best = out; bestStep = mid; hi = mid - 1 } else lo = mid + 1
                }
                val hit = best != null
                return Shrunk(
                    best ?: build(PDF_LADDER.lastIndex), "application/pdf", "pdf", hit,
                    rasterized = true,
                    pages = renderer.pageCount,
                    dpi = (72 * PDF_LADDER[bestStep].first).roundToInt(),
                )
            }
        }
    }

    // ---------- Save ----------

    fun saveToDownloads(cr: ContentResolver, name: String, mime: String, bytes: ByteArray): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Shrink")
        }
        val uri = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("Can't create file.")
        cr.openOutputStream(uri)!!.use { it.write(bytes) }
        return uri
    }
}
