package com.example.shrink

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.shrink.engine.Engine
import com.example.shrink.engine.Kind
import com.example.shrink.engine.OutFormat
import com.example.shrink.engine.Shrunk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

data class Source(val uri: Uri, val name: String, val bytes: Long, val kind: Kind, val mime: String)

sealed interface Phase {
    data object Idle : Phase
    data object Working : Phase
    data class Done(val shrunk: Shrunk, val savedAs: String? = null) : Phase
    data class Failed(val msg: String) : Phase
}

class ShrinkViewModel(app: Application) : AndroidViewModel(app) {
    private val cr get() = getApplication<Application>().contentResolver

    var source by mutableStateOf<Source?>(null); private set
    var phase by mutableStateOf<Phase>(Phase.Idle); private set
    var targetText by mutableStateOf("200"); private set
    var unitMb by mutableStateOf(false); private set
    var format by mutableStateOf(OutFormat.JPG); private set

    val targetBytes: Long
        get() = ((targetText.toDoubleOrNull() ?: 0.0) * if (unitMb) 1_048_576.0 else 1024.0).toLong()

    // Any change to the inputs makes an old result stale, so drop it.
    private fun invalidate() { if (phase is Phase.Done || phase is Phase.Failed) phase = Phase.Idle }

    fun onTargetText(v: String) { targetText = v.filter { it.isDigit() || it == '.' }.take(7); invalidate() }
    fun onUnit(mb: Boolean) { unitMb = mb; invalidate() }
    fun onFormat(f: OutFormat) { format = f; invalidate() }

    fun pick(uri: Uri?) {
        uri ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val mime = cr.getType(uri).orEmpty()
            val kind = when {
                mime == "application/pdf" -> Kind.PDF
                mime.startsWith("image/") -> Kind.IMAGE
                else -> null
            }
            if (kind == null) { phase = Phase.Failed("Only photos and PDFs are supported for now."); return@launch }
            var name = "file"; var size = 0L
            cr.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { name = c.getString(it) }
                    c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 }?.let { size = c.getLong(it) }
                }
            }
            source = Source(uri, name, size, kind, mime)
            // Default the output to the photo's own format.
            format = when (mime) {
                "image/png" -> OutFormat.PNG
                "image/webp" -> OutFormat.WEBP
                else -> OutFormat.JPG
            }
            phase = Phase.Idle
        }
    }

    fun shrink() {
        val s = source ?: return
        val t = targetBytes
        if (t < 5 * 1024) { phase = Phase.Failed("Target must be at least 5 KB."); return }
        val f = format
        phase = Phase.Working
        viewModelScope.launch(Dispatchers.Default) {
            phase = try {
                Phase.Done(Engine.compress(cr, s.uri, s.kind, s.mime, s.bytes, t, f))
            } catch (e: Throwable) {
                Phase.Failed(e.message ?: "Couldn't process this file.")
            }
        }
    }

    fun save() {
        val d = phase as? Phase.Done ?: return
        val s = source ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val name = s.name.substringBeforeLast('.') + "_shrunk." + d.shrunk.ext
            phase = try {
                Engine.saveToDownloads(cr, name, d.shrunk.mime, d.shrunk.bytes)
                d.copy(savedAs = name)
            } catch (e: Throwable) {
                Phase.Failed("Couldn't save: ${e.message}")
            }
        }
    }
}
