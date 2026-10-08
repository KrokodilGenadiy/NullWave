package com.zaus.nullwave

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class NavigationInstrumentedTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun featureRegistrationNavigationRecreationAndSystemBack() {
        compose.onNodeWithText("Библиотека").assertIsDisplayed()
        compose.onNodeWithText("О приложении").performClick()
        compose.onNodeWithText("Назад").assertIsDisplayed()

        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Назад").assertIsDisplayed()

        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("Библиотека").assertIsDisplayed()
    }

    @Test
    fun inAppBackReturnsToLibrary() {
        compose.onNodeWithText("О приложении").performClick()
        compose.onNodeWithText("Назад").performClick()
        compose.onNodeWithText("Библиотека").assertIsDisplayed()
    }

    @Test
    fun systemBackAtRootLeavesTheActivity() {
        compose.onNodeWithText("Библиотека").assertIsDisplayed()
        val activity = compose.activity
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val stopped = CountDownLatch(1)
        instrumentation.runOnMainSync {
            activity.lifecycle.addObserver(LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) stopped.countDown()
            })
            activity.onBackPressedDispatcher.onBackPressed()
        }
        // Android 12+ backgrounds the root launcher task instead of finishing its Activity.
        assertTrue("System Back must leave the root screen", stopped.await(5, TimeUnit.SECONDS))
    }
}
