
package tachiyomi.domain.source.audiobook.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.source.audiobook.model.StubAudiobookSource

interface AudiobookStubSourceRepository {
    fun subscribeAllAudiobook(): Flow<List<StubAudiobookSource>>

    suspend fun getStubAudiobookSource(id: Long): StubAudiobookSource?

    suspend fun upsertStubAudiobookSource(id: Long, lang: String, name: String)
}
