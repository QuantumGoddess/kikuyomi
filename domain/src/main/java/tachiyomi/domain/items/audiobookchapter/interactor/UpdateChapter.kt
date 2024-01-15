package tachiyomi.domain.items.audiobookchapter.interactor

import logcat.LogPriority
import tachiyomi.core.util.system.logcat
import tachiyomi.domain.items.audiobookchapter.model.ChapterUpdate
import tachiyomi.domain.items.audiobookchapter.repository.ChapterRepository

class UpdateChapter(
    private val chapterRepository: ChapterRepository,
) {

    suspend fun await(chapterUpdate: ChapterUpdate) {
        try {
            chapterRepository.updateChapter(chapterUpdate)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
        }
    }

    suspend fun awaitAll(chapterUpdates: List<ChapterUpdate>) {
        try {
            chapterRepository.updateAllChapters(chapterUpdates)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
        }
    }
}
