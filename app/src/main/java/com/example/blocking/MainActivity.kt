package com.example.blocking

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.blocking.ui.BlockingScreen
import com.example.blocking.ui.theme.BlockingTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BlockingTheme {
                BlockingScreen()
            }
        }
    }
}