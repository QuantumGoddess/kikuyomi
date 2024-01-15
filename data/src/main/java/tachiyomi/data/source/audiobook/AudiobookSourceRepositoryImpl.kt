package tachiyomi.data.source.audiobook

import eu.kanade.tachiyomi.audiobooksource.AudiobookCatalogueSource
import eu.kanade.tachiyomi.audiobooksource.AudiobookSource
import eu.kanade.tachiyomi.audiobooksource.model.AudiobookFilterList
import eu.kanade.tachiyomi.audiobooksource.online.AudiobookHttpSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import tachiyomi.data.handlers.audiobook.AudiobookDatabaseHandler
import tachiyomi.domain.source.audiobook.model.AudiobookSourceWithCount
import tachiyomi.domain.source.audiobook.model.StubAudiobookSource
import tachiyomi.domain.source.audiobook.repository.AudiobookSourcePagingSourceType
import tachiyomi.domain.source.audiobook.repository.AudiobookSourceRepository
import tachiyomi.domain.source.audiobook.service.AudiobookSourceManager
import tachiyomi.domain.source.audiobook.model.AudiobookSource as DomainSource

class AudiobookSourceRepositoryImpl(
    private val sourceManager: AudiobookSourceManager,
    private val handler: AudiobookDatabaseHandler,
) : AudiobookSourceRepository {

    override fun getAudiobookSources(): Flow<List<DomainSource>> {
        return sourceManager.catalogueSources.map { sources ->
            sources.map {
                mapSourceToDomainSource(it).copy(
                    supportsLatest = it.supportsLatest,
                )
            }
        }
    }

    override fun getOnlineAudiobookSources(): Flow<List<DomainSource>> {
        return sourceManager.catalogueSources.map { sources ->
            sources
                .filterIsInstance<AudiobookHttpSource>()
                .map(::mapSourceToDomainSource)
        }
    }

    override fun getAudiobookSourcesWithFavoriteCount(): Flow<List<Pair<DomainSource, Long>>> {
        val sourceIdWithFavoriteCount =
            handler.subscribeToList { audiobooksQueries.getAudiobookSourceIdWithFavoriteCount() }
        return sourceIdWithFavoriteCount.map { sourceIdsWithCount ->
            sourceIdsWithCount
                .map { (sourceId, count) ->
                    val source = sourceManager.getOrStub(sourceId)
                    val domainSource = mapSourceToDomainSource(source).copy(
                        isStub = source is StubAudiobookSource,
                    )
                    domainSource to count
                }
        }
    }

    override fun getSourcesWithNonLibraryAudiobook(): Flow<List<AudiobookSourceWithCount>> {
        val sourceIdWithNonLibraryAudiobook =
            handler.subscribeToList { audiobooksQueries.getSourceIdsWithNonLibraryAudiobook() }
        return sourceIdWithNonLibraryAudiobook.map { sourceId ->
            sourceId.map { (sourceId, count) ->
                val source = sourceManager.getOrStub(sourceId)
                val domainSource = mapSourceToDomainSource(source).copy(
                    isStub = source is StubAudiobookSource,
                )
                AudiobookSourceWithCount(domainSource, count)
            }
        }
    }

    override fun searchAudiobook(
        sourceId: Long,
        query: String,
        filterList: AudiobookFilterList,
    ): AudiobookSourcePagingSourceType {
        val source = sourceManager.get(sourceId) as AudiobookCatalogueSource
        return AudiobookSourceSearchPagingSource(source, query, filterList)
    }

    override fun getPopularAudiobook(sourceId: Long): AudiobookSourcePagingSourceType {
        val source = sourceManager.get(sourceId) as AudiobookCatalogueSource
        return AudiobookSourcePopularPagingSource(source)
    }

    override fun getLatestAudiobook(sourceId: Long): AudiobookSourcePagingSourceType {
        val source = sourceManager.get(sourceId) as AudiobookCatalogueSource
        return AudiobookSourceLatestPagingSource(source)
    }

    private fun mapSourceToDomainSource(source: AudiobookSource): DomainSource = DomainSource(
        id = source.id,
        lang = source.lang,
        name = source.name,
        supportsLatest = false,
        isStub = false,
    )
}
