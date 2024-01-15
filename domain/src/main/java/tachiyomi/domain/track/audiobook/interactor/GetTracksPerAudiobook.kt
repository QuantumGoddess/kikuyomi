package tachiyomi.domain.track.audiobook.interactor

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import tachiyomi.domain.track.audiobook.model.AudiobookTrack
import tachiyomi.domain.track.audiobook.repository.AudiobookTrackRepository

class GetTracksPerAudiobook(
    private val trackRepository: AudiobookTrackRepository,
) {

    fun subscribe(): Flow<Map<Long, List<AudiobookTrack>>> {
        return trackRepository.getAudiobookTracksAsFlow().map { tracks -> tracks.groupBy { it.audiobookId } }
    }
}
