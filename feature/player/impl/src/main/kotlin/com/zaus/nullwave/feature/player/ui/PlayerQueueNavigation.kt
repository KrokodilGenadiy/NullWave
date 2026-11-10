package com.zaus.nullwave.feature.player.ui

import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LocalLifecycleOwner

internal class PlayerQueueNavigation(open: Boolean) : OnBackPressedCallback(open) {
    var queueOpen by mutableStateOf(open)
        private set

    fun showQueue(open: Boolean) {
        queueOpen = open
        // Back can arrive before Compose draws the requested page.
        isEnabled = open
    }

    override fun handleOnBackPressed() = showQueue(false)
}

private val QueueNavigationSaver = Saver<PlayerQueueNavigation, Boolean>(
    save = { it.queueOpen },
    restore = { PlayerQueueNavigation(it) },
)

@Composable
internal fun rememberPlayerQueueNavigation(): PlayerQueueNavigation {
    val navigation = rememberSaveable(saver = QueueNavigationSaver) { PlayerQueueNavigation(false) }
    val dispatcher = checkNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
    val owner = LocalLifecycleOwner.current
    DisposableEffect(dispatcher, owner, navigation) {
        dispatcher.addCallback(owner, navigation)
        onDispose { navigation.remove() }
    }
    return navigation
}
