package tachiyomi.domain.source.audiobook.model

data class AudiobookSourceWithCount(
    val source: AudiobookSource,
    val count: Long,
) {

    val id: Long
        get() = source.id

    val name: String
        get() = source.name
}
