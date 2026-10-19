package com.zaus.nullwave.core.mvi.ui

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource

/** A value held in State; resources are resolved with the UI's current locale, not in a ViewModel. */
@Immutable
sealed interface UiText {
    data class Literal(val value: String) : UiText

    /** Copies its arguments and never exposes their backing collection. */
    class Resource(
        @param:StringRes val id: Int,
        vararg arguments: Argument,
    ) : UiText {
        private val arguments = arguments.toList()

        internal val argumentCount: Int get() = arguments.size
        internal fun argumentAt(index: Int): Argument = arguments[index]

        override fun equals(other: Any?): Boolean =
            other is Resource && id == other.id && arguments == other.arguments

        override fun hashCode(): Int = 31 * id + arguments.hashCode()

        override fun toString(): String = "Resource(id=$id, arguments=$arguments)"
    }

    /** Only immutable values can become formatting arguments; nested resources keep their locale. */
    @Immutable
    sealed interface Argument {
        data class Text(val value: UiText) : Argument {
            constructor(value: String) : this(Literal(value))
        }

        data class Count(val value: Long) : Argument {
            constructor(value: Int) : this(value.toLong())
        }

        data class Decimal(val value: Double) : Argument
    }
}

/** For non-composable Android callers; pass their current configuration Context. */
fun UiText.asString(context: Context): String = when (this) {
    is UiText.Literal -> value
    is UiText.Resource -> if (argumentCount == 0) context.getString(id) else context.getString(
        id,
        *Array<Any>(argumentCount) { index ->
            when (val argument = argumentAt(index)) {
                is UiText.Argument.Text -> argument.value.asString(context)
                is UiText.Argument.Count -> argument.value
                is UiText.Argument.Decimal -> argument.value
            }
        },
    )
}

/** Uses Compose resources so a locale change also invalidates nested resource arguments. */
@Composable
@ReadOnlyComposable
fun UiText.asString(): String = when (this) {
    is UiText.Literal -> value
    is UiText.Resource -> if (argumentCount == 0) {
        stringResource(id)
    } else {
        // A loop permits composable resolution of nested text before passing ordinary arguments.
        val arguments = Array<Any>(argumentCount) { Unit }
        for (index in arguments.indices) {
            arguments[index] = when (val argument = argumentAt(index)) {
                is UiText.Argument.Text -> argument.value.asString()
                is UiText.Argument.Count -> argument.value
                is UiText.Argument.Decimal -> argument.value
            }
        }
        stringResource(id, *arguments)
    }
}
