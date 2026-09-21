package com.davexh.shrinky

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.davexh.shrinky.engine.Saver
import com.davexh.shrinky.ui.AppRoot
import com.davexh.shrinky.ui.ShrinkyTheme

class MainActivity : ComponentActivity() {
    private val shrink: ShrinkVm by viewModels()
    private val crop: CropVm by viewModels()
    private val pdf: PdfVm by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Saver.init(applicationContext)
        enableEdgeToEdge()
        setContent { ShrinkyTheme { AppRoot(shrink, crop, pdf) } }
    }
}
