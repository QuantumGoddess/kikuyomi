package tachiyomi.domain.entries.audiobook.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.entries.audiobook.model.Audiobook
import tachiyomi.domain.entries.audiobook.model.AudiobookUpdate
import tachiyomi.domain.library.audiobook.LibraryAudiobook

interface AudiobookRepository {

    suspend fun getAudiobookById(id: Long): Audiobook

    suspend fun getAudiobookByIdAsFlow(id: Long): Flow<Audiobook>

    suspend fun getAudiobookByUrlAndSourceId(url: String, sourceId: Long): Audiobook?

    fun getAudiobookByUrlAndSourceIdAsFlow(url: String, sourceId: Long): Flow<Audiobook?>

    suspend fun getAudiobookFavorites(): List<Audiobook>

    suspend fun getLibraryAudiobook(): List<LibraryAudiobook>

    fun getLibraryAudiobookAsFlow(): Flow<List<LibraryAudiobook>>

    fun getAudiobookFavoritesBySourceId(sourceId: Long): Flow<List<Audiobook>>

    suspend fun getDuplicateLibraryAudiobook(id: Long, title: String): List<Audiobook>

    suspend fun resetAudiobookViewerFlags(): Boolean

    suspend fun setAudiobookCategories(audiobookId: Long, categoryIds: List<Long>)

    suspend fun insertAudiobook(audiobook: Audiobook): Long?

    suspend fun updateAudiobook(update: AudiobookUpdate): Boolean

    suspend fun updateAllAudiobook(audiobookUpdates: List<AudiobookUpdate>): Boolean
}
