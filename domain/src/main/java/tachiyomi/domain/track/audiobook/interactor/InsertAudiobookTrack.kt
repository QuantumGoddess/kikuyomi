package tachiyomi.domain.track.audiobook.interactor

import logcat.LogPriority
import tachiyomi.core.util.system.logcat
import tachiyomi.domain.track.audiobook.model.AudiobookTrack
import tachiyomi.domain.track.audiobook.repository.AudiobookTrackRepository

class InsertAudiobookTrack(
    private val audiobooktrackRepository: AudiobookTrackRepository,
) {

    suspend fun await(track: AudiobookTrack) {
        try {
            audiobooktrackRepository.insertAudiobook(track)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
        }
    }

    suspend fun awaitAll(tracks: List<AudiobookTrack>) {
        try {
            audiobooktrackRepository.insertAllAudiobook(tracks)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
        }
    }
}
