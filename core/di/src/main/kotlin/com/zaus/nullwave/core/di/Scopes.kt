package com.zaus.nullwave.core.di

/**
 * Scope marker for things that live as long as an Activity.
 *
 * Metro scopes are ordinary marker classes - there is no annotation to define. Use it as
 * `@SingleIn(ActivityScope::class)` for a binding, `@ContributesBinding(ActivityScope::class)` to
 * contribute one, and it is listed in `AppGraph`'s `additionalScopes` so those contributions
 * aggregate.
 */
abstract class ActivityScope private constructor()
