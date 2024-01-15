package tachiyomi.domain.updates.audiobook.interactor

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.updates.audiobook.model.AudiobookUpdatesWithRelations
import tachiyomi.domain.updates.audiobook.repository.AudiobookUpdatesRepository
import java.time.Instant

class GetAudiobookUpdates(
    private val repository: AudiobookUpdatesRepository,
) {

    suspend fun await(read: Boolean, after: Long): List<AudiobookUpdatesWithRelations> {
        return repository.awaitWithRead(read, after, limit = 500)
    }

    fun subscribe(instant: Instant): Flow<List<AudiobookUpdatesWithRelations>> {
        return repository.subscribeAllAudiobookUpdates(instant.toEpochMilli(), limit = 500)
    }

    fun subscribe(read: Boolean, after: Long): Flow<List<AudiobookUpdatesWithRelations>> {
        return repository.subscribeWithRead(read, after, limit = 500)
    }
}
