package tachiyomi.data.history.audiobook

import kotlinx.coroutines.flow.Flow
import logcat.LogPriority
import tachiyomi.core.util.system.logcat
import tachiyomi.data.handlers.audiobook.AudiobookDatabaseHandler
import tachiyomi.domain.history.audiobook.model.AudiobookHistory
import tachiyomi.domain.history.audiobook.model.AudiobookHistoryUpdate
import tachiyomi.domain.history.audiobook.model.AudiobookHistoryWithRelations
import tachiyomi.domain.history.audiobook.repository.AudiobookHistoryRepository

class AudiobookHistoryRepositoryImpl(
    private val handler: AudiobookDatabaseHandler,
) : AudiobookHistoryRepository {

    override fun getAudiobookHistory(query: String): Flow<List<AudiobookHistoryWithRelations>> {
        return handler.subscribeToList {
            audiobookhistoryViewQueries.audiobookhistory(query, AudiobookHistoryMapper::mapAudiobookHistoryWithRelations)
        }
    }

    override suspend fun getLastAudiobookHistory(): AudiobookHistoryWithRelations? {
        return handler.awaitOneOrNull {
            audiobookhistoryViewQueries.getLatestAudiobookHistory(AudiobookHistoryMapper::mapAudiobookHistoryWithRelations)
        }
    }

    override suspend fun getHistoryByAudiobookId(audiobookId: Long): List<AudiobookHistory> {
        return handler.awaitList {
            audiobookhistoryQueries.getHistoryByAudiobookId(
                audiobookId,
                AudiobookHistoryMapper::mapAudiobookHistory,
            )
        }
    }

    override suspend fun resetAudiobookHistory(historyId: Long) {
        try {
            handler.await { audiobookhistoryQueries.resetAudiobookHistoryById(historyId) }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, throwable = e)
        }
    }

    override suspend fun resetHistoryByAudiobookId(audiobookId: Long) {
        try {
            handler.await { audiobookhistoryQueries.resetHistoryByAudiobookId(audiobookId) }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, throwable = e)
        }
    }

    override suspend fun deleteAllAudiobookHistory(): Boolean {
        return try {
            handler.await { audiobookhistoryQueries.removeAllHistory() }
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, throwable = e)
            false
        }
    }

    override suspend fun upsertAudiobookHistory(historyUpdate: AudiobookHistoryUpdate) {
        try {
            handler.await {
                audiobookhistoryQueries.upsert(
                    historyUpdate.chapterId,
                    historyUpdate.readAt,
                )
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, throwable = e)
        }
    }
}
