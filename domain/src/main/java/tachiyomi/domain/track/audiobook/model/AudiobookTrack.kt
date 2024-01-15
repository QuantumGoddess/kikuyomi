package tachiyomi.domain.track.audiobook.model

data class AudiobookTrack(
    val id: Long,
    val audiobookId: Long,
    val syncId: Long,
    val remoteId: Long,
    val libraryId: Long?,
    val title: String,
    val lastChapterRead: Double,
    val totalChapters: Long,
    val status: Long,
    val score: Double,
    val remoteUrl: String,
    val startDate: Long,
    val finishDate: Long,
)
