package tachiyomi.domain.history.audiobook.interactor

import tachiyomi.domain.history.audiobook.model.AudiobookHistoryWithRelations
import tachiyomi.domain.history.audiobook.repository.AudiobookHistoryRepository

class RemoveAudiobookHistory(
    private val repository: AudiobookHistoryRepository,
) {

    suspend fun awaitAll(): Boolean {
        return repository.deleteAllAudiobookHistory()
    }

    suspend fun await(history: AudiobookHistoryWithRelations) {
        repository.resetAudiobookHistory(history.id)
    }

    suspend fun await(audiobookId: Long) {
        repository.resetHistoryByAudiobookId(audiobookId)
    }
}
