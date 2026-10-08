package com.zaus.nullwave.core.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationScopeTest {
    private data object Root : NavKey
    private data object Details : NavKey

    private fun scope() = object : NavigationScope {
        override val backStack = NavBackStack<NavKey>(Root)
    }

    @Test
    fun repeatedTapDoesNotAddDuplicateDestination() {
        with(scope()) {
            navigateTo(Details)
            navigateTo(Details)
            assertEquals(listOf(Root, Details), backStack.toList())
        }
    }

    @Test
    fun backReturnsToRootAndCannotRemoveIt() {
        with(scope()) {
            navigateTo(Details)
            navigateBack()
            navigateBack()
            assertEquals(listOf(Root), backStack.toList())
        }
    }
}
