package tachiyomi.domain.source.audiobook.repository

import androidx.paging.PagingSource
import eu.kanade.tachiyomi.audiobooksource.model.AudiobookFilterList
import eu.kanade.tachiyomi.audiobooksource.model.SAudiobook
import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.source.audiobook.model.AudiobookSource
import tachiyomi.domain.source.audiobook.model.AudiobookSourceWithCount

typealias AudiobookSourcePagingSourceType = PagingSource<Long, SAudiobook>

interface AudiobookSourceRepository {

    fun getAudiobookSources(): Flow<List<AudiobookSource>>

    fun getOnlineAudiobookSources(): Flow<List<AudiobookSource>>

    fun getAudiobookSourcesWithFavoriteCount(): Flow<List<Pair<AudiobookSource, Long>>>

    fun getSourcesWithNonLibraryAudiobook(): Flow<List<AudiobookSourceWithCount>>

    fun searchAudiobook(sourceId: Long, query: String, filterList: AudiobookFilterList): AudiobookSourcePagingSourceType

    fun getPopularAudiobook(sourceId: Long): AudiobookSourcePagingSourceType

    fun getLatestAudiobook(sourceId: Long): AudiobookSourcePagingSourceType
}
