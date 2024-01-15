package eu.kanade.tachiyomi.audiobooksource.online

import eu.kanade.tachiyomi.audiobooksource.model.AudiobooksPage
import eu.kanade.tachiyomi.audiobooksource.model.SAudiobook
import eu.kanade.tachiyomi.audiobooksource.model.SChapter
import eu.kanade.tachiyomi.audiobooksource.model.Audio
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * A simple implementation for sources from a website using Jsoup, an HTML parser.
 */
@Suppress("unused")
abstract class ParsedAudiobookHttpSource : AudiobookHttpSource() {

    /**
     * Parses the response from the site and returns a [AudiobooksPage] object.
     *
     * @param response the response from the site.
     */
    override fun popularAudiobookParse(response: Response): AudiobooksPage {
        val document = response.asJsoup()

        val audiobooks = document.select(popularAudiobookSelector()).map { element ->
            popularAudiobookFromElement(element)
        }

        val hasNextPage = popularAudiobookNextPageSelector()?.let { selector ->
            document.select(selector).first()
        } != null

        return AudiobooksPage(audiobooks, hasNextPage)
    }

    /**
     * Returns the Jsoup selector that returns a list of [Element] corresponding to each audiobook.
     */
    protected abstract fun popularAudiobookSelector(): String

    /**
     * Returns an audiobook from the given [element]. Most sites only show the title and the url, it's
     * totally fine to fill only those two values.
     *
     * @param element an element obtained from [popularAudiobookSelector].
     */
    protected abstract fun popularAudiobookFromElement(element: Element): SAudiobook

    /**
     * Returns the Jsoup selector that returns the <a> tag linking to the next page, or null if
     * there's no next page.
     */
    protected abstract fun popularAudiobookNextPageSelector(): String?

    /**
     * Parses the response from the site and returns a [AudiobooksPage] object.
     *
     * @param response the response from the site.
     */
    override fun searchAudiobookParse(response: Response): AudiobooksPage {
        val document = response.asJsoup()

        val audiobooks = document.select(searchAudiobookSelector()).map { element ->
            searchAudiobookFromElement(element)
        }

        val hasNextPage = searchAudiobookNextPageSelector()?.let { selector ->
            document.select(selector).first()
        } != null

        return AudiobooksPage(audiobooks, hasNextPage)
    }

    /**
     * Returns the Jsoup selector that returns a list of [Element] corresponding to each audiobook.
     */
    protected abstract fun searchAudiobookSelector(): String

    /**
     * Returns an audiobook from the given [element]. Most sites only show the title and the url, it's
     * totally fine to fill only those two values.
     *
     * @param element an element obtained from [searchAudiobookSelector].
     */
    protected abstract fun searchAudiobookFromElement(element: Element): SAudiobook

    /**
     * Returns the Jsoup selector that returns the <a> tag linking to the next page, or null if
     * there's no next page.
     */
    protected abstract fun searchAudiobookNextPageSelector(): String?

    /**
     * Parses the response from the site and returns a [AudiobooksPage] object.
     *
     * @param response the response from the site.
     */
    override fun latestUpdatesParse(response: Response): AudiobooksPage {
        val document = response.asJsoup()

        val audiobooks = document.select(latestUpdatesSelector()).map { element ->
            latestUpdatesFromElement(element)
        }

        val hasNextPage = latestUpdatesNextPageSelector()?.let { selector ->
            document.select(selector).first()
        } != null

        return AudiobooksPage(audiobooks, hasNextPage)
    }

    /**
     * Returns the Jsoup selector that returns a list of [Element] corresponding to each audiobook.
     */
    protected abstract fun latestUpdatesSelector(): String

    /**
     * Returns an audiobook from the given [element]. Most sites only show the title and the url, it's
     * totally fine to fill only those two values.
     *
     * @param element an element obtained from [latestUpdatesSelector].
     */
    protected abstract fun latestUpdatesFromElement(element: Element): SAudiobook

    /**
     * Returns the Jsoup selector that returns the <a> tag linking to the next page, or null if
     * there's no next page.
     */
    protected abstract fun latestUpdatesNextPageSelector(): String?

    /**
     * Parses the response from the site and returns the details of an audiobook.
     *
     * @param response the response from the site.
     */
    override fun audiobookDetailsParse(response: Response): SAudiobook {
        return audiobookDetailsParse(response.asJsoup())
    }

    /**
     * Returns the details of the audiobook from the given [document].
     *
     * @param document the parsed document.
     */
    protected abstract fun audiobookDetailsParse(document: Document): SAudiobook

    /**
     * Parses the response from the site and returns a list of chapters.
     *
     * @param response the response from the site.
     */
    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select(chapterListSelector()).map { chapterFromElement(it) }
    }

    /**
     * Returns the Jsoup selector that returns a list of [Element] corresponding to each chapter.
     */
    protected abstract fun chapterListSelector(): String

    /**
     * Returns an chapter from the given element.
     *
     * @param element an element obtained from [chapterListSelector].
     */
    protected abstract fun chapterFromElement(element: Element): SChapter

    /**
     * Parses the response from the site and returns the page list.
     *
     * @param response the response from the site.
     */
    override fun audioListParse(response: Response): List<Audio> {
        val document = response.asJsoup()
        return document.select(audioListSelector()).map { audioFromElement(it) }
    }

    /**
     * Returns the Jsoup selector that returns a list of [Element] corresponding to each audio.
     */
    protected abstract fun audioListSelector(): String

    /**
     * Returns a audio from the given element.
     *
     * @param element an element obtained from [audioListSelector].
     */
    protected abstract fun audioFromElement(element: Element): Audio

    /**
     * Parse the response from the site and returns the absolute url to the source audio.
     *
     * @param response the response from the site.
     */
    override fun audioUrlParse(response: Response): String {
        return audioUrlParse(response.asJsoup())
    }

    /**
     * Returns the absolute url to the source image from the document.
     *
     * @param document the parsed document.
     */
    protected abstract fun audioUrlParse(document: Document): String
}
