package tachiyomi.domain.updates.audiobook.model

import tachiyomi.domain.entries.audiobook.model.AudiobookCover

data class AudiobookUpdatesWithRelations(
    val audiobookId: Long,
    val audiobookTitle: String,
    val chapterId: Long,
    val chapterName: String,
    val scanlator: String?,
    val read: Boolean,
    val bookmark: Boolean,
    val lastSecondRead: Long,
    val totalSeconds: Long,
    val sourceId: Long,
    val dateFetch: Long,
    val coverData: AudiobookCover,
)
