package eu.kanade.tachiyomi.audiobooksource.online

import eu.kanade.tachiyomi.audiobooksource.AudiobookSource
import eu.kanade.tachiyomi.audiobooksource.model.SAudiobook
import eu.kanade.tachiyomi.audiobooksource.model.SChapter

/**
 * A source that may handle opening an SAudiobook or SChapter for a given URI.
 *
 * @since extensions-lib 1.5
 */
interface ResolvableAudiobookSource : AudiobookSource {

    /**
     * Returns what the given URI may open.
     * Returns [UriType.Unknown] if the source is not able to resolve the URI.
     *
     * @since extensions-lib 1.5
     */
    fun getUriType(uri: String): UriType

    /**
     * Called if [getUriType] is [UriType.Audiobook].
     * Returns the corresponding SManga, if possible.
     *
     * @since extensions-lib 1.5
     */
    suspend fun getAudiobook(uri: String): SAudiobook?

    /**
     * Called if [getUriType] is [UriType.Chapter].
     * Returns the corresponding SChapter, if possible.
     *
     * @since extensions-lib 1.5
     */
    suspend fun getChapter(uri: String): SChapter?
}

sealed interface UriType {
    data object Audiobook : UriType
    data object Chapter : UriType
    data object Unknown : UriType
}
