package tachiyomi.domain.entries.audiobook.interactor

import tachiyomi.domain.entries.audiobook.repository.AudiobookRepository

class ResetAudiobookViewerFlags(
    private val audiobookRepository: AudiobookRepository,
) {
    suspend fun await(): Boolean {
        return audiobookRepository.resetAudiobookViewerFlags()
    }
}
