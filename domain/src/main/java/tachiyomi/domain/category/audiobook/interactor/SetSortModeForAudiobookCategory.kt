package tachiyomi.domain.category.audiobook.interactor

import tachiyomi.domain.category.audiobook.repository.AudiobookCategoryRepository
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.model.CategoryUpdate
import tachiyomi.domain.library.audiobook.model.AudiobookLibrarySort
import tachiyomi.domain.library.model.plus
import tachiyomi.domain.library.service.LibraryPreferences

class SetSortModeForAudiobookCategory(
    private val preferences: LibraryPreferences,
    private val categoryRepository: AudiobookCategoryRepository,
) {

    suspend fun await(
        categoryId: Long?,
        type: AudiobookLibrarySort.Type,
        direction: AudiobookLibrarySort.Direction,
    ) {
        val category = categoryId?.let { categoryRepository.getAudiobookCategory(it) }
        val flags = (category?.flags ?: 0) + type + direction
        if (category != null && preferences.categorizedDisplaySettings().get()) {
            categoryRepository.updatePartialAudiobookCategory(
                CategoryUpdate(
                    id = category.id,
                    flags = flags,
                ),
            )
        } else {
            preferences.audiobookSortingMode().set(AudiobookLibrarySort(type, direction))
            categoryRepository.updateAllAudiobookCategoryFlags(flags)
        }
    }

    suspend fun await(
        category: Category?,
        type: AudiobookLibrarySort.Type,
        direction: AudiobookLibrarySort.Direction,
    ) {
        await(category?.id, type, direction)
    }
}
