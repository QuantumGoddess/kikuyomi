package eu.kanade.tachiyomi.util.audiobookchapter

import eu.kanade.tachiyomi.data.download.audiobook.AudiobookDownloadCache
import tachiyomi.domain.entries.audiobook.model.Audiobook
import tachiyomi.domain.items.audiobookchapter.model.Chapter
import tachiyomi.source.local.entries.audiobook.isLocal
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Returns a copy of the list with not downloaded chapters removed.
 */
fun List<Chapter>.filterDownloadedChapters(audiobook: Audiobook): List<Chapter> {
    if (audiobook.isLocal()) return this

    val downloadCache: AudiobookDownloadCache = Injekt.get()

    return filter {
        downloadCache.isChapterDownloaded(
            it.name,
            it.scanlator,
            audiobook.title,
            audiobook.source,
            false,
        )
    }
}
