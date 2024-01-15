package tachiyomi.domain.history.audiobook.model

import java.util.Date

data class AudiobookHistoryUpdate(
    val chapterId: Long,
    val readAt: Date,
)
