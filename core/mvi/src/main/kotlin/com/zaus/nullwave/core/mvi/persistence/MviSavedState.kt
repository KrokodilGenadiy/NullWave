package com.zaus.nullwave.core.mvi.persistence

import androidx.lifecycle.SavedStateHandle
import com.zaus.nullwave.core.mvi.UiState
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

/**
 * Optional, single-VM persistence binding. Create it with [savedState] and pass it to BaseMviViewModel.
 * It restores before the initial State is exposed; only the runtime may save accepted transitions.
 * The handle/key belongs exclusively to this binding: UI observes the VM State, not this payload.
 */
class MviSavedState<S : UiState> internal constructor(
    private val bind: (S) -> SavedStateBinding<S>,
) {
    private var attached = false

    internal fun attach(defaults: S): SavedStateBinding<S> {
        check(!attached) { "An MVI saved-state binding belongs to one ViewModel." }
        attached = true
        return bind(defaults)
    }
}

/** Read-only access to old keys. Only keys read during migration are removed after a successful save. */
class LegacySavedState internal constructor(private val read: (String) -> Any?) {
    internal val keys = mutableSetOf<String>()

    fun string(key: String): String? {
        keys += key
        return read(key) as? String
    }
}

/**
 * Saves one versioned JSON String under [key], never the whole State by default.
 * [select] returns a small immutable payload with value equality; unchanged payloads are not encoded
 * or written again. [restore] overlays that payload on fresh defaults (including the new locale).
 * Both transforms and the serializer must be pure. Decode failures/unsupported versions use defaults;
 * encode/transform failures are programming errors and propagate before State/source revisions change.
 *
 * [legacy] is consulted only when [key] is absent, never to resurrect stale keys after malformed/newer
 * data. Bump [version] for incompatible schemas; this version falls back to defaults for other versions.
 * SavedStateHandle is an Android restoration checkpoint, not durable storage or an exactly-once log.
 */
fun <S : UiState, P : Any> savedState(
    handle: SavedStateHandle,
    key: String,
    serializer: KSerializer<P>,
    version: Int = 1,
    select: S.() -> P,
    restore: S.(P) -> S,
    legacy: (LegacySavedState.() -> P?)? = null,
): MviSavedState<S> = jsonSavedState(HandleStateStore(handle), key, serializer, version, select, restore, legacy)

internal fun <S : UiState, P : Any> jsonSavedState(
    store: SavedStateStore,
    key: String,
    serializer: KSerializer<P>,
    version: Int = 1,
    select: S.() -> P,
    restore: S.(P) -> S,
    legacy: (LegacySavedState.() -> P?)? = null,
): MviSavedState<S> {
    require(key.isNotBlank()) { "Saved-state key must not be blank." }
    require(version > 0) { "Saved-state version must be positive." }
    return MviSavedState { defaults ->
        val old = LegacySavedState(store::get)
        val payload = if (store.contains(key)) {
            decodePayload(store.get(key), version, serializer)
        } else {
            legacy?.invoke(old)
        }
        check(key !in old.keys) { "A legacy key must differ from the payload key." }
        val restored = payload?.let { restore(defaults, it) } ?: defaults
        var previous: P? = null
        SavedStateBinding(restored) { next ->
            val selected = select(next)
            if (selected == previous) {
                null
            } else {
                // Complete potentially throwing user serialization before writing anything.
                val encoded = buildJsonObject {
                    put("version", version)
                    put("payload", persistenceJson.encodeToJsonElement(serializer, selected))
                }.toString()
                PreparedSavedState {
                    store.set(key, encoded)
                    old.keys.forEach(store::remove)
                    old.keys.clear()
                    previous = selected
                }
            }
        }
    }
}

private val persistenceJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

private fun <P> decodePayload(raw: Any?, version: Int, serializer: KSerializer<P>): P? {
    if (raw !is String) return null
    return try {
        val envelope = persistenceJson.parseToJsonElement(raw) as? JsonObject ?: return null
        val savedVersion = envelope["version"] as? JsonPrimitive ?: return null
        if (savedVersion.isString || savedVersion.intOrNull != version) return null
        val payload = envelope["payload"] ?: return null
        // The streaming decoder also rejects object/array payloads for primitive serializers.
        // The tree decoder in serialization 1.11 can throw IndexOutOfBoundsException for that input.
        persistenceJson.decodeFromString(serializer, payload.toString())
    } catch (_: IllegalArgumentException) {
        // Includes SerializationException and invalid constructor arguments from decoded data.
        null
    }
}

internal class SavedStateBinding<S : UiState>(
    val initialState: S,
    val prepare: (S) -> PreparedSavedState?,
)

internal class PreparedSavedState(val write: () -> Unit)

internal interface SavedStateStore {
    fun contains(key: String): Boolean
    fun get(key: String): Any?
    fun set(key: String, value: String)
    fun remove(key: String)
}

private class HandleStateStore(private val handle: SavedStateHandle) : SavedStateStore {
    override fun contains(key: String): Boolean = handle.contains(key)
    override fun get(key: String): Any? = handle.get<Any?>(key)
    override fun set(key: String, value: String) { handle[key] = value }
    override fun remove(key: String) { handle.remove<Any?>(key) }
}
