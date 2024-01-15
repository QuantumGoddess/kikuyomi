package tachiyomi.domain.entries.audiobook.interactor

import tachiyomi.domain.entries.audiobook.model.Audiobook
import tachiyomi.domain.entries.audiobook.repository.AudiobookRepository

class GetDuplicateLibraryAudiobook(
    private val audiobookRepository: AudiobookRepository,
) {

    suspend fun await(audiobook: Audiobook): List<Audiobook> {
        return audiobookRepository.getDuplicateLibraryAudiobook(audiobook.id, audiobook.title.lowercase())
    }
}
