package com.example.shrink

import android.app.Application
import android.content.ContentResolver
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.shrink.engine.CropState
import com.example.shrink.engine.Engine
import com.example.shrink.engine.Images
import com.example.shrink.engine.Kind
import com.example.shrink.engine.OutFormat
import com.example.shrink.engine.PdfMaker
import com.example.shrink.engine.Saver
import com.example.shrink.engine.Shrunk
import com.example.shrink.engine.queryMeta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** File name + "was it saved" state shared by every tool. */
class SaveState {
    var name by mutableStateOf("")
    var savedAs by mutableStateOf<String?>(null)
    fun onName(v: String) { name = v; savedAs = null }
    fun reset(defaultName: String) { name = defaultName; savedAs = null }
}

abstract class BaseVm(app: Application) : AndroidViewModel(app) {
    protected val cr: ContentResolver get() = getApplication<Application>().contentResolver

    var busy by mutableStateOf(false); protected set
    var failure by mutableStateOf<String?>(null); protected set
    val save = SaveState()

    protected fun work(block: () -> Unit) {
        busy = true; failure = null
        viewModelScope.launch(Dispatchers.Default) {
            try { block() } catch (e: Throwable) { failure = e.message ?: "Something went wrong." } finally { busy = false }
        }
    }

    protected fun writeFile(ext: String, mime: String, bytes: ByteArray) {
        val name = Saver.fileName(save.name, ext)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Saver.save(cr, name, mime, bytes)
                save.savedAs = Saver.label + "/" + name
            } catch (e: Throwable) {
                failure = "Couldn't save: ${e.message}"
            }
        }
    }
}

// ---------------------------------------------------------------- Shrink

data class Source(val uri: Uri, val name: String, val bytes: Long, val kind: Kind, val mime: String)

class ShrinkVm(app: Application) : BaseVm(app) {
    var source by mutableStateOf<Source?>(null); private set
    var result by mutableStateOf<Shrunk?>(null); private set
    var targetText by mutableStateOf("200"); private set
    var unitMb by mutableStateOf(false); private set
    var format by mutableStateOf(OutFormat.JPG); private set

    val targetBytes: Long
        get() = ((targetText.toDoubleOrNull() ?: 0.0) * if (unitMb) 1_048_576.0 else 1024.0).toLong()

    private fun stale() { result = null; failure = null }

    fun onTargetText(v: String) { targetText = v.filter { it.isDigit() || it == '.' }.take(7); stale() }
    fun onUnit(mb: Boolean) { unitMb = mb; stale() }
    fun onFormat(f: OutFormat) { format = f; stale() }

    fun pick(uri: Uri?) {
        uri ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val mime = cr.getType(uri).orEmpty()
            val kind = when {
                mime == "application/pdf" -> Kind.PDF
                mime.startsWith("image/") -> Kind.IMAGE
                else -> null
            }
            if (kind == null) { failure = "Only photos and PDFs are supported."; return@launch }
            val (name, size) = queryMeta(cr, uri)
            source = Source(uri, name, size, kind, mime)
            format = when (mime) {
                "image/png" -> OutFormat.PNG
                "image/webp" -> OutFormat.WEBP
                else -> OutFormat.JPG
            }
            result = null
            failure = null
        }
    }

    fun shrink() {
        val s = source ?: return
        val t = targetBytes
        if (t < 5 * 1024) { failure = "Target must be at least 5 KB."; return }
        val f = format
        result = null
        work {
            result = Engine.compress(cr, s.uri, s.kind, s.mime, s.bytes, t, f)
            save.reset(s.name.substringBeforeLast('.') + "_shrunk")
        }
    }

    fun saveResult() {
        val r = result ?: return
        writeFile(r.ext, r.mime, r.bytes)
    }
}

// ---------------------------------------------------------------- Crop

class CropSource(val name: String, val origW: Int, val origH: Int)

class CropResult(val bytes: ByteArray, val ext: String, val mime: String, val w: Int, val h: Int, val preview: Bitmap)

