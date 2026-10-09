package com.zaus.nullwave

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        val entryProviderInstallers = (application as NullWaveApplication)
            .appGraph.entryProviderInstallers
        setContent {
            NullWaveTheme {
                NullWaveNavigation(entryProviderInstallers)
            }
        }
    }
}

