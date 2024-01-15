package tachiyomi.domain.entries.audiobook.interactor

import kotlinx.coroutines.flow.Flow
import logcat.LogPriority
import tachiyomi.core.util.system.logcat
import tachiyomi.domain.entries.audiobook.model.Audiobook
import tachiyomi.domain.entries.audiobook.repository.AudiobookRepository

class GetAudiobook(
    private val audiobookRepository: AudiobookRepository,
) {

    suspend fun await(id: Long): Audiobook? {
        return try {
            audiobookRepository.getAudiobookById(id)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            null
        }
    }

    suspend fun subscribe(id: Long): Flow<Audiobook> {
        return audiobookRepository.getAudiobookByIdAsFlow(id)
    }

    fun subscribe(url: String, sourceId: Long): Flow<Audiobook?> {
        return audiobookRepository.getAudiobookByUrlAndSourceIdAsFlow(url, sourceId)
    }
}
