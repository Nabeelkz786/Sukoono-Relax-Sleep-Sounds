package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.monetization.AdMobManager
import com.example.ui.ZenithApp
import com.example.ui.theme.ZenithTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AdMobManager.ensureMobileAdsInitialized(applicationContext)
        enableEdgeToEdge()
        setContent {
            ZenithTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ZenithApp()
                }
            }
        }
    }
}
