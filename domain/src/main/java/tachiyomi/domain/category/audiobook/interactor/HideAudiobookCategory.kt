package tachiyomi.domain.category.audiobook.interactor

import logcat.LogPriority
import tachiyomi.core.util.lang.withNonCancellableContext
import tachiyomi.core.util.system.logcat
import tachiyomi.domain.category.audiobook.repository.AudiobookCategoryRepository
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.model.CategoryUpdate

class HideAudiobookCategory(
    private val categoryRepository: AudiobookCategoryRepository,
) {

    suspend fun await(category: Category) = withNonCancellableContext {
        val update = CategoryUpdate(
            id = category.id,
            hidden = !category.hidden,
        )

        try {
            categoryRepository.updatePartialAudiobookCategory(update)
            RenameAudiobookCategory.Result.Success
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            Result.InternalError(e)
        }
    }

    sealed class Result {
        data object Success : Result()
        data class InternalError(val error: Throwable) : Result()
    }
}
