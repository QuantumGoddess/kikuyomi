package tachiyomi.domain.items.audiobookchapter.interactor

import tachiyomi.domain.items.audiobookchapter.model.Chapter
import tachiyomi.domain.items.audiobookchapter.repository.ChapterRepository

class GetChapterByUrlAndAudiobookId(
    private val chapterRepository: ChapterRepository,
) {

    suspend fun await(url: String, sourceId: Long): Chapter? {
        return try {
            chapterRepository.getChapterByUrlAndAudiobookId(url, sourceId)
        } catch (e: Exception) {
            null
        }
    }
}
