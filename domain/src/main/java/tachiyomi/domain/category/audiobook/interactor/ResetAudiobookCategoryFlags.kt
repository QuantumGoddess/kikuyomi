package tachiyomi.domain.category.audiobook.interactor

import tachiyomi.domain.category.audiobook.repository.AudiobookCategoryRepository
import tachiyomi.domain.library.model.plus
import tachiyomi.domain.library.service.LibraryPreferences

class ResetAudiobookCategoryFlags(
    private val preferences: LibraryPreferences,
    private val categoryRepository: AudiobookCategoryRepository,
) {

    suspend fun await() {
        val sort = preferences.audiobookSortingMode().get()
        categoryRepository.updateAllAudiobookCategoryFlags(sort.type + sort.direction)
    }
}
