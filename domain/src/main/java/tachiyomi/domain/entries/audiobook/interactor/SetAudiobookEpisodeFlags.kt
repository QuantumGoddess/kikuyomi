package tachiyomi.domain.entries.audiobook.interactor

import tachiyomi.domain.entries.audiobook.model.Audiobook
import tachiyomi.domain.entries.audiobook.model.AudiobookUpdate
import tachiyomi.domain.entries.audiobook.repository.AudiobookRepository

class SetAudiobookChapterFlags(
    private val audiobookRepository: AudiobookRepository,
) {

    suspend fun awaitSetDownloadedFilter(audiobook: Audiobook, flag: Long): Boolean {
        return audiobookRepository.updateAudiobook(
            AudiobookUpdate(
                id = audiobook.id,
                chapterFlags = audiobook.chapterFlags.setFlag(flag, Audiobook.CHAPTER_DOWNLOADED_MASK),
            ),
        )
    }

    suspend fun awaitSetUnreadFilter(audiobook: Audiobook, flag: Long): Boolean {
        return audiobookRepository.updateAudiobook(
            AudiobookUpdate(
                id = audiobook.id,
                chapterFlags = audiobook.chapterFlags.setFlag(flag, Audiobook.CHAPTER_UNREAD_MASK),
            ),
        )
    }

    suspend fun awaitSetBookmarkFilter(audiobook: Audiobook, flag: Long): Boolean {
        return audiobookRepository.updateAudiobook(
            AudiobookUpdate(
                id = audiobook.id,
                chapterFlags = audiobook.chapterFlags.setFlag(flag, Audiobook.CHAPTER_BOOKMARKED_MASK),
            ),
        )
    }

    suspend fun awaitSetDisplayMode(audiobook: Audiobook, flag: Long): Boolean {
        return audiobookRepository.updateAudiobook(
            AudiobookUpdate(
                id = audiobook.id,
                chapterFlags = audiobook.chapterFlags.setFlag(flag, Audiobook.CHAPTER_DISPLAY_MASK),
            ),
        )
    }

    suspend fun awaitSetSortingModeOrFlipOrder(audiobook: Audiobook, flag: Long): Boolean {
        val newFlags = audiobook.chapterFlags.let {
            if (audiobook.sorting == flag) {
                // Just flip the order
                val orderFlag = if (audiobook.sortDescending()) {
                    Audiobook.CHAPTER_SORT_ASC
                } else {
                    Audiobook.CHAPTER_SORT_DESC
                }
                it.setFlag(orderFlag, Audiobook.CHAPTER_SORT_DIR_MASK)
            } else {
                // Set new flag with ascending order
                it
                    .setFlag(flag, Audiobook.CHAPTER_SORTING_MASK)
                    .setFlag(Audiobook.CHAPTER_SORT_ASC, Audiobook.CHAPTER_SORT_DIR_MASK)
            }
        }
        return audiobookRepository.updateAudiobook(
            AudiobookUpdate(
                id = audiobook.id,
                chapterFlags = newFlags,
            ),
        )
    }

    suspend fun awaitSetAllFlags(
        audiobookId: Long,
        unreadFilter: Long,
        downloadedFilter: Long,
        bookmarkedFilter: Long,
        sortingMode: Long,
        sortingDirection: Long,
        displayMode: Long,
    ): Boolean {
        return audiobookRepository.updateAudiobook(
            AudiobookUpdate(
                id = audiobookId,
                chapterFlags = 0L.setFlag(unreadFilter, Audiobook.CHAPTER_UNREAD_MASK)
                    .setFlag(downloadedFilter, Audiobook.CHAPTER_DOWNLOADED_MASK)
                    .setFlag(bookmarkedFilter, Audiobook.CHAPTER_BOOKMARKED_MASK)
                    .setFlag(sortingMode, Audiobook.CHAPTER_SORTING_MASK)
                    .setFlag(sortingDirection, Audiobook.CHAPTER_SORT_DIR_MASK)
                    .setFlag(displayMode, Audiobook.CHAPTER_DISPLAY_MASK),
            ),
        )
    }

    private fun Long.setFlag(flag: Long, mask: Long): Long {
        return this and mask.inv() or (flag and mask)
    }
}
