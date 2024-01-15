package tachiyomi.domain.items.audiobookchapter.interactor

import logcat.LogPriority
import tachiyomi.core.util.system.logcat
import tachiyomi.domain.items.audiobookchapter.model.Chapter
import tachiyomi.domain.items.audiobookchapter.repository.ChapterRepository

class GetChaptersByAudiobookId(
    private val chapterRepository: ChapterRepository,
) {

    suspend fun await(audiobookId: Long): List<Chapter> {
        return try {
            chapterRepository.getChapterByAudiobookId(audiobookId)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            emptyList()
        }
    }
}
