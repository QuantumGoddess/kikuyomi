package tachiyomi.domain.history.audiobook.interactor

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.history.audiobook.model.AudiobookHistory
import tachiyomi.domain.history.audiobook.model.AudiobookHistoryWithRelations
import tachiyomi.domain.history.audiobook.repository.AudiobookHistoryRepository

class GetAudiobookHistory(
    private val repository: AudiobookHistoryRepository,
) {

    suspend fun await(audiobookId: Long): List<AudiobookHistory> {
        return repository.getHistoryByAudiobookId(audiobookId)
    }

    fun subscribe(query: String): Flow<List<AudiobookHistoryWithRelations>> {
        return repository.getAudiobookHistory(query)
    }
}
