package eu.kanade.tachiyomi.data.track

import android.app.Application
import eu.kanade.domain.track.audiobook.interactor.AddAudiobookTracks
import eu.kanade.domain.track.audiobook.model.toDomainTrack
import eu.kanade.tachiyomi.data.track.model.AudiobookTrackSearch
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.collections.immutable.ImmutableList
import logcat.LogPriority
import tachiyomi.core.util.lang.withIOContext
import tachiyomi.core.util.lang.withUIContext
import tachiyomi.core.util.system.logcat
import tachiyomi.domain.track.audiobook.interactor.InsertAudiobookTrack
import tachiyomi.domain.track.audiobook.model.AudiobookTrack
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy

private val addTracks: AddAudiobookTracks by injectLazy()
private val insertTrack: InsertAudiobookTrack by injectLazy()

interface AudiobookTracker {

    // Common functions
    fun getCompletionStatus(): Int

    fun getScoreList(): ImmutableList<String>

    fun indexToScore(index: Int): Float {
        return index.toFloat()
    }

    // Audiobook specific functions
    fun getStatusListAudiobook(): List<Int>

    fun getListeningStatus(): Int

    fun getRelisteningStatus(): Int

    // TODO: Store all scores as 10 point in the future maybe?
    fun get10PointScore(track: AudiobookTrack): Double {
        return track.score
    }

    fun displayScore(track: eu.kanade.tachiyomi.data.database.models.audiobook.AudiobookTrack): String

    suspend fun update(track: eu.kanade.tachiyomi.data.database.models.audiobook.AudiobookTrack, didListenChapter: Boolean = false): eu.kanade.tachiyomi.data.database.models.audiobook.AudiobookTrack

    suspend fun bind(track: eu.kanade.tachiyomi.data.database.models.audiobook.AudiobookTrack, hasReadChapters: Boolean = false): eu.kanade.tachiyomi.data.database.models.audiobook.AudiobookTrack

    suspend fun searchAudiobook(query: String): List<AudiobookTrackSearch>

    suspend fun refresh(track: eu.kanade.tachiyomi.data.database.models.audiobook.AudiobookTrack): eu.kanade.tachiyomi.data.database.models.audiobook.AudiobookTrack

    // TODO: move this to an interactor, and update all trackers based on common data
    suspend fun register(item: eu.kanade.tachiyomi.data.database.models.audiobook.AudiobookTrack, audiobookId: Long) {
        item.audiobook_id = audiobookId
        try {
            addTracks.bind(this, item, audiobookId)
        } catch (e: Throwable) {
            withUIContext { Injekt.get<Application>().toast(e.message) }
        }
    }

    suspend fun setRemoteAudiobookStatus(track: eu.kanade.tachiyomi.data.database.models.audiobook.AudiobookTrack, status: Int) {
        track.status = status
        if (track.status == getCompletionStatus() && track.total_chapters != 0) {
            track.last_chapter_read = track.total_chapters.toFloat()
        }
        updateRemote(track)
    }

    suspend fun setRemoteLastChapterRead(track: eu.kanade.tachiyomi.data.database.models.audiobook.AudiobookTrack, chapterNumber: Int) {
        if (track.last_chapter_read == 0f &&
            track.last_chapter_read < chapterNumber &&
            track.status != getRelisteningStatus()
        ) {
            track.status = getListeningStatus()
        }
        track.last_chapter_read = chapterNumber.toFloat()
        if (track.total_chapters != 0 && track.last_chapter_read.toInt() == track.total_chapters) {
            track.status = getCompletionStatus()
            track.finished_listening_date = System.currentTimeMillis()
        }
        updateRemote(track)
    }

    suspend fun setRemoteScore(track: eu.kanade.tachiyomi.data.database.models.audiobook.AudiobookTrack, scoreString: String) {
        track.score = indexToScore(getScoreList().indexOf(scoreString))
        updateRemote(track)
    }

    suspend fun setRemoteStartDate(track: eu.kanade.tachiyomi.data.database.models.audiobook.AudiobookTrack, epochMillis: Long) {
        track.started_listening_date = epochMillis
        updateRemote(track)
    }

    suspend fun setRemoteFinishDate(track: eu.kanade.tachiyomi.data.database.models.audiobook.AudiobookTrack, epochMillis: Long) {
        track.finished_listening_date = epochMillis
        updateRemote(track)
    }

    private suspend fun updateRemote(track: eu.kanade.tachiyomi.data.database.models.audiobook.AudiobookTrack): Unit =
        withIOContext {
            try {
                update(track)
                track.toDomainTrack(idRequired = false)?.let {
                    insertTrack.await(it)
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to update remote track data id=${track.id}" }
                withUIContext { Injekt.get<Application>().toast(e.message) }
            }
        }
}
