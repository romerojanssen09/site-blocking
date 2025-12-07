package com.example.blocking

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.blocking.ui.BlockingScreen
import com.example.blocking.ui.theme.BlockingTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Install splash screen before super.onCreate()
        installSplashScreen()
        
        super.onCreate(savedInstanceState)
        
        // Initialize database
        com.example.blocking.data.BlockingRulesManager.initialize(this)
        
        enableEdgeToEdge()
        setContent {
            BlockingTheme {
                BlockingScreen()
            }
        }
    }
}