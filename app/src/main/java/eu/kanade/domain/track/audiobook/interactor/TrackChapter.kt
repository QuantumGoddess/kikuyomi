package eu.kanade.domain.track.audiobook.interactor

import android.content.Context
import eu.kanade.domain.track.audiobook.model.toDbTrack
import eu.kanade.domain.track.audiobook.model.toDomainTrack
import eu.kanade.domain.track.audiobook.service.DelayedAudiobookTrackingUpdateJob
import eu.kanade.domain.track.audiobook.store.DelayedAudiobookTrackingStore
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.util.system.isOnline
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import logcat.LogPriority
import tachiyomi.core.util.lang.withNonCancellableContext
import tachiyomi.core.util.system.logcat
import tachiyomi.domain.track.audiobook.interactor.GetAudiobookTracks
import tachiyomi.domain.track.audiobook.interactor.InsertAudiobookTrack

class TrackChapter(
    private val getTracks: GetAudiobookTracks,
    private val trackerManager: TrackerManager,
    private val insertTrack: InsertAudiobookTrack,
    private val delayedTrackingStore: DelayedAudiobookTrackingStore,
) {

    suspend fun await(context: Context, audiobookId: Long, chapterNumber: Double) {
        withNonCancellableContext {
            val tracks = getTracks.await(audiobookId)
            if (tracks.isEmpty()) return@withNonCancellableContext

            tracks.mapNotNull { track ->
                val service = trackerManager.get(track.syncId)
                if (service == null || !service.isLoggedIn || chapterNumber <= track.lastChapterRead) {
                    return@mapNotNull null
                }

                async {
                    runCatching {
                        if (context.isOnline()) {
                            val updatedTrack = service.audiobookService.refresh(track.toDbTrack())
                                .toDomainTrack(idRequired = true)!!
                                .copy(lastChapterRead = chapterNumber)
                            service.audiobookService.update(updatedTrack.toDbTrack(), true)
                            insertTrack.await(updatedTrack)
                            delayedTrackingStore.removeAudiobookItem(track.id)
                        } else {
                            delayedTrackingStore.addAudiobook(track.id, chapterNumber)
                            DelayedAudiobookTrackingUpdateJob.setupTask(context)
                        }
                    }
                }
            }
                .awaitAll()
                .mapNotNull { it.exceptionOrNull() }
                .forEach { logcat(LogPriority.INFO, it) }
        }
    }
}
