package com.zaus.nullwave

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import org.junit.Rule
import org.junit.Test
import com.zaus.nullwave.feature.about.api.R as AboutR
import com.zaus.nullwave.feature.library.impl.R as LibraryR

class NavigationRestorationTest {
    private fun libraryText(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun restoredSectionCanReturnToLibraryThroughTheMenu() {
        val application = InstrumentationRegistry.getInstrumentation()
            .targetContext.applicationContext as NullWaveApplication
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            NullWaveTheme {
                CompositionLocalProvider(LocalMetroViewModelFactory provides application.appGraph.metroViewModelFactory) {
                    NullWaveNavigation(
                        application.appGraph.entryProviderInstallers,
                        rememberNullWaveNavigationScope(),
                    )
                }
            }
        }

        compose.onNodeWithContentDescription(libraryText(R.string.menu_open)).performClick()
        compose.onNodeWithText(libraryText(AboutR.string.about_title)).performClick()
        compose.onNodeWithText(libraryText(AboutR.string.about_title).uppercase()).assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText(libraryText(AboutR.string.about_title).uppercase()).assertIsDisplayed()
        compose.onNodeWithContentDescription(libraryText(R.string.menu_open)).performClick()
        compose.onNodeWithText(libraryText(R.string.menu_library)).performClick()
        compose.onNodeWithText(libraryText(LibraryR.string.library_title)).assertIsDisplayed()
    }
}
