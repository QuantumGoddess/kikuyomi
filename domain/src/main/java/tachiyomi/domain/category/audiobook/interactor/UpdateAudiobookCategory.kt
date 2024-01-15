package tachiyomi.domain.category.audiobook.interactor

import tachiyomi.core.util.lang.withNonCancellableContext
import tachiyomi.domain.category.audiobook.repository.AudiobookCategoryRepository
import tachiyomi.domain.category.model.CategoryUpdate

class UpdateAudiobookCategory(
    private val categoryRepository: AudiobookCategoryRepository,
) {

    suspend fun await(payload: CategoryUpdate): Result = withNonCancellableContext {
        try {
            categoryRepository.updatePartialAudiobookCategory(payload)
            Result.Success
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    sealed interface Result {
        data object Success : Result
        data class Error(val error: Exception) : Result
    }
}
