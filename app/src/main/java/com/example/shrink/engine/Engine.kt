package com.example.shrink.engine

import android.content.ContentResolver
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

enum class Kind { IMAGE, PDF }

class Shrunk(
    val bytes: ByteArray,
    val mime: String,
    val ext: String,
    val hitTarget: Boolean,
    val rasterized: Boolean = false,
)

object Engine {
    private const val MIN_Q = 40
    private const val MAX_Q = 95
    private const val MAX_SIDE = 4096

    // (render scale, JPEG quality), biggest file first. Binary-searched for the first that fits.
    private val PDF_LADDER = listOf(
        2.0f to 85, 1.6f to 75, 1.3f to 65, 1.0f to 60, 1.0f to 45,
        0.8f to 40, 0.6f to 35, 0.45f to 30, 0.3f to 25,
    )

    fun compress(cr: ContentResolver, uri: Uri, kind: Kind, mime: String, originalSize: Long, target: Long): Shrunk {
        if (originalSize in 1..target) {
            val bytes = cr.openInputStream(uri)!!.use { it.readBytes() }
            val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "bin"
            return Shrunk(bytes, mime, ext, hitTarget = true)
        }
        return when (kind) {
            Kind.IMAGE -> compressImage(cr, uri, target)
            Kind.PDF -> compressPdf(cr, uri, target)
        }
    }

    // ---------- Images ----------

    private fun compressImage(cr: ContentResolver, uri: Uri, target: Long): Shrunk {
        val decoded = ImageDecoder.decodeBitmap(ImageDecoder.createSource(cr, uri)) { d, info, _ ->
            d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val longest = max(info.size.width, info.size.height)
            if (longest > MAX_SIDE) {
                val s = MAX_SIDE.toFloat() / longest
                d.setTargetSize((info.size.width * s).toInt(), (info.size.height * s).toInt())
            }
        }
        val flat = flatten(decoded)
        var scale = 1f
        var smallest: ByteArray? = null

        repeat(16) {
            val bmp = if (scale == 1f) flat else Bitmap.createScaledBitmap(
                flat,
                (flat.width * scale).roundToInt().coerceAtLeast(32),
                (flat.height * scale).roundToInt().coerceAtLeast(32),
                true,
            )
            val atMin = jpeg(bmp, MIN_Q)
            if (atMin.size <= target) {
                var lo = MIN_Q; var hi = MAX_Q; var best = atMin
                while (lo < hi) {
                    val mid = (lo + hi + 1) / 2
                    val b = jpeg(bmp, mid)
                    if (b.size <= target) { lo = mid; best = b } else hi = mid - 1
                }
                return Shrunk(best, "image/jpeg", "jpg", hitTarget = true)
            }
            smallest = atMin
            scale *= 0.85f
        }
        return Shrunk(smallest!!, "image/jpeg", "jpg", hitTarget = false)
    }

    private fun flatten(src: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        Canvas(out).apply { drawColor(Color.WHITE); drawBitmap(src, 0f, 0f, null) }
        return out
    }

    private fun jpeg(b: Bitmap, q: Int): ByteArray =
        ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.JPEG, q, it) }.toByteArray()

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
                            val jpg = jpeg(bmp, q)
                            bmp.recycle()
                            JpegPage(jpg, w, h, p.width.toFloat(), p.height.toFloat())
                        }
                    }
                    PdfWriter.write(pages)
                }

                var lo = 0; var hi = PDF_LADDER.lastIndex; var best: ByteArray? = null
                while (lo <= hi) {
                    val mid = (lo + hi) / 2
                    val out = build(mid)
                    if (out.size <= target) { best = out; hi = mid - 1 } else lo = mid + 1
                }
                val hit = best != null
                return Shrunk(best ?: build(PDF_LADDER.lastIndex), "application/pdf", "pdf", hit, rasterized = true)
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
