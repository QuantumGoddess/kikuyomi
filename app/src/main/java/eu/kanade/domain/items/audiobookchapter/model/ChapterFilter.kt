package eu.kanade.domain.items.audiobookchapter.model

import eu.kanade.domain.entries.audiobook.model.downloadedFilter
import eu.kanade.tachiyomi.data.download.audiobook.AudiobookDownloadManager
import eu.kanade.tachiyomi.ui.entries.audiobook.ChapterList
import tachiyomi.domain.entries.audiobook.model.Audiobook
import tachiyomi.domain.entries.applyFilter
import tachiyomi.domain.items.audiobookchapter.model.Chapter
import tachiyomi.domain.items.audiobookchapter.service.getChapterSort
import tachiyomi.source.local.entries.audiobook.isLocal

/**
 * Applies the view filters to the list of chapters obtained from the database.
 * @return an observable of the list of chapters filtered and sorted.
 */
fun List<Chapter>.applyFilters(audiobook: Audiobook, downloadManager: AudiobookDownloadManager): List<Chapter> {
    val isLocalAudiobook = audiobook.isLocal()
    val unreadFilter = audiobook.unreadFilter
    val downloadedFilter = audiobook.downloadedFilter
    val bookmarkedFilter = audiobook.bookmarkedFilter

    return filter { chapter -> applyFilter(unreadFilter) { !chapter.read } }
        .filter { chapter -> applyFilter(bookmarkedFilter) { chapter.bookmark } }
        .filter { chapter ->
            applyFilter(downloadedFilter) {
                val downloaded = downloadManager.isChapterDownloaded(
                    chapter.name,
                    chapter.scanlator,
                    audiobook.title,
                    audiobook.source,
                )
                downloaded || isLocalAudiobook
            }
        }
        .sortedWith(getChapterSort(audiobook))
}

/**
 * Applies the view filters to the list of chapters obtained from the database.
 * @return an observable of the list of chapters filtered and sorted.
 */
fun List<ChapterList.Item>.applyFilters(audiobook: Audiobook): Sequence<ChapterList.Item> {
    val isLocalAudiobook = audiobook.isLocal()
    val unreadFilter = audiobook.unreadFilter
    val downloadedFilter = audiobook.downloadedFilter
    val bookmarkedFilter = audiobook.bookmarkedFilter
    return asSequence()
        .filter { (chapter) -> applyFilter(unreadFilter) { !chapter.read } }
        .filter { (chapter) -> applyFilter(bookmarkedFilter) { chapter.bookmark } }
        .filter { applyFilter(downloadedFilter) { it.isDownloaded || isLocalAudiobook } }
        .sortedWith { (chapter1), (chapter2) -> getChapterSort(audiobook).invoke(chapter1, chapter2) }
}
