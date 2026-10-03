package com.zaus.nullwave.di

import androidx.lifecycle.ViewModel
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.ViewModelAssistedFactory
import kotlin.reflect.KClass

/**
 * The one concrete [MetroViewModelFactory] in the app.
 *
 * MetroX ships [MetroViewModelFactory] as an `abstract class` with no binding of its own - verified by
 * disassembling `metrox-viewmodel-1.4.4` and reading its sources jar: nothing in that artifact carries a
 * Metro annotation. So `AppGraph : ViewModelGraph` alone does not make the factory injectable; it only
 * declares the three `@Multibinds(allowEmpty = true)` maps and an accessor. This class is what closes
 * that gap, and it is the installation the library's own KDoc recommends.
 *
 * The three maps are injected rather than looked up. Each is a Metro multibinding that features fill in
 * with `@ContributesIntoMap(AppScope::class) @ViewModelKey(SomeViewModel::class)`, so this file never
 * names a ViewModel - the same self-registration as `EntryProviderInstaller` and [TopLevelDestination].
 * `allowEmpty = true` on the declarations is why the app still builds with no ViewModels contributed yet.
 *
 * `@SingleIn(AppScope::class)` because the factory is stateless plumbing over those maps and is read on
 * every screen; there is nothing to gain from rebuilding it. Lifetime is not a concern: it holds
 * providers, not ViewModels. What owns the ViewModels is the `ViewModelStore`, and
 * `rememberViewModelStoreNavEntryDecorator()` in `MainActivity` scopes those to a back-stack entry.
 *
 * Overriding all three maps even though only `viewModelProviders` is used today: they default to
 * `emptyMap()`, so leaving the other two out would silently make assisted ViewModels fail at runtime
 * with "Unknown model class" rather than at compile time. The cost of wiring them now is one line each.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class NullWaveViewModelFactory(
    override val viewModelProviders: Map<KClass<out ViewModel>, () -> ViewModel>,
    override val assistedFactoryProviders: Map<KClass<out ViewModel>, () -> ViewModelAssistedFactory>,
    override val manualAssistedFactoryProviders:
        Map<KClass<out ManualViewModelAssistedFactory>, () -> ManualViewModelAssistedFactory>,
) : MetroViewModelFactory()
