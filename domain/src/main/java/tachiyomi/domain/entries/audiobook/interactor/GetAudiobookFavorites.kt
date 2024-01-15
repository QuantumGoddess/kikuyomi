package tachiyomi.domain.entries.audiobook.interactor

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.entries.audiobook.model.Audiobook
import tachiyomi.domain.entries.audiobook.repository.AudiobookRepository

class GetAudiobookFavorites(
    private val audiobookRepository: AudiobookRepository,
) {

    suspend fun await(): List<Audiobook> {
        return audiobookRepository.getAudiobookFavorites()
    }

    fun subscribe(sourceId: Long): Flow<List<Audiobook>> {
        return audiobookRepository.getAudiobookFavoritesBySourceId(sourceId)
    }
}
