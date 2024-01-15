package tachiyomi.domain.category.audiobook.interactor

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.category.audiobook.repository.AudiobookCategoryRepository
import tachiyomi.domain.category.model.Category

class GetAudiobookCategories(
    private val categoryRepository: AudiobookCategoryRepository,
) {

    fun subscribe(): Flow<List<Category>> {
        return categoryRepository.getAllAudiobookCategoriesAsFlow()
    }

    fun subscribe(audiobookId: Long): Flow<List<Category>> {
        return categoryRepository.getCategoriesByAudiobookIdAsFlow(audiobookId)
    }

    suspend fun await(): List<Category> {
        return categoryRepository.getAllAudiobookCategories()
    }

    suspend fun await(audiobookId: Long): List<Category> {
        return categoryRepository.getCategoriesByAudiobookId(audiobookId)
    }
}
