package com.example.shrink.engine

import android.content.ContentResolver
import android.net.Uri

object PdfMaker {
    private const val A4_W = 595f
    private const val A4_H = 842f

    /** One image per page. A4 keeps the photo fully visible on a white page; otherwise the page matches the photo. */
    fun make(cr: ContentResolver, uris: List<Uri>, a4: Boolean): ByteArray {
        val pages = uris.map { uri ->
            val flat = Images.flatten(Images.decode(cr, uri, 2400).bitmap)
            val jpg = Images.encode(flat, OutFormat.JPG, 85)
            val w = flat.width
            val h = flat.height
            flat.recycle()
            if (a4) {
                val land = w > h
                val pw = if (land) A4_H else A4_W
                val ph = if (land) A4_W else A4_H
                val s = minOf(pw / w, ph / h)
                val dw = w * s
                val dh = h * s
                JpegPage(jpg, w, h, pw, ph, (pw - dw) / 2f, (ph - dh) / 2f, dw, dh)
            } else {
                JpegPage(jpg, w, h, A4_W, A4_W * h / w)
            }
        }
        return PdfWriter.write(pages)
    }
}
