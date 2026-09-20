package com.example.shrink

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.shrink.ui.HomeScreen
import com.example.shrink.ui.ShrinkTheme

class MainActivity : ComponentActivity() {
    private val vm: ShrinkViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { ShrinkTheme { HomeScreen(vm) } }
    }
}
