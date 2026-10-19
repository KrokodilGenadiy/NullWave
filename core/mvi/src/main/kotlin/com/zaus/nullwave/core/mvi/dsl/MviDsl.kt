package com.zaus.nullwave.core.mvi.dsl

/** Keeps implicit receivers confined to the current MVI declaration or handler. */
@DslMarker
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE)
annotation class MviDsl
