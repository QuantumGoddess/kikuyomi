package eu.kanade.tachiyomi.audiobooksource

import eu.kanade.tachiyomi.audiobooksource.model.AudiobookFilterList
import eu.kanade.tachiyomi.audiobooksource.model.AudiobooksPage
import rx.Observable
import tachiyomi.core.util.lang.awaitSingle

interface AudiobookCatalogueSource : AudiobookSource {

    /**
     * An ISO 639-1 compliant language code (two letters in lower case).
     */
    override val lang: String

    /**
     * Whether the source has support for latest updates.
     */
    val supportsLatest: Boolean

    /**
     * Get a page with a list of audiobook.
     *
     * @since extensions-lib 1.5
     * @param page the page number to retrieve.
     */
    @Suppress("DEPRECATION")
    suspend fun getPopularAudiobook(page: Int): AudiobooksPage {
        return fetchPopularAudiobook(page).awaitSingle()
    }

    /**
     * Get a page with a list of audiobook.
     *
     * @since extensions-lib 1.5
     * @param page the page number to retrieve.
     * @param query the search query.
     * @param filters the list of filters to apply.
     */
    @Suppress("DEPRECATION")
    suspend fun getSearchAudiobook(page: Int, query: String, filters: AudiobookFilterList): AudiobooksPage {
        return fetchSearchAudiobook(page, query, filters).awaitSingle()
    }

    /**
     * Get a page with a list of latest audiobook updates.
     *
     * @since extensions-lib 1.5
     * @param page the page number to retrieve.
     */
    @Suppress("DEPRECATION")
    suspend fun getLatestUpdates(page: Int): AudiobooksPage {
        return fetchLatestUpdates(page).awaitSingle()
    }

    /**
     * Returns the list of filters for the source.
     */
    fun getFilterList(): AudiobookFilterList

    // Should be replaced as soon as Audiobook Extension reach 1.5
    @Deprecated(
        "Use the non-RxJava API instead",
        ReplaceWith("getPopularAudiobook"),
    )
    fun fetchPopularAudiobook(page: Int): Observable<AudiobooksPage>

    // Should be replaced as soon as Audiobook Extension reach 1.5
    @Deprecated(
        "Use the non-RxJava API instead",
        ReplaceWith("getSearchAudiobook"),
    )
    fun fetchSearchAudiobook(page: Int, query: String, filters: AudiobookFilterList): Observable<AudiobooksPage>

    // Should be replaced as soon as Audiobook Extension reach 1.5
    @Deprecated(
        "Use the non-RxJava API instead",
        ReplaceWith("getLatestUpdates"),
    )
    fun fetchLatestUpdates(page: Int): Observable<AudiobooksPage>
}
