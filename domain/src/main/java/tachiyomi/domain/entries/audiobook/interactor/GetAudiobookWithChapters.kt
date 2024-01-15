package tachiyomi.domain.entries.audiobook.interactor

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import tachiyomi.domain.entries.audiobook.model.Audiobook
import tachiyomi.domain.entries.audiobook.repository.AudiobookRepository
import tachiyomi.domain.items.audiobookchapter.model.Chapter
import tachiyomi.domain.items.audiobookchapter.repository.ChapterRepository

class GetAudiobookWithChapters(
    private val audiobookRepository: AudiobookRepository,
    private val chapterRepository: ChapterRepository,
) {

    suspend fun subscribe(id: Long): Flow<Pair<Audiobook, List<Chapter>>> {
        return combine(
            audiobookRepository.getAudiobookByIdAsFlow(id),
            chapterRepository.getChapterByAudiobookIdAsFlow(id),
        ) { audiobook, chapters ->
            Pair(audiobook, chapters)
        }
    }

    suspend fun awaitAudiobook(id: Long): Audiobook {
        return audiobookRepository.getAudiobookById(id)
    }

    suspend fun awaitChapters(id: Long): List<Chapter> {
        return chapterRepository.getChapterByAudiobookId(id)
    }
}
