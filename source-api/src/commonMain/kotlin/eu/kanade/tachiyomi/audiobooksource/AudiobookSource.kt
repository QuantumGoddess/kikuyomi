package eu.kanade.tachiyomi.audiobooksource

import eu.kanade.tachiyomi.audiobooksource.model.SAudiobook
import eu.kanade.tachiyomi.audiobooksource.model.SChapter
import eu.kanade.tachiyomi.audiobooksource.model.Audio
import eu.kanade.tachiyomi.util.awaitSingle
import rx.Observable

/**
 * A basic interface for creating a source. It could be an online source, a local source, etc.
 */
interface AudiobookSource {

    /**
     * ID for the source. Must be unique.
     */
    val id: Long

    /**
     * Name of the source.
     */
    val name: String

    val lang: String
        get() = ""

    /**
     * Get the updated details for a audiobook.
     *
     * @since extensions-lib 1.5
     * @param audiobook the audiobook to update.
     * @return the updated audiobook.
     */
    @Suppress("DEPRECATION")
    suspend fun getAudiobookDetails(audiobook: SAudiobook): SAudiobook {
        return fetchAudiobookDetails(audiobook).awaitSingle()
    }

    /**
     * Get all the available chapters for a audiobook.
     *
     * @since extensions-lib 1.5
     * @param audiobook the audiobook to update.
     * @return the chapters for the audiobook.
     */
    @Suppress("DEPRECATION")
    suspend fun getChapterList(audiobook: SAudiobook): List<SChapter> {
        return fetchChapterList(audiobook).awaitSingle()
    }

    /**
     * Get the list of audios a chapter has. Pages should be returned
     * in the expected order; the index is ignored.
     *
     * @since extensions-lib 1.5
     * @param chapter the chapter.
     * @return the audios for the chapter.
     */
    @Suppress("DEPRECATION")
    suspend fun getAudioList(chapter: SChapter): List<Audio> {
        return fetchAudioList(chapter).awaitSingle()
    }

    @Deprecated(
        "Use the non-RxJava API instead",
        ReplaceWith("getAudiobookDetails"),
    )
    fun fetchAudiobookDetails(audiobook: SAudiobook): Observable<SAudiobook> =
        throw IllegalStateException("Not used")

    @Deprecated(
        "Use the non-RxJava API instead",
        ReplaceWith("getChapterList"),
    )
    fun fetchChapterList(audiobook: SAudiobook): Observable<List<SChapter>> =
        throw IllegalStateException("Not used")

    @Deprecated(
        "Use the non-RxJava API instead",
        ReplaceWith("getAudioList"),
    )
    fun fetchAudioList(chapter: SChapter): Observable<List<Audio>> =
        throw IllegalStateException("Not used")
}
