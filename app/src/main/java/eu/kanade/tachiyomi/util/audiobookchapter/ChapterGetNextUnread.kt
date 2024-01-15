package eu.kanade.tachiyomi.util.audiobookchapter

import eu.kanade.domain.items.audiobookchapter.model.applyFilters
import eu.kanade.tachiyomi.data.download.audiobook.AudiobookDownloadManager
import eu.kanade.tachiyomi.ui.entries.audiobook.ChapterList
import tachiyomi.domain.entries.audiobook.model.Audiobook
import tachiyomi.domain.items.audiobookchapter.model.Chapter

/**
 * Gets next unread chapter with filters and sorting applied
 */
fun List<Chapter>.getNextUnread(audiobook: Audiobook, downloadManager: AudiobookDownloadManager): Chapter? {
    return applyFilters(audiobook, downloadManager).let { chapters ->
        if (audiobook.sortDescending()) {
            chapters.findLast { !it.read }
        } else {
            chapters.find { !it.read }
        }
    }
}

/**
 * Gets next unread chapter with filters and sorting applied
 */
fun List<ChapterList.Item>.getNextUnread(audiobook: Audiobook): Chapter? {
    return applyFilters(audiobook).let { chapters ->
        if (audiobook.sortDescending()) {
            chapters.findLast { !it.chapter.read }
        } else {
            chapters.find { !it.chapter.read }
        }
    }?.chapter
}
