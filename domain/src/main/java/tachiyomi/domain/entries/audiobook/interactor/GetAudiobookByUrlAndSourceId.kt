package tachiyomi.domain.entries.audiobook.interactor

import tachiyomi.domain.entries.audiobook.model.Audiobook
import tachiyomi.domain.entries.audiobook.repository.AudiobookRepository

class GetAudiobookByUrlAndSourceId(
    private val audiobookRepository: AudiobookRepository,
) {
    suspend fun awaitAudiobook(url: String, sourceId: Long): Audiobook? {
        return audiobookRepository.getAudiobookByUrlAndSourceId(url, sourceId)
    }
}
