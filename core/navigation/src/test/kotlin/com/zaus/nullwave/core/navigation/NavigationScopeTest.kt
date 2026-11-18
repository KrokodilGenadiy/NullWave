package com.zaus.nullwave.core.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationScopeTest {
    private data object Root : NavKey
    private data object Details : NavKey
    private data object Album : NavKey

    private fun scope() = object : NavigationScope {
        override val backStack = NavBackStack<NavKey>(Root)
    }

    @Test
    fun repeatedTapDoesNotAddDuplicateDestination() {
        with(scope()) {
            navigateTo(Root, Details)
            navigateTo(Root, Details)
            assertEquals(listOf(Root, Details), backStack.toList())
        }
    }

    @Test
    fun lateForwardAfterBackCannotOpenAnotherDestination() {
        with(scope()) {
            navigateTo(Root, Details)
            navigateBack(Details)
            navigateTo(Details, Album)
            assertEquals(listOf(Root), backStack.toList())
        }
    }

    @Test
    fun rapidTapsOnDifferentDestinationsOnlyAcceptTheFirst() {
        with(scope()) {
            navigateTo(Root, Details)
            navigateTo(Root, Album)
            assertEquals(listOf(Root, Details), backStack.toList())
        }
    }

    @Test
    fun currentEntryCanOpenTheNextDestination() {
        with(scope()) {
            navigateTo(Root, Details)
            navigateTo(Details, Album)
            assertEquals(listOf(Root, Details, Album), backStack.toList())
        }
    }

    @Test
    fun navigatingToTheCurrentDestinationDoesNotDuplicateIt() {
        with(scope()) {
            navigateTo(Root, Root)
            assertEquals(listOf(Root), backStack.toList())
        }
    }

    @Test
    fun backReturnsToRootAndCannotRemoveIt() {
        with(scope()) {
            navigateTo(Root, Details)
            navigateBack(Details)
            navigateBack(Root)
            assertEquals(listOf(Root), backStack.toList())
        }
    }
}
