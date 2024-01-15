package tachiyomi.data.entries.audiobook

import kotlinx.coroutines.flow.Flow
import logcat.LogPriority
import tachiyomi.core.util.system.logcat
import tachiyomi.data.AudiobookUpdateStrategyColumnAdapter
import tachiyomi.data.StringListColumnAdapter
import tachiyomi.data.handlers.audiobook.AudiobookDatabaseHandler
import tachiyomi.domain.entries.audiobook.model.Audiobook
import tachiyomi.domain.entries.audiobook.model.AudiobookUpdate
import tachiyomi.domain.entries.audiobook.repository.AudiobookRepository
import tachiyomi.domain.library.audiobook.LibraryAudiobook

class AudiobookRepositoryImpl(
    private val handler: AudiobookDatabaseHandler,
) : AudiobookRepository {

    override suspend fun getAudiobookById(id: Long): Audiobook {
        return handler.awaitOne { audiobooksQueries.getAudiobookById(id, AudiobookMapper::mapAudiobook) }
    }

    override suspend fun getAudiobookByIdAsFlow(id: Long): Flow<Audiobook> {
        return handler.subscribeToOne { audiobooksQueries.getAudiobookById(id, AudiobookMapper::mapAudiobook) }
    }

    override suspend fun getAudiobookByUrlAndSourceId(url: String, sourceId: Long): Audiobook? {
        return handler.awaitOneOrNull {
            audiobooksQueries.getAudiobookByUrlAndSource(
                url,
                sourceId,
                AudiobookMapper::mapAudiobook,
            )
        }
    }

    override fun getAudiobookByUrlAndSourceIdAsFlow(url: String, sourceId: Long): Flow<Audiobook?> {
        return handler.subscribeToOneOrNull {
            audiobooksQueries.getAudiobookByUrlAndSource(
                url,
                sourceId,
                AudiobookMapper::mapAudiobook,
            )
        }
    }

    override suspend fun getAudiobookFavorites(): List<Audiobook> {
        return handler.awaitList { audiobooksQueries.getFavorites(AudiobookMapper::mapAudiobook) }
    }

    override suspend fun getLibraryAudiobook(): List<LibraryAudiobook> {
        return handler.awaitList { audiobooklibViewQueries.audiobooklib(AudiobookMapper::mapLibraryAudiobook) }
    }

    override fun getLibraryAudiobookAsFlow(): Flow<List<LibraryAudiobook>> {
        return handler.subscribeToList { audiobooklibViewQueries.audiobooklib(AudiobookMapper::mapLibraryAudiobook) }
    }

    override fun getAudiobookFavoritesBySourceId(sourceId: Long): Flow<List<Audiobook>> {
        return handler.subscribeToList { audiobooksQueries.getFavoriteBySourceId(sourceId, AudiobookMapper::mapAudiobook) }
    }

    override suspend fun getDuplicateLibraryAudiobook(id: Long, title: String): List<Audiobook> {
        return handler.awaitList {
            audiobooksQueries.getDuplicateLibraryAudiobook(title, id, AudiobookMapper::mapAudiobook)
        }
    }

    override suspend fun resetAudiobookViewerFlags(): Boolean {
        return try {
            handler.await { audiobooksQueries.resetViewerFlags() }
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            false
        }
    }

    override suspend fun setAudiobookCategories(audiobookId: Long, categoryIds: List<Long>) {
        handler.await(inTransaction = true) {
            audiobooks_categoriesQueries.deleteAudiobookCategoryByAudiobookId(audiobookId)
            categoryIds.map { categoryId ->
                audiobooks_categoriesQueries.insert(audiobookId, categoryId)
            }
        }
    }

    override suspend fun insertAudiobook(audiobook: Audiobook): Long? {
        return handler.awaitOneOrNullExecutable(inTransaction = true) {
            audiobooksQueries.insert(
                source = audiobook.source,
                url = audiobook.url,
                artist = audiobook.artist,
                author = audiobook.author,
                description = audiobook.description,
                genre = audiobook.genre,
                title = audiobook.title,
                status = audiobook.status,
                thumbnailUrl = audiobook.thumbnailUrl,
                favorite = audiobook.favorite,
                lastUpdate = audiobook.lastUpdate,
                nextUpdate = audiobook.nextUpdate,
                calculateInterval = audiobook.fetchInterval.toLong(),
                initialized = audiobook.initialized,
                viewerFlags = audiobook.viewerFlags,
                chapterFlags = audiobook.chapterFlags,
                coverLastModified = audiobook.coverLastModified,
                dateAdded = audiobook.dateAdded,
                updateStrategy = audiobook.updateStrategy,
            )
            audiobooksQueries.selectLastInsertedRowId()
        }
    }

    override suspend fun updateAudiobook(update: AudiobookUpdate): Boolean {
        return try {
            partialUpdateAudiobook(update)
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            false
        }
    }

    override suspend fun updateAllAudiobook(audiobookUpdates: List<AudiobookUpdate>): Boolean {
        return try {
            partialUpdateAudiobook(*audiobookUpdates.toTypedArray())
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            false
        }
    }

    private suspend fun partialUpdateAudiobook(vararg audiobookUpdates: AudiobookUpdate) {
        handler.await(inTransaction = true) {
            audiobookUpdates.forEach { value ->
                audiobooksQueries.update(
                    source = value.source,
                    url = value.url,
                    artist = value.artist,
                    author = value.author,
                    description = value.description,
                    genre = value.genre?.let(StringListColumnAdapter::encode),
                    title = value.title,
                    status = value.status,
                    thumbnailUrl = value.thumbnailUrl,
                    favorite = value.favorite,
                    lastUpdate = value.lastUpdate,
                    nextUpdate = value.nextUpdate,
                    calculateInterval = value.fetchInterval?.toLong(),
                    initialized = value.initialized,
                    viewer = value.viewerFlags,
                    chapterFlags = value.chapterFlags,
                    coverLastModified = value.coverLastModified,
                    dateAdded = value.dateAdded,
                    audiobookId = value.id,
                    updateStrategy = value.updateStrategy?.let(AudiobookUpdateStrategyColumnAdapter::encode),
                )
            }
        }
    }
}
