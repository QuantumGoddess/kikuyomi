package tachiyomi.data.category.audiobook

import kikuyomi.data.AudiobookDatabase
import kotlinx.coroutines.flow.Flow
import tachiyomi.data.handlers.audiobook.AudiobookDatabaseHandler
import tachiyomi.domain.category.audiobook.repository.AudiobookCategoryRepository
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.model.CategoryUpdate

class AudiobookCategoryRepositoryImpl(
    private val handler: AudiobookDatabaseHandler,
) : AudiobookCategoryRepository {

    override suspend fun getAudiobookCategory(id: Long): Category? {
        return handler.awaitOneOrNull { categoriesQueries.getCategory(id, ::mapCategory) }
    }

    override suspend fun getAllAudiobookCategories(): List<Category> {
        return handler.awaitList { categoriesQueries.getCategories(::mapCategory) }
    }

    override suspend fun getAllVisibleAudiobookCategories(): List<Category> {
        return handler.awaitList { categoriesQueries.getVisibleCategories(::mapCategory) }
    }

    override fun getAllAudiobookCategoriesAsFlow(): Flow<List<Category>> {
        return handler.subscribeToList { categoriesQueries.getCategories(::mapCategory) }
    }

    override fun getAllVisibleAudiobookCategoriesAsFlow(): Flow<List<Category>> {
        return handler.subscribeToList { categoriesQueries.getVisibleCategories(::mapCategory) }
    }

    override suspend fun getCategoriesByAudiobookId(audiobookId: Long): List<Category> {
        return handler.awaitList {
            categoriesQueries.getCategoriesByAudiobookId(audiobookId, ::mapCategory)
        }
    }

    override suspend fun getVisibleCategoriesByAudiobookId(audiobookId: Long): List<Category> {
        return handler.awaitList {
            categoriesQueries.getVisibleCategoriesByAudiobookId(audiobookId, ::mapCategory)
        }
    }

    override fun getCategoriesByAudiobookIdAsFlow(audiobookId: Long): Flow<List<Category>> {
        return handler.subscribeToList {
            categoriesQueries.getCategoriesByAudiobookId(audiobookId, ::mapCategory)
        }
    }

    override fun getVisibleCategoriesByAudiobookIdAsFlow(audiobookId: Long): Flow<List<Category>> {
        return handler.subscribeToList {
            categoriesQueries.getVisibleCategoriesByAudiobookId(audiobookId, ::mapCategory)
        }
    }

    override suspend fun insertAudiobookCategory(category: Category) {
        handler.await {
            categoriesQueries.insert(
                name = category.name,
                order = category.order,
                flags = category.flags,
            )
        }
    }

    override suspend fun updatePartialAudiobookCategory(update: CategoryUpdate) {
        handler.await {
            updatePartialBlocking(update)
        }
    }

    override suspend fun updatePartialAudiobookCategories(updates: List<CategoryUpdate>) {
        handler.await(inTransaction = true) {
            for (update in updates) {
                updatePartialBlocking(update)
            }
        }
    }

    private fun AudiobookDatabase.updatePartialBlocking(update: CategoryUpdate) {
        categoriesQueries.update(
            name = update.name,
            order = update.order,
            flags = update.flags,
            hidden = if (update.hidden == true) 1L else 0L,
            categoryId = update.id,
        )
    }

    override suspend fun updateAllAudiobookCategoryFlags(flags: Long?) {
        handler.await {
            categoriesQueries.updateAllFlags(flags)
        }
    }

    override suspend fun deleteAudiobookCategory(categoryId: Long) {
        handler.await {
            categoriesQueries.delete(
                categoryId = categoryId,
            )
        }
    }

    private fun mapCategory(
        id: Long,
        name: String,
        order: Long,
        flags: Long,
        hidden: Long,
    ): Category {
        return Category(
            id = id,
            name = name,
            order = order,
            flags = flags,
            hidden = hidden == 1L,
        )
    }
}
