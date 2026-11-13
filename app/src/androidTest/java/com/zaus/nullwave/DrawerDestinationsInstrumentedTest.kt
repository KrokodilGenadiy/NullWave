package com.zaus.nullwave

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import com.zaus.nullwave.core.designsystem.R as DesignR
import com.zaus.nullwave.feature.equalizer.api.R as EqualizerR
import com.zaus.nullwave.feature.library.impl.R as LibraryR
import com.zaus.nullwave.feature.settings.api.R as SettingsR
import com.zaus.nullwave.feature.sleeptimer.api.R as SleepTimerR
import org.junit.Rule
import org.junit.Test

class DrawerDestinationsInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun equalizerOpensAndRestores() = verifyDestination(EqualizerR.string.equalizer_title)
    @Test fun sleepTimerOpensAndRestores() = verifyDestination(SleepTimerR.string.sleeptimer_title)
    @Test fun settingsOpensAndRestores() = verifyDestination(SettingsR.string.settings_title)

    private fun verifyDestination(titleId: Int) {
        val title = compose.activity.getString(titleId)
        val message = compose.activity.getString(DesignR.string.nw_feature_in_development)
        val menu = compose.onNodeWithContentDescription(compose.activity.getString(R.string.menu_open))
        val albums = compose.onNodeWithText(compose.activity.getString(LibraryR.string.library_tab_albums))
        albums.performClick().assertIsSelected()
        menu.performClick()
        compose.onNodeWithText(title).performScrollTo().performClick()
        compose.onNodeWithText(message).assertIsDisplayed()
        compose.onNodeWithTag("library_screen").assertDoesNotExist()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText(compose.activity.screenTitle(titleId)).assertIsDisplayed()
        menu.performClick()
        compose.onNode(hasText(title) and hasAnyAncestor(hasTestTag("section_drawer"))).assertIsSelected()
        compose.onNodeWithText(compose.activity.getString(R.string.menu_library)).performClick()
        albums.assertIsSelected()
        compose.onNodeWithTag("section_drawer").assertDoesNotExist()

        menu.performClick()
        compose.onNodeWithText(title).performScrollTo().performClick()
        compose.onNodeWithText(message).assertIsDisplayed()
        compose.activity.assertBackLeavesActivity()
    }
}
