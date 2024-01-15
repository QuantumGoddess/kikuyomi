package tachiyomi.data.source.audiobook

import androidx.paging.PagingState
import eu.kanade.tachiyomi.audiobooksource.AudiobookCatalogueSource
import eu.kanade.tachiyomi.audiobooksource.model.AudiobookFilterList
import eu.kanade.tachiyomi.audiobooksource.model.AudiobooksPage
import eu.kanade.tachiyomi.audiobooksource.model.SAudiobook
import tachiyomi.core.util.lang.withIOContext
import tachiyomi.domain.items.audiobookchapter.model.NoChaptersException
import tachiyomi.domain.source.audiobook.repository.AudiobookSourcePagingSourceType

class AudiobookSourceSearchPagingSource(
    source: AudiobookCatalogueSource,
    val query: String,
    val filters: AudiobookFilterList,
) : AudiobookSourcePagingSource(source) {
    override suspend fun requestNextPage(currentPage: Int): AudiobooksPage {
        return source.getSearchAudiobook(currentPage, query, filters)
    }
}

class AudiobookSourcePopularPagingSource(source: AudiobookCatalogueSource) : AudiobookSourcePagingSource(source) {
    override suspend fun requestNextPage(currentPage: Int): AudiobooksPage {
        return source.getPopularAudiobook(currentPage)
    }
}

class AudiobookSourceLatestPagingSource(source: AudiobookCatalogueSource) : AudiobookSourcePagingSource(source) {
    override suspend fun requestNextPage(currentPage: Int): AudiobooksPage {
        return source.getLatestUpdates(currentPage)
    }
}

abstract class AudiobookSourcePagingSource(
    protected val source: AudiobookCatalogueSource,
) : AudiobookSourcePagingSourceType() {

    abstract suspend fun requestNextPage(currentPage: Int): AudiobooksPage

    override suspend fun load(params: LoadParams<Long>): LoadResult<Long, SAudiobook> {
        val page = params.key ?: 1

        val audiobooksPage = try {
            withIOContext {
                requestNextPage(page.toInt())
                    .takeIf { it.audiobooks.isNotEmpty() }
                    ?: throw NoChaptersException()
            }
        } catch (e: Exception) {
            return LoadResult.Error(e)
        }

        return LoadResult.Page(
            data = audiobooksPage.audiobooks,
            prevKey = null,
            nextKey = if (audiobooksPage.hasNextPage) page + 1 else null,
        )
    }

    override fun getRefreshKey(state: PagingState<Long, SAudiobook>): Long? {
        return state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey ?: anchorPage?.nextKey
        }
    }
}
