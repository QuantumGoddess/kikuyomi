package eu.kanade.domain.download.audiobook.interactor

import eu.kanade.tachiyomi.data.download.audiobook.AudiobookDownloadManager
import tachiyomi.core.util.lang.withNonCancellableContext
import tachiyomi.domain.entries.audiobook.model.Audiobook
import tachiyomi.domain.items.audiobookchapter.model.Chapter
import tachiyomi.domain.source.audiobook.service.AudiobookSourceManager

class DeleteChapterDownload(
    private val sourceManager: AudiobookSourceManager,
    private val downloadManager: AudiobookDownloadManager,
) {

    suspend fun awaitAll(audiobook: Audiobook, vararg chapters: Chapter) = withNonCancellableContext {
        sourceManager.get(audiobook.source)?.let { source ->
            downloadManager.deleteChapters(chapters.toList(), audiobook, source)
        }
    }
}
