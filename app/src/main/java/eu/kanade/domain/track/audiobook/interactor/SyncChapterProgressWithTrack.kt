package eu.kanade.domain.track.audiobook.interactor

import eu.kanade.domain.track.audiobook.model.toDbTrack
import eu.kanade.tachiyomi.data.track.AudiobookTracker
import eu.kanade.tachiyomi.data.track.EnhancedAudiobookTracker
import logcat.LogPriority
import tachiyomi.core.util.system.logcat
import tachiyomi.domain.items.audiobookchapter.interactor.GetChaptersByAudiobookId
import tachiyomi.domain.items.audiobookchapter.interactor.UpdateChapter
import tachiyomi.domain.items.audiobookchapter.model.toChapterUpdate
import tachiyomi.domain.track.audiobook.interactor.InsertAudiobookTrack
import tachiyomi.domain.track.audiobook.model.AudiobookTrack

class SyncChapterProgressWithTrack(
    private val updateChapter: UpdateChapter,
    private val insertTrack: InsertAudiobookTrack,
    private val getChaptersByAudiobookId: GetChaptersByAudiobookId,
) {

    suspend fun await(
        audiobookId: Long,
        remoteTrack: AudiobookTrack,
        service: AudiobookTracker,
    ) {
        if (service !is EnhancedAudiobookTracker) {
            return
        }

        val sortedChapters = getChaptersByAudiobookId.await(audiobookId)
            .sortedBy { it.chapterNumber }
            .filter { it.isRecognizedNumber }

        val chapterUpdates = sortedChapters
            .filter { chapter -> chapter.chapterNumber <= remoteTrack.lastChapterRead && !chapter.read }
            .map { it.copy(read = true).toChapterUpdate() }

        // only take into account continuous watching
        val localLastRead = sortedChapters.takeWhile { it.read }.lastOrNull()?.chapterNumber ?: 0F
        val updatedTrack = remoteTrack.copy(lastChapterRead = localLastRead.toDouble())

        try {
            service.update(updatedTrack.toDbTrack())
            updateChapter.awaitAll(chapterUpdates)
            insertTrack.await(updatedTrack)
        } catch (e: Throwable) {
            logcat(LogPriority.WARN, e)
        }
    }
}
