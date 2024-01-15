package eu.kanade.tachiyomi.data.track

import eu.kanade.tachiyomi.audiobooksource.AudiobookSource
import eu.kanade.tachiyomi.data.track.model.AudiobookTrackSearch
import tachiyomi.domain.entries.audiobook.model.Audiobook
import tachiyomi.domain.track.audiobook.model.AudiobookTrack

/**
 * An Enhanced Track Service will never prompt the user to match a manga with the remote.
 * It is expected that such Track Service can only work with specific sources and unique IDs.
 */
interface EnhancedAudiobookTracker {
    /**
     * This Tracker will only work with the sources that are accepted by this filter function.
     */
    fun accept(source: AudiobookSource): Boolean {
        return source::class.qualifiedName in getAcceptedSources()
    }

    /**
     * Fully qualified source classes that this track service is compatible with.
     */
    fun getAcceptedSources(): List<String>

    fun loginNoop()

    /**
     * match is similar to Tracker.search, but only return zero or one match.
     */
    suspend fun match(audiobook: Audiobook): AudiobookTrackSearch?

    /**
     * Checks whether the provided source/track/audiobook triplet is from this AudiobookTracker
     */
    fun isTrackFrom(track: AudiobookTrack, audiobook: Audiobook, source: AudiobookSource?): Boolean

    /**
     * Migrates the given track for the audiobook to the newSource, if possible
     */
    fun migrateTrack(track: AudiobookTrack, audiobook: Audiobook, newSource: AudiobookSource): AudiobookTrack?
}
