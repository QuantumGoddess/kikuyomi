package tachiyomi.domain.updates.audiobook.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.updates.audiobook.model.AudiobookUpdatesWithRelations

interface AudiobookUpdatesRepository {

    suspend fun awaitWithRead(seen: Boolean, after: Long, limit: Long): List<AudiobookUpdatesWithRelations>

    fun subscribeAllAudiobookUpdates(after: Long, limit: Long): Flow<List<AudiobookUpdatesWithRelations>>

    fun subscribeWithRead(seen: Boolean, after: Long, limit: Long): Flow<List<AudiobookUpdatesWithRelations>>
}
