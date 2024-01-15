package tachiyomi.domain.category.audiobook.interactor

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.category.audiobook.repository.AudiobookCategoryRepository
import tachiyomi.domain.category.model.Category

class GetVisibleAudiobookCategories(
    private val categoryRepository: AudiobookCategoryRepository,
) {
    fun subscribe(): Flow<List<Category>> {
        return categoryRepository.getAllVisibleAudiobookCategoriesAsFlow()
    }

    fun subscribe(audiobookId: Long): Flow<List<Category>> {
        return categoryRepository.getVisibleCategoriesByAudiobookIdAsFlow(audiobookId)
    }

    suspend fun await(): List<Category> {
        return categoryRepository.getAllVisibleAudiobookCategories()
    }

    suspend fun await(audiobookId: Long): List<Category> {
        return categoryRepository.getVisibleCategoriesByAudiobookId(audiobookId)
    }
}
