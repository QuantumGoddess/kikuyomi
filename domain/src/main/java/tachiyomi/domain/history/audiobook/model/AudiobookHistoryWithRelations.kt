package tachiyomi.domain.history.audiobook.model

import tachiyomi.domain.entries.audiobook.model.AudiobookCover
import java.util.Date

data class AudiobookHistoryWithRelations(
    val id: Long,
    val chapterId: Long,
    val audiobookId: Long,
    val title: String,
    val chapterNumber: Double,
    val readAt: Date?,
    val coverData: AudiobookCover,
)
