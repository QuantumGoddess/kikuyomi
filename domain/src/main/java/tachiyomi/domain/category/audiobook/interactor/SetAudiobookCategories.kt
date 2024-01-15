package tachiyomi.domain.category.audiobook.interactor

import logcat.LogPriority
import tachiyomi.core.util.system.logcat
import tachiyomi.domain.entries.audiobook.repository.AudiobookRepository

class SetAudiobookCategories(
    private val audiobookRepository: AudiobookRepository,
) {

    suspend fun await(audiobookId: Long, categoryIds: List<Long>) {
        try {
            audiobookRepository.setAudiobookCategories(audiobookId, categoryIds)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
        }
    }
}
