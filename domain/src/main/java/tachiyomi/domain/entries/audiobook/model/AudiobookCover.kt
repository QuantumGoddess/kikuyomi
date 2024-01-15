package tachiyomi.domain.entries.audiobook.model

import tachiyomi.domain.entries.EntryCover

/**
 * Contains the required data for AudiobookCoverFetcher
 */
data class AudiobookCover(
    val audiobookId: Long,
    val sourceId: Long,
    val isAudiobookFavorite: Boolean,
    val url: String?,
    val lastModified: Long,
) : EntryCover

fun Audiobook.asAudiobookCover(): AudiobookCover {
    return AudiobookCover(
        audiobookId = id,
        sourceId = source,
        isAudiobookFavorite = favorite,
        url = thumbnailUrl,
        lastModified = coverLastModified,
    )
}
