package tachiyomi.domain.source.audiobook.interactor

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.source.audiobook.model.AudiobookSourceWithCount
import tachiyomi.domain.source.audiobook.repository.AudiobookSourceRepository

class GetAudiobookSourcesWithNonLibraryAudiobook(
    private val repository: AudiobookSourceRepository,
) {

    fun subscribe(): Flow<List<AudiobookSourceWithCount>> {
        return repository.getSourcesWithNonLibraryAudiobook()
    }
}
