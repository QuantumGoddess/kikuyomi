package eu.kanade.tachiyomi.data.track

import eu.kanade.tachiyomi.data.database.models.audiobook.AudiobookTrack

/**
 *Tracker that support deleting am entry from a user's list
 */
interface DeletableAudiobookTracker {

    suspend fun delete(track: AudiobookTrack): AudiobookTrack
}
