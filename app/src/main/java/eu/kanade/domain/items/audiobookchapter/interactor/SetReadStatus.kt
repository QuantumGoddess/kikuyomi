package eu.kanade.domain.items.audiobookchapter.interactor

import eu.kanade.domain.download.audiobook.interactor.DeleteChapterDownload
import logcat.LogPriority
import tachiyomi.core.util.lang.withNonCancellableContext
import tachiyomi.core.util.system.logcat
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.entries.audiobook.model.Audiobook
import tachiyomi.domain.entries.audiobook.repository.AudiobookRepository
import tachiyomi.domain.items.audiobookchapter.model.Chapter
import tachiyomi.domain.items.audiobookchapter.model.ChapterUpdate
import tachiyomi.domain.items.audiobookchapter.repository.ChapterRepository

class SetReadStatus(
    private val downloadPreferences: DownloadPreferences,
    private val deleteDownload: DeleteChapterDownload,
    private val audiobookRepository: AudiobookRepository,
    private val chapterRepository: ChapterRepository,
) {

    private val mapper = { chapter: Chapter, read: Boolean ->
        ChapterUpdate(
            read = read,
            lastSecondRead = if (!read) 0 else null,
            id = chapter.id,
        )
    }

    suspend fun await(read: Boolean, vararg chapters: Chapter): Result = withNonCancellableContext {
        val chaptersToUpdate = chapters.filter {
            when (read) {
                true -> !it.read
                false -> it.read || it.lastSecondRead > 0
            }
        }
        if (chaptersToUpdate.isEmpty()) {
            return@withNonCancellableContext Result.NoChapters
        }

        try {
            chapterRepository.updateAllChapters(
                chaptersToUpdate.map { mapper(it, read) },
            )
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            return@withNonCancellableContext Result.InternalError(e)
        }

        if (read && downloadPreferences.removeAfterMarkedAsRead().get()) {
            chaptersToUpdate
                .groupBy { it.audiobookId }
                .forEach { (audiobookId, chapters) ->
                    deleteDownload.awaitAll(
                        audiobook = audiobookRepository.getAudiobookById(audiobookId),
                        chapters = chapters.toTypedArray(),
                    )
                }
        }

        Result.Success
    }

    suspend fun await(audiobookId: Long, read: Boolean): Result = withNonCancellableContext {
        await(
            read = read,
            chapters = chapterRepository
                .getChapterByAudiobookId(audiobookId)
                .toTypedArray(),
        )
    }

    suspend fun await(audiobook: Audiobook, read: Boolean) =
        await(audiobook.id, read)

    sealed interface Result {
        data object Success : Result
        data object NoChapters : Result
        data class InternalError(val error: Throwable) : Result
    }
}
