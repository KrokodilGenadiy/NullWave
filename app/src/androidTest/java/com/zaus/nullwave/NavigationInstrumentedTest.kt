package com.zaus.nullwave

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.Espresso.pressBack
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import com.zaus.nullwave.feature.about.api.R as AboutR
import com.zaus.nullwave.feature.library.impl.R as LibraryR
import com.zaus.nullwave.feature.settings.api.R as SettingsR
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class NavigationInstrumentedTest {
    private fun libraryText(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun switchingFromSettingsToAboutDoesNotBuildBackHistory() {
        compose.onNodeWithText(libraryText(LibraryR.string.library_title)).assertIsDisplayed()
        compose.onNodeWithContentDescription(libraryText(R.string.menu_open)).performClick()
        compose.onNodeWithText(libraryText(SettingsR.string.settings_title)).performClick()
        compose.onNodeWithText(libraryText(SettingsR.string.settings_title).uppercase()).assertIsDisplayed()
        openAbout()
        compose.onNodeWithText(libraryText(AboutR.string.about_title).uppercase()).assertIsDisplayed()

        compose.activityRule.scenario.recreate()
        compose.onNodeWithText(libraryText(AboutR.string.about_title).uppercase()).assertIsDisplayed()

        compose.activity.assertBackLeavesActivity()
    }

    @Test
    fun menuReturnsToLibraryFromAbout() {
        openAbout()
        compose.onNodeWithContentDescription(libraryText(R.string.menu_open)).performClick()
        compose.onNodeWithText(libraryText(R.string.menu_library)).performClick()
        compose.onNodeWithText(libraryText(LibraryR.string.library_title)).assertIsDisplayed()
    }

    @Test
    fun openDrawerSurvivesRecreationAndBackReturnsToTheSelectedTab() {
        val albums = compose.onNodeWithText(libraryText(LibraryR.string.library_tab_albums))
        albums.performClick().assertIsSelected()
        compose.onNodeWithContentDescription(libraryText(R.string.menu_open)).performClick()
        compose.onNodeWithTag("section_drawer").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText(libraryText(R.string.menu_library)).assertIsSelected()
        pressBack()
        compose.onNodeWithTag("section_drawer").assertDoesNotExist()
        albums.assertIsSelected()
    }

    private fun openAbout() {
        compose.onNodeWithContentDescription(libraryText(R.string.menu_open)).performClick()
        compose.onNodeWithText(libraryText(AboutR.string.about_title)).performClick()
    }

    @Test
    fun systemBackAtRootLeavesTheActivity() {
        compose.onNodeWithText(libraryText(LibraryR.string.library_title)).assertIsDisplayed()
        compose.activity.assertBackLeavesActivity()
    }
}

internal fun MainActivity.assertBackLeavesActivity() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val stopped = CountDownLatch(1)
    instrumentation.runOnMainSync {
        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) stopped.countDown()
        })
        onBackPressedDispatcher.onBackPressed()
    }
    // Android 12+ backgrounds the root launcher task instead of finishing its Activity.
    assertTrue("System Back must leave the root screen", stopped.await(5, TimeUnit.SECONDS))
}
