package com.zaus.nullwave.core.navigation

import androidx.annotation.DrawableRes
import androidx.navigation3.runtime.NavKey

/**
 * Where a destination sits in the app-level navigation surface.
 *
 * A feature knows what *kind* of thing it is; it does not know what else exists, so it should not
 * be claiming a global position. It declares a section, and the order of sections is decided once,
 * here, by the enum's declaration order.
 *
 * Adding a feature therefore needs no edit here and no renumbering of anything else.
 */
enum class RailSection {
    /** The library. The app's default destination. */
    Primary,

    /** Equalizer, sleep timer - things you do to playback. */
    Tools,

    /** Settings, About. */
    System,
}

/**
 * An entry in the app-level navigation surface - the rail on wide windows, the modal drawer on
 * phones.
 *
 * Contributed the same way screens are: each feature adds one into a set from its own `di` module,
 * so the host never holds a list of features.
 *
 * ```
 * @IntoSet
 * @Provides
 * fun provideTopLevelDestination() = TopLevelDestination(
 *     key = EqualizerKey, label = "Equalizer", icon = NullWaveIcons.Equalizer,
 *     section = RailSection.Tools,
 * )
 * ```
 *
 * Not every feature contributes one - Search lives in the top bar and the Player is a sheet, not a
 * destination.
 */
data class TopLevelDestination(
    val key: NavKey,
    val label: String,
    @param:DrawableRes val icon: Int,
    val section: RailSection,
)

/**
 * Sections in declaration order, then alphabetically within a section.
 *
 * Alphabetical is a deterministic tie-break that needs no coordination between modules. If a
 * section ever grows enough that its order matters, give [TopLevelDestination] a
 * `positionInSection` hint rather than going back to a global index.
 */
fun Collection<TopLevelDestination>.inDisplayOrder(): List<TopLevelDestination> =
    sortedWith(compareBy({ it.section.ordinal }, { it.label }))
