package eu.kanade.tachiyomi.audiobooksource.model

sealed class AudiobookFilter<T>(val name: String, var state: T) {
    open class Header(name: String) : AudiobookFilter<Any>(name, 0)
    open class Separator(name: String = "") : AudiobookFilter<Any>(name, 0)
    abstract class Select<V>(name: String, val values: Array<V>, state: Int = 0) : AudiobookFilter<Int>(
        name,
        state,
    )
    abstract class Text(name: String, state: String = "") : AudiobookFilter<String>(name, state)
    abstract class CheckBox(name: String, state: Boolean = false) : AudiobookFilter<Boolean>(
        name,
        state,
    )
    abstract class TriState(name: String, state: Int = STATE_IGNORE) : AudiobookFilter<Int>(name, state) {
        fun isIgnored() = state == STATE_IGNORE
        fun isIncluded() = state == STATE_INCLUDE
        fun isExcluded() = state == STATE_EXCLUDE

        companion object {
            const val STATE_IGNORE = 0
            const val STATE_INCLUDE = 1
            const val STATE_EXCLUDE = 2
        }
    }

    abstract class Group<V>(name: String, state: List<V>) : AudiobookFilter<List<V>>(name, state)

    abstract class Sort(name: String, val values: Array<String>, state: Selection? = null) :
        AudiobookFilter<Sort.Selection?>(name, state) {
        data class Selection(val index: Int, val ascending: Boolean)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AudiobookFilter<*>) return false

        return name == other.name && state == other.state
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + (state?.hashCode() ?: 0)
        return result
    }
}
