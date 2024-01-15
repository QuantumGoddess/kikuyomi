package tachiyomi.domain.source.audiobook.interactor

import eu.kanade.tachiyomi.audiobooksource.model.AudiobookFilterList
import tachiyomi.domain.source.audiobook.repository.AudiobookSourcePagingSourceType
import tachiyomi.domain.source.audiobook.repository.AudiobookSourceRepository

class GetRemoteAudiobook(
    private val repository: AudiobookSourceRepository,
) {

    fun subscribe(sourceId: Long, query: String, filterList: AudiobookFilterList): AudiobookSourcePagingSourceType {
        return when (query) {
            QUERY_POPULAR -> repository.getPopularAudiobook(sourceId)
            QUERY_LATEST -> repository.getLatestAudiobook(sourceId)
            else -> repository.searchAudiobook(sourceId, query, filterList)
        }
    }

    companion object {
        const val QUERY_POPULAR = "eu.kanade.domain.source.audiobook.interactor.POPULAR"
        const val QUERY_LATEST = "eu.kanade.domain.source.audiobook.interactor.LATEST"
    }
}
