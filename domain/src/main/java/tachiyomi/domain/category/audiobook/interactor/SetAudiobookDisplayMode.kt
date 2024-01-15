package tachiyomi.domain.category.audiobook.interactor

import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.library.service.LibraryPreferences

class SetAudiobookDisplayMode(
    private val preferences: LibraryPreferences,
) {

    fun await(display: LibraryDisplayMode) {
        preferences.displayMode().set(display)
    }
}
