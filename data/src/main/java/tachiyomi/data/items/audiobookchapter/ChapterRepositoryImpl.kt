package tachiyomi.data.items.audiobookchapter

import kotlinx.coroutines.flow.Flow
import logcat.LogPriority
import tachiyomi.core.util.system.logcat
import tachiyomi.data.handlers.audiobook.AudiobookDatabaseHandler
import tachiyomi.domain.items.audiobookchapter.model.Chapter
import tachiyomi.domain.items.audiobookchapter.model.ChapterUpdate
import tachiyomi.domain.items.audiobookchapter.repository.ChapterRepository

class ChapterRepositoryImpl(
    private val handler: AudiobookDatabaseHandler,
) : ChapterRepository {

    override suspend fun addAllChapters(chapters: List<Chapter>): List<Chapter> {
        return try {
            handler.await(inTransaction = true) {
                chapters.map { chapter ->
                    chaptersQueries.insert(
                        chapter.audiobookId,
                        chapter.url,
                        chapter.name,
                        chapter.scanlator,
                        chapter.read,
                        chapter.bookmark,
                        chapter.lastSecondRead,
                        chapter.totalSeconds,
                        chapter.chapterNumber,
                        chapter.sourceOrder,
                        chapter.dateFetch,
                        chapter.dateUpload,
                    )
                    val lastInsertId = chaptersQueries.selectLastInsertedRowId().executeAsOne()
                    chapter.copy(id = lastInsertId)
                }
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            emptyList()
        }
    }

    override suspend fun updateChapter(chapterUpdate: ChapterUpdate) {
        partialUpdate(chapterUpdate)
    }

    override suspend fun updateAllChapters(chapterUpdates: List<ChapterUpdate>) {
        partialUpdate(*chapterUpdates.toTypedArray())
    }

    private suspend fun partialUpdate(vararg chapterUpdates: ChapterUpdate) {
        handler.await(inTransaction = true) {
            chapterUpdates.forEach { chapterUpdate ->
                chaptersQueries.update(
                    audiobookId = chapterUpdate.audiobookId,
                    url = chapterUpdate.url,
                    name = chapterUpdate.name,
                    scanlator = chapterUpdate.scanlator,
                    read = chapterUpdate.read,
                    bookmark = chapterUpdate.bookmark,
                    lastSecondRead = chapterUpdate.lastSecondRead,
                    totalSeconds = chapterUpdate.totalSeconds,
                    chapterNumber = chapterUpdate.chapterNumber,
                    sourceOrder = chapterUpdate.sourceOrder,
                    dateFetch = chapterUpdate.dateFetch,
                    dateUpload = chapterUpdate.dateUpload,
                    chapterId = chapterUpdate.id,
                )
            }
        }
    }

    override suspend fun removeChaptersWithIds(chapterIds: List<Long>) {
        try {
            handler.await { chaptersQueries.removeChaptersWithIds(chapterIds) }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
        }
    }

    override suspend fun getChapterByAudiobookId(audiobookId: Long): List<Chapter> {
        return handler.awaitList { chaptersQueries.getChaptersByAudiobookId(audiobookId, ::mapChapter) }
    }

    override suspend fun getBookmarkedChaptersByAudiobookId(audiobookId: Long): List<Chapter> {
        return handler.awaitList {
            chaptersQueries.getBookmarkedChaptersByAudiobookId(
                audiobookId,
                ::mapChapter,
            )
        }
    }

    override suspend fun getChapterById(id: Long): Chapter? {
        return handler.awaitOneOrNull { chaptersQueries.getChapterById(id, ::mapChapter) }
    }

    override suspend fun getChapterByAudiobookIdAsFlow(audiobookId: Long): Flow<List<Chapter>> {
        return handler.subscribeToList {
            chaptersQueries.getChaptersByAudiobookId(
                audiobookId,
                ::mapChapter,
            )
        }
    }

    override suspend fun getChapterByUrlAndAudiobookId(url: String, audiobookId: Long): Chapter? {
        return handler.awaitOneOrNull {
            chaptersQueries.getChapterByUrlAndAudiobookId(
                url,
                audiobookId,
                ::mapChapter,
            )
        }
    }

    private fun mapChapter(
        id: Long,
        audiobookId: Long,
        url: String,
        name: String,
        scanlator: String?,
        read: Boolean,
        bookmark: Boolean,
        lastSecondRead: Long,
        totalSeconds: Long,
        chapterNumber: Double,
        sourceOrder: Long,
        dateFetch: Long,
        dateUpload: Long,
        lastModifiedAt: Long,
    ): Chapter = Chapter(
        id = id,
        audiobookId = audiobookId,
        read = read,
        bookmark = bookmark,
        lastSecondRead = lastSecondRead,
        totalSeconds = totalSeconds,
        dateFetch = dateFetch,
        sourceOrder = sourceOrder,
        url = url,
        name = name,
        dateUpload = dateUpload,
        chapterNumber = chapterNumber,
        scanlator = scanlator,
        lastModifiedAt = lastModifiedAt,
    )
}
