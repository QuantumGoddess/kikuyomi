package eu.kanade.domain.track.audiobook.interactor

import eu.kanade.domain.track.audiobook.model.toDbTrack
import eu.kanade.domain.track.audiobook.model.toDomainTrack
import eu.kanade.tachiyomi.audiobooksource.AudiobookSource
import eu.kanade.tachiyomi.data.database.models.audiobook.AudiobookTrack
import eu.kanade.tachiyomi.data.track.AudiobookTracker
import eu.kanade.tachiyomi.data.track.EnhancedAudiobookTracker
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.util.lang.convertEpochMillisZone
import logcat.LogPriority
import tachiyomi.core.util.lang.withIOContext
import tachiyomi.core.util.lang.withNonCancellableContext
import tachiyomi.core.util.system.logcat
import tachiyomi.domain.entries.audiobook.model.Audiobook
import tachiyomi.domain.history.audiobook.interactor.GetAudiobookHistory
import tachiyomi.domain.items.audiobookchapter.interactor.GetChaptersByAudiobookId
import tachiyomi.domain.track.audiobook.interactor.GetAudiobookTracks
import tachiyomi.domain.track.audiobook.interactor.InsertAudiobookTrack
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.ZoneOffset

class AddAudiobookTracks(
    private val getTracks: GetAudiobookTracks,
    private val insertTrack: InsertAudiobookTrack,
    private val syncChapterProgressWithTrack: SyncChapterProgressWithTrack,
    private val getChaptersByAudiobookId: GetChaptersByAudiobookId,
) {

    // TODO: update all trackers based on common data
    suspend fun bind(tracker: AudiobookTracker, item: AudiobookTrack, audiobookId: Long) = withNonCancellableContext {
        withIOContext {
            val allChapters = getChaptersByAudiobookId.await(audiobookId)
            val hasReadChapters = allChapters.any { it.read }
            tracker.bind(item, hasReadChapters)

            var track = item.toDomainTrack(idRequired = false) ?: return@withIOContext

            insertTrack.await(track)

            // TODO: merge into [SyncChapterProgressWithTrack]?
            // Update chapter progress if newer chapters marked read locally
            if (hasReadChapters) {
                val latestLocalReadChapterNumber = allChapters
                    .sortedBy { it.chapterNumber }
                    .takeWhile { it.read }
                    .lastOrNull()
                    ?.chapterNumber ?: -1.0

                if (latestLocalReadChapterNumber > track.lastChapterRead) {
                    track = track.copy(
                        lastChapterRead = latestLocalReadChapterNumber,
                    )
                    tracker.setRemoteLastChapterRead(track.toDbTrack(), latestLocalReadChapterNumber.toInt())
                }

                if (track.startDate <= 0) {
                    val firstReadChapterDate = Injekt.get<GetAudiobookHistory>().await(audiobookId)
                        .sortedBy { it.readAt }
                        .firstOrNull()
                        ?.readAt

                    firstReadChapterDate?.let {
                        val startDate = firstReadChapterDate.time.convertEpochMillisZone(
                            ZoneOffset.systemDefault(),
                            ZoneOffset.UTC,
                        )
                        track = track.copy(
                            startDate = startDate,
                        )
                        tracker.setRemoteStartDate(track.toDbTrack(), startDate)
                    }
                }
            }

            syncChapterProgressWithTrack.await(audiobookId, track, tracker)
        }
    }

    suspend fun bindEnhancedTrackers(audiobook: Audiobook, source: AudiobookSource) = withNonCancellableContext {
        withIOContext {
            getTracks.await(audiobook.id)
                .filterIsInstance<EnhancedAudiobookTracker>()
                .filter { it.accept(source) }
                .forEach { service ->
                    try {
                        service.match(audiobook)?.let { track ->
                            track.audiobook_id = audiobook.id
                            (service as Tracker).audiobookService.bind(track)
                            insertTrack.await(track.toDomainTrack()!!)

                            syncChapterProgressWithTrack.await(
                                audiobook.id,
                                track.toDomainTrack()!!,
                                service.audiobookService,
                            )
                        }
                    } catch (e: Exception) {
                        logcat(
                            LogPriority.WARN,
                            e,
                        ) { "Could not match audiobook: ${audiobook.title} with service $service" }
                    }
                }
        }
    }
}
