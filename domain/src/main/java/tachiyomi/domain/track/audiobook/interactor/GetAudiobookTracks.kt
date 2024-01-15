package tachiyomi.domain.track.audiobook.interactor

import kotlinx.coroutines.flow.Flow
import logcat.LogPriority
import tachiyomi.core.util.system.logcat
import tachiyomi.domain.track.audiobook.model.AudiobookTrack
import tachiyomi.domain.track.audiobook.repository.AudiobookTrackRepository

class GetAudiobookTracks(
    private val audiobooktrackRepository: AudiobookTrackRepository,
) {

    suspend fun awaitOne(id: Long): AudiobookTrack? {
        return try {
            audiobooktrackRepository.getTrackByAudiobookId(id)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            null
        }
    }

    suspend fun await(audiobookId: Long): List<AudiobookTrack> {
        return try {
            audiobooktrackRepository.getTracksByAudiobookId(audiobookId)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            emptyList()
        }
    }

    fun subscribe(audiobookId: Long): Flow<List<AudiobookTrack>> {
        return audiobooktrackRepository.getTracksByAudiobookIdAsFlow(audiobookId)
    }
}
