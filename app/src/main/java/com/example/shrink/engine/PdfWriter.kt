package com.example.shrink.engine

import java.io.ByteArrayOutputStream
import java.util.Locale

class JpegPage(val jpeg: ByteArray, val pxW: Int, val pxH: Int, val ptW: Float, val ptH: Float)

/**
 * Minimal PDF writer that embeds each page as a raw JPEG (DCTDecode).
 * Android's PdfDocument re-encodes bitmaps losslessly, which balloons file size,
 * so we write the bytes ourselves and keep full control of the output size.
 */
object PdfWriter {
    fun write(pages: List<JpegPage>): ByteArray {
        val out = ByteArrayOutputStream()
        val offsets = ArrayList<Int>()
        fun s(t: String) = out.write(t.toByteArray(Charsets.ISO_8859_1))
        fun obj(n: Int, body: () -> Unit) {
            offsets.add(out.size()); s("$n 0 obj\n"); body(); s("\nendobj\n")
        }

        s("%PDF-1.4\n")
        obj(1) { s("<< /Type /Catalog /Pages 2 0 R >>") }
        val kids = pages.indices.joinToString(" ") { "${3 + 3 * it} 0 R" }
        obj(2) { s("<< /Type /Pages /Kids [$kids] /Count ${pages.size} >>") }

        pages.forEachIndexed { i, p ->
            val page = 3 + 3 * i; val content = page + 1; val image = page + 2
            obj(page) {
                s("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${p.ptW} ${p.ptH}] " +
                    "/Resources << /XObject << /Im0 $image 0 R >> >> /Contents $content 0 R >>")
            }
            val draw = "q ${p.ptW} 0 0 ${p.ptH} 0 0 cm /Im0 Do Q"
            obj(content) { s("<< /Length ${draw.length} >>\nstream\n$draw\nendstream") }
            obj(image) {
                s("<< /Type /XObject /Subtype /Image /Width ${p.pxW} /Height ${p.pxH} " +
                    "/ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length ${p.jpeg.size} >>\nstream\n")
                out.write(p.jpeg)
                s("\nendstream")
            }
        }

        val xref = out.size()
        s("xref\n0 ${offsets.size + 1}\n0000000000 65535 f \n")
        offsets.forEach { s(String.format(Locale.US, "%010d 00000 n \n", it)) }
        s("trailer\n<< /Size ${offsets.size + 1} /Root 1 0 R >>\nstartxref\n$xref\n%%EOF")
        return out.toByteArray()
    }
}
