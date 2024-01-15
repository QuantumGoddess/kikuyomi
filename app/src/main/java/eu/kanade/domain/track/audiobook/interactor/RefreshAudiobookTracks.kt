package eu.kanade.domain.track.audiobook.interactor

import eu.kanade.domain.track.audiobook.model.toDbTrack
import eu.kanade.domain.track.audiobook.model.toDomainTrack
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import tachiyomi.domain.track.audiobook.interactor.GetAudiobookTracks
import tachiyomi.domain.track.audiobook.interactor.InsertAudiobookTrack

class RefreshAudiobookTracks(
    private val getTracks: GetAudiobookTracks,
    private val trackerManager: TrackerManager,
    private val insertTrack: InsertAudiobookTrack,
    private val syncChapterProgressWithTrack: SyncChapterProgressWithTrack,
) {

    /**
     * Fetches updated tracking data from all logged in trackers.
     *
     * @return Failed updates.
     */
    suspend fun await(audiobookId: Long): List<Pair<Tracker?, Throwable>> {
        return supervisorScope {
            return@supervisorScope getTracks.await(audiobookId)
                .map { it to trackerManager.get(it.syncId) }
                .filter { (_, service) -> service?.isLoggedIn == true }
                .map { (track, service) ->
                    async {
                        return@async try {
                            val updatedTrack = service!!.audiobookService.refresh(track.toDbTrack())
                            insertTrack.await(updatedTrack.toDomainTrack()!!)
                            syncChapterProgressWithTrack.await(audiobookId, track, service.audiobookService)
                            null
                        } catch (e: Throwable) {
                            service to e
                        }
                    }
                }
                .awaitAll()
                .filterNotNull()
        }
    }
}
