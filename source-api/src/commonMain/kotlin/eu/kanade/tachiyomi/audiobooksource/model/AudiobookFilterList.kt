package eu.kanade.tachiyomi.audiobooksource.model

data class AudiobookFilterList(val list: List<AudiobookFilter<*>>) : List<AudiobookFilter<*>> by list {

    constructor(vararg fs: AudiobookFilter<*>) : this(if (fs.isNotEmpty()) fs.asList() else emptyList())

    override fun equals(other: Any?): Boolean {
        return false
    }

    override fun hashCode(): Int {
        return list.hashCode()
    }
}
