package tachiyomi.domain.history.audiobook.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.history.audiobook.model.AudiobookHistory
import tachiyomi.domain.history.audiobook.model.AudiobookHistoryUpdate
import tachiyomi.domain.history.audiobook.model.AudiobookHistoryWithRelations

interface AudiobookHistoryRepository {

    fun getAudiobookHistory(query: String): Flow<List<AudiobookHistoryWithRelations>>

    suspend fun getLastAudiobookHistory(): AudiobookHistoryWithRelations?

    suspend fun resetAudiobookHistory(historyId: Long)

    suspend fun getHistoryByAudiobookId(audiobookId: Long): List<AudiobookHistory>

    suspend fun resetHistoryByAudiobookId(audiobookId: Long)

    suspend fun deleteAllAudiobookHistory(): Boolean

    suspend fun upsertAudiobookHistory(historyUpdate: AudiobookHistoryUpdate)
}
