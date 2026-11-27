package eu.kanade.tachiyomi.animesource.online

import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.AnikkuOnly
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * A simple implementation for sources from a website using Jsoup, an HTML parser.
 *
 * The host supplies the parsing glue; extensions only describe the selectors.
 */
@Suppress("unused", "unused_parameter")
@Deprecated(
    message = "In most cases sources only require a subset of the methods from this class. " +
        "Source developers should make their own implementation according to their needs.",
)
abstract class ParsedAnimeHttpSource : AnimeHttpSource() {

    override fun popularAnimeParse(response: Response): AnimesPage =
        parseAnimeList(response, popularAnimeSelector(), ::popularAnimeFromElement, popularAnimeNextPageSelector())

    protected abstract fun popularAnimeSelector(): String

    protected abstract fun popularAnimeFromElement(element: Element): SAnime

    protected abstract fun popularAnimeNextPageSelector(): String?

    override fun searchAnimeParse(response: Response): AnimesPage =
        parseAnimeList(response, searchAnimeSelector(), ::searchAnimeFromElement, searchAnimeNextPageSelector())

    protected abstract fun searchAnimeSelector(): String

    protected abstract fun searchAnimeFromElement(element: Element): SAnime

    protected abstract fun searchAnimeNextPageSelector(): String?

    override fun latestUpdatesParse(response: Response): AnimesPage =
        parseAnimeList(response, latestUpdatesSelector(), ::latestUpdatesFromElement, latestUpdatesNextPageSelector())

    protected abstract fun latestUpdatesSelector(): String

    protected abstract fun latestUpdatesFromElement(element: Element): SAnime

    protected abstract fun latestUpdatesNextPageSelector(): String?

    override fun animeDetailsParse(response: Response): SAnime = animeDetailsParse(response.asJsoup())

    protected abstract fun animeDetailsParse(document: Document): SAnime

    @AnikkuOnly
    override fun relatedAnimeListParse(response: Response): List<SAnime> {
        val doc = response.asJsoup()
        val selector = relatedAnimeListSelector()
        return doc.select(selector).map { element -> relatedAnimeFromElement(element) }
    }

    @AnikkuOnly
    protected open fun relatedAnimeListSelector(): String = throw UnsupportedOperationException()

    @AnikkuOnly
    protected open fun relatedAnimeFromElement(element: Element): SAnime = throw UnsupportedOperationException()

    override fun episodeListParse(response: Response): List<SEpisode> {
        val doc = response.asJsoup()
        val selector = episodeListSelector()
        return doc.select(selector).map { element -> episodeFromElement(element) }
    }

    protected abstract fun episodeListSelector(): String

    protected abstract fun episodeFromElement(element: Element): SEpisode

    override fun seasonListParse(response: Response): List<SAnime> {
        val doc = response.asJsoup()
        val selector = seasonListSelector()
        return doc.select(selector).map { element -> seasonFromElement(element) }
    }

    protected abstract fun seasonListSelector(): String

    protected abstract fun seasonFromElement(element: Element): SAnime

    private fun parseAnimeList(
        response: Response,
        selector: String,
        fromElement: (Element) -> SAnime,
        nextPageSelector: String?,
    ): AnimesPage {
        val doc = response.asJsoup()
        val animes = doc.select(selector).map(fromElement)
        val hasNextPage = nextPageSelector != null && doc.selectFirst(nextPageSelector) != null
        return AnimesPage(animes, hasNextPage)
    }
}
