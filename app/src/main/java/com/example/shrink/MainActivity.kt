package com.example.shrink

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.shrink.engine.Saver
import com.example.shrink.ui.AppRoot
import com.example.shrink.ui.ShrinkyTheme

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
