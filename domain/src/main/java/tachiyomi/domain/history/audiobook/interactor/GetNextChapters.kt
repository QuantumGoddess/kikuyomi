package tachiyomi.domain.history.audiobook.interactor

import tachiyomi.domain.entries.audiobook.interactor.GetAudiobook
import tachiyomi.domain.history.audiobook.repository.AudiobookHistoryRepository
import tachiyomi.domain.items.audiobookchapter.interactor.GetChaptersByAudiobookId
import tachiyomi.domain.items.audiobookchapter.model.Chapter
import tachiyomi.domain.items.audiobookchapter.service.getChapterSort
import kotlin.math.max

class GetNextChapters(
    private val getChaptersByAudiobookId: GetChaptersByAudiobookId,
    private val getAudiobook: GetAudiobook,
    private val historyRepository: AudiobookHistoryRepository,
) {

    suspend fun await(onlyUnread: Boolean = true): List<Chapter> {
        val history = historyRepository.getLastAudiobookHistory() ?: return emptyList()
        return await(history.audiobookId, history.chapterId, onlyUnread)
    }

    suspend fun await(audiobookId: Long, onlyUnread: Boolean = true): List<Chapter> {
        val audiobook = getAudiobook.await(audiobookId) ?: return emptyList()
        val chapters = getChaptersByAudiobookId.await(audiobookId)
            .sortedWith(getChapterSort(audiobook, sortDescending = false))

        return if (onlyUnread) {
            chapters.filterNot { it.read }
        } else {
            chapters
        }
    }

    suspend fun await(audiobookId: Long, fromChapterId: Long, onlyUnread: Boolean = true): List<Chapter> {
        val chapters = await(audiobookId, onlyUnread)
        val currChapterIndex = chapters.indexOfFirst { it.id == fromChapterId }
        val nextChapters = chapters.subList(max(0, currChapterIndex), chapters.size)

        if (onlyUnread) {
            return nextChapters
        }

        // The "next chapter" is either:
        // - The current chapter if it isn't completely read
        // - The chapters after the current chapter if the current one is completely read
        val fromChapter = chapters.getOrNull(currChapterIndex)
        return if (fromChapter != null && !fromChapter.read) {
            nextChapters
        } else {
            nextChapters.drop(1)
        }
    }
}
