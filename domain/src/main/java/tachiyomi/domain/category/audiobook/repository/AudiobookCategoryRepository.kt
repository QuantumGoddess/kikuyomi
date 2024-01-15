package tachiyomi.domain.category.audiobook.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.model.CategoryUpdate

interface AudiobookCategoryRepository {

    suspend fun getAudiobookCategory(id: Long): Category?

    suspend fun getAllAudiobookCategories(): List<Category>

    suspend fun getAllVisibleAudiobookCategories(): List<Category>

    fun getAllAudiobookCategoriesAsFlow(): Flow<List<Category>>

    fun getAllVisibleAudiobookCategoriesAsFlow(): Flow<List<Category>>

    suspend fun getCategoriesByAudiobookId(audiobookId: Long): List<Category>

    suspend fun getVisibleCategoriesByAudiobookId(audiobookId: Long): List<Category>

    fun getCategoriesByAudiobookIdAsFlow(audiobookId: Long): Flow<List<Category>>

    fun getVisibleCategoriesByAudiobookIdAsFlow(audiobookId: Long): Flow<List<Category>>

    suspend fun insertAudiobookCategory(category: Category)

    suspend fun updatePartialAudiobookCategory(update: CategoryUpdate)

    suspend fun updatePartialAudiobookCategories(updates: List<CategoryUpdate>)

    suspend fun updateAllAudiobookCategoryFlags(flags: Long?)

    suspend fun deleteAudiobookCategory(categoryId: Long)
}
