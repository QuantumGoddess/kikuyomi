package tachiyomi.domain.entries.audiobook.interactor

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.entries.audiobook.repository.AudiobookRepository
import tachiyomi.domain.library.audiobook.LibraryAudiobook

class GetLibraryAudiobook(
    private val audiobookRepository: AudiobookRepository,
) {

    suspend fun await(): List<LibraryAudiobook> {
        return audiobookRepository.getLibraryAudiobook()
    }

    fun subscribe(): Flow<List<LibraryAudiobook>> {
        return audiobookRepository.getLibraryAudiobookAsFlow()
    }
}
