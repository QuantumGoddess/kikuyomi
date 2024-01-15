package tachiyomi.domain.items.audiobookchapter.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.items.audiobookchapter.model.Chapter
import tachiyomi.domain.items.audiobookchapter.model.ChapterUpdate

interface ChapterRepository {

    suspend fun addAllChapters(chapters: List<Chapter>): List<Chapter>

    suspend fun updateChapter(chapterUpdate: ChapterUpdate)

    suspend fun updateAllChapters(chapterUpdates: List<ChapterUpdate>)

    suspend fun removeChaptersWithIds(chapterIds: List<Long>)

    suspend fun getChapterByAudiobookId(audiobookId: Long): List<Chapter>

    suspend fun getBookmarkedChaptersByAudiobookId(audiobookId: Long): List<Chapter>

    suspend fun getChapterById(id: Long): Chapter?

    suspend fun getChapterByAudiobookIdAsFlow(audiobookId: Long): Flow<List<Chapter>>

    suspend fun getChapterByUrlAndAudiobookId(url: String, audiobookId: Long): Chapter?
}