class CropVm(app: Application) : BaseVm(app) {
    val editor = CropState()
    var src by mutableStateOf<CropSource?>(null); private set
    var wText by mutableStateOf(""); private set
    var hText by mutableStateOf(""); private set
    var format by mutableStateOf(OutFormat.JPG); private set
    var result by mutableStateOf<CropResult?>(null); private set

    private fun sizeChanged() {
        result = null; failure = null
        editor.setAspect(wText.toIntOrNull() ?: 0, hText.toIntOrNull() ?: 0)
    }

    fun onW(v: String) { wText = v.filter { it.isDigit() }.take(4); sizeChanged() }
    fun onH(v: String) { hText = v.filter { it.isDigit() }.take(4); sizeChanged() }
    fun onFormat(f: OutFormat) { format = f; result = null; failure = null }

    fun pick(uri: Uri?) {
        uri ?: return
        work {
            val mime = cr.getType(uri).orEmpty()
            if (!mime.startsWith("image/")) error("Pick a photo.")
            val d = Images.decode(cr, uri, 3072)
            val (name, _) = queryMeta(cr, uri)
            editor.image = d.bitmap
            editor.reset()
            format = when (mime) {
                "image/png" -> OutFormat.PNG
                "image/webp" -> OutFormat.WEBP
                else -> OutFormat.JPG
            }
            result = null
            src = CropSource(name, d.origW, d.origH)
        }
    }

    fun crop() {
        val bmp = editor.image ?: return
        val w = wText.toIntOrNull() ?: 0
        val h = hText.toIntOrNull() ?: 0
        val rect = editor.sourceRect()
        if (w <= 0 || h <= 0 || rect == null) { failure = "Enter a width and height first."; return }
        if (w.toLong() * h > 25_000_000L) { failure = "Output is too large. Keep it under 25 megapixels."; return }
        val f = format
        val base = src?.name?.substringBeforeLast('.') ?: "crop"
        work {
            val out = Images.crop(bmp, rect, w, h, f)
            val bytes = Images.encode(out, f, 92)
            result = CropResult(bytes, f.ext, f.mime, w, h, Images.thumb(out))
            save.reset(base + "_" + w + "x" + h)
        }
    }

    fun saveResult() {
        val r = result ?: return
        writeFile(r.ext, r.mime, r.bytes)
    }
}

// ---------------------------------------------------------------- Images to PDF

class PageItem(val id: Long, val uri: Uri, val name: String, val thumb: Bitmap)

class PdfResult(val bytes: ByteArray, val pages: Int)

class PdfVm(app: Application) : BaseVm(app) {
    val pages = mutableStateListOf<PageItem>()
    var a4 by mutableStateOf(true); private set
    var result by mutableStateOf<PdfResult?>(null); private set
    private var nextId = 0L

    private fun stale() { result = null; failure = null }

    fun add(uris: List<Uri>) {
        if (uris.isEmpty()) return
        busy = true
        viewModelScope.launch(Dispatchers.IO) {
            val items = uris.mapNotNull { u ->
                runCatching { PageItem(nextId++, u, queryMeta(cr, u).first, Images.decode(cr, u, 200).bitmap) }.getOrNull()
            }
            withContext(Dispatchers.Main) { pages.addAll(items); stale(); busy = false }
        }
    }

    fun move(i: Int, d: Int) {
        val j = i + d
        if (i !in pages.indices || j !in pages.indices) return
        val t = pages[i]; pages[i] = pages[j]; pages[j] = t
        stale()
    }

    fun remove(i: Int) { if (i in pages.indices) { pages.removeAt(i); stale() } }

    fun onA4(v: Boolean) { a4 = v; stale() }

    fun make() {
        val uris = pages.map { it.uri }
        if (uris.isEmpty()) return
        val a = a4
        work {
            result = PdfResult(PdfMaker.make(cr, uris, a), uris.size)
            save.reset("pages")
        }
    }

    fun saveResult() {
        val r = result ?: return
        writeFile("pdf", "application/pdf", r.bytes)
    }
}
