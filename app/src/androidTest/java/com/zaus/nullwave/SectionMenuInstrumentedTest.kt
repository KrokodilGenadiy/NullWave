package com.zaus.nullwave

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.zaus.nullwave.core.designsystem.theme.NullWaveTheme
import com.zaus.nullwave.feature.library.api.LibraryKey
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SectionMenuInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private fun text(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @Test fun backBeforeFirstOpeningFrameCancelsMenuAndLeavesRootBackAvailable() {
        var rootBacks = 0
        lateinit var dispatcher: OnBackPressedDispatcher
        compose.setContent {
            dispatcher = checkNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
            BackHandler { rootBacks++ }
            NullWaveTheme { SectionMenu(LibraryKey, null) { error("Unexpected destination: $it") } }
        }
        val menu = compose.onNodeWithContentDescription(text(R.string.menu_open))
        val drawer = compose.onNodeWithTag("section_drawer")
        val click = checkNotNull(menu.fetchSemanticsNode().config[SemanticsActions.OnClick].action)
        compose.mainClock.autoAdvance = false
        try {
            // Same main-thread turn: neither recomposition nor an animation frame can intervene.
            compose.runOnUiThread {
                click()
                dispatcher.onBackPressed()
                assertEquals("Opening Back must belong to the menu", 0, rootBacks)
            }
        } finally {
            compose.mainClock.autoAdvance = true
        }
        drawer.assertDoesNotExist()
        menu.performClick()
        drawer.assertIsDisplayed()
        compose.onNodeWithText(text(R.string.menu_library)).performClick()
        drawer.assertDoesNotExist()
        compose.runOnIdle {
            dispatcher.onBackPressed()
            assertEquals("Closed menu must release Back", 1, rootBacks)
        }
    }

    @Test fun scrimAndCurrentSectionDismissWithoutNavigating() {
        compose.setContent {
            NullWaveTheme { SectionMenu(LibraryKey, null) { error("Unexpected navigation to $it") } }
        }
        val menu = compose.onNodeWithContentDescription(text(R.string.menu_open))
        val drawer = compose.onNodeWithTag("section_drawer")
        menu.performClick()
        drawer.assertIsDisplayed().performTouchInput { click(Offset(width + 4.dp.toPx(), centerY)) }
        drawer.assertDoesNotExist()
        menu.performClick()
        compose.onNodeWithText(text(R.string.menu_library)).assertIsSelected().performClick()
        drawer.assertDoesNotExist()
    }

    @Test fun externalLinksReportAMissingHandlerAndAllowAnotherChoice() {
        val opened = mutableListOf<String>()
        val handler = object : UriHandler {
            override fun openUri(uri: String) {
                opened += uri
                if (opened.size == 1) throw IllegalArgumentException("No handler")
            }
        }
        compose.setContent {
            CompositionLocalProvider(LocalUriHandler provides handler) {
                NullWaveTheme { SectionMenu(LibraryKey, null) {} }
            }
        }
        compose.onNodeWithContentDescription(text(R.string.menu_open)).performClick()
        compose.onNodeWithText("Bandcamp ↗").performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.menu_link_error)).assertExists()
        compose.onNodeWithText("Spotify ↗").performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.menu_link_error)).assertDoesNotExist()
        compose.runOnIdle { assertEquals(listOf("https://bandcamp.com/", "https://open.spotify.com/"), opened) }
    }
}
