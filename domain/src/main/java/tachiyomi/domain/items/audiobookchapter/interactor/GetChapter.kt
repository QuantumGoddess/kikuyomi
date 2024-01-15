package tachiyomi.domain.items.audiobookchapter.interactor

import logcat.LogPriority
import tachiyomi.core.util.system.logcat
import tachiyomi.domain.items.audiobookchapter.model.Chapter
import tachiyomi.domain.items.audiobookchapter.repository.ChapterRepository

class GetChapter(
    private val chapterRepository: ChapterRepository,
) {

    suspend fun await(id: Long): Chapter? {
        return try {
            chapterRepository.getChapterById(id)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            null
        }
    }

    suspend fun await(url: String, audiobookId: Long): Chapter? {
        return try {
            chapterRepository.getChapterByUrlAndAudiobookId(url, audiobookId)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            null
        }
    }
}
