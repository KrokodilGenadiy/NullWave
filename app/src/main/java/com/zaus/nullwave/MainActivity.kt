package com.zaus.nullwave

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.zaus.nullwave.ui.theme.NullWaveTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val entryProviderInstallers = (application as NullWaveApplication)
            .appGraph.entryProviderInstallers
        setContent {
            NullWaveTheme {
                NullWaveNavigation(entryProviderInstallers)
            }
        }
    }
}

