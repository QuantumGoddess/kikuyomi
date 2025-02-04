package tachiyomi.domain.category.audiobook.interactor

import logcat.LogPriority
import tachiyomi.core.util.lang.withNonCancellableContext
import tachiyomi.core.util.system.logcat
import tachiyomi.domain.category.audiobook.repository.AudiobookCategoryRepository
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.library.service.LibraryPreferences

class CreateAudiobookCategoryWithName(
    private val categoryRepository: AudiobookCategoryRepository,
    private val preferences: LibraryPreferences,
) {

    private val initialFlags: Long
        get() {
            val sort = preferences.audiobookSortingMode().get()
            return sort.type.flag or sort.direction.flag
        }

    suspend fun await(name: String): Result = withNonCancellableContext {
        val categories = categoryRepository.getAllAudiobookCategories()
        val nextOrder = categories.maxOfOrNull { it.order }?.plus(1) ?: 0
        val newCategory = Category(
            id = 0,
            name = name,
            order = nextOrder,
            flags = initialFlags,
            hidden = false,
        )

        try {
            categoryRepository.insertAudiobookCategory(newCategory)
            Result.Success
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            Result.InternalError(e)
        }
    }

    sealed interface Result {
        data object Success : Result
        data class InternalError(val error: Throwable) : Result
    }
}
