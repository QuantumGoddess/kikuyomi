package tachiyomi.domain.history.audiobook.interactor

import tachiyomi.domain.history.audiobook.model.AudiobookHistoryUpdate
import tachiyomi.domain.history.audiobook.repository.AudiobookHistoryRepository

class UpsertAudiobookHistory(
    private val historyRepository: AudiobookHistoryRepository,
) {

    suspend fun await(historyUpdate: AudiobookHistoryUpdate) {
        historyRepository.upsertAudiobookHistory(historyUpdate)
    }
}
