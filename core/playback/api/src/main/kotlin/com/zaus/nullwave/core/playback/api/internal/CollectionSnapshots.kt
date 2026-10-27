package com.zaus.nullwave.core.playback.api.internal

/** Copies inputs and prevents mutation through a cast or an iterator on the published view. */
internal fun <T> List<T>.snapshotList(): List<T> = SnapshotList(this)

internal fun <T> Set<T>.snapshotSet(): Set<T> = SnapshotSet(this)

private class SnapshotList<T>(source: List<T>) : AbstractList<T>() {
    private val values = source.toList()
    override val size: Int get() = values.size
    override fun get(index: Int): T = values[index]
}

private class SnapshotSet<T>(source: Set<T>) : AbstractSet<T>() {
    private val values = source.toSet()
    override val size: Int get() = values.size
    override fun contains(element: T): Boolean = values.contains(element)
    override fun iterator(): Iterator<T> {
        val source = values.iterator()
        return object : Iterator<T> {
            override fun hasNext(): Boolean = source.hasNext()
            override fun next(): T = source.next()
        }
    }
}
