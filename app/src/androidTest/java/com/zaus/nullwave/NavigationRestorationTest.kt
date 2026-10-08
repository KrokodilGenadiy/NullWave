package com.zaus.nullwave

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.zaus.nullwave.ui.theme.NullWaveTheme
import org.junit.Rule
import org.junit.Test

class NavigationRestorationTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun savedBackStackRestoresBothFeatureKeys() {
        val application = InstrumentationRegistry.getInstrumentation()
            .targetContext.applicationContext as NullWaveApplication
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            NullWaveTheme {
                NullWaveNavigation(application.appGraph.entryProviderInstallers)
            }
        }

        compose.onNodeWithText("О приложении").performClick()
        compose.onNodeWithText("Назад").assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Назад").assertIsDisplayed()
        compose.onNodeWithText("Назад").performClick()
        compose.onNodeWithText("Библиотека").assertIsDisplayed()
    }
}
