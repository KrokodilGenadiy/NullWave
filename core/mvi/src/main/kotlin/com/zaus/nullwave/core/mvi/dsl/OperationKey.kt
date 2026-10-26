package com.zaus.nullwave.core.mvi.dsl

/**
 * Stable identity shared by an operation declaration and its UI. Create once, outside define and
 * composition; equal names do not make different keys interchangeable. The input type stays typed.
 * The key contains no running state and can be reused by independent ViewModel instances.
 */
class OperationKey<A>(val name: String) {
    init { require(name.isNotBlank()) { "An operation key must have a nonblank name." } }
    override fun toString(): String = name
}
