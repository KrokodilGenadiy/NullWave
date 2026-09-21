package com.zaus.nullwave

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import com.zaus.nullwave.designsystem.theme.NullWaveColors
import com.zaus.nullwave.designsystem.theme.NullWaveTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Dark only: pin the system bars to the theme's canvas instead of letting the platform
        // pick a light scrim. The app bar and the mini-player handle their own insets.
        val barColor = NullWaveColors.Yellow.bg.toArgb()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(barColor),
            navigationBarStyle = SystemBarStyle.dark(barColor),
        )
        super.onCreate(savedInstanceState)
        setContent {
            NullWaveTheme {
                // Nothing here yet. The library, the drawer and the player sheet go in from here.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(NullWaveTheme.colors.bg)
                )
            }
        }
    }
}
