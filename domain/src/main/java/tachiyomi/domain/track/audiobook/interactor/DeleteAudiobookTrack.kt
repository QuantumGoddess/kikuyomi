package tachiyomi.domain.track.audiobook.interactor

import logcat.LogPriority
import tachiyomi.core.util.system.logcat
import tachiyomi.domain.track.audiobook.repository.AudiobookTrackRepository

class DeleteAudiobookTrack(
    private val trackRepository: AudiobookTrackRepository,
) {

    suspend fun await(audiobookId: Long, syncId: Long) {
        try {
            trackRepository.deleteAudiobook(audiobookId, syncId)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
        }
    }
}
