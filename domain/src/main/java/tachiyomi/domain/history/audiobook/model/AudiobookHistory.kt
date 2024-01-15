package tachiyomi.domain.history.audiobook.model

import java.util.Date

data class AudiobookHistory(
    val id: Long,
    val chapterId: Long,
    val readAt: Date?,
)
