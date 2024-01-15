package eu.kanade.tachiyomi.ui.library.audiobook

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.base.BasePreferences
import eu.kanade.tachiyomi.data.track.TrackerManager
import tachiyomi.core.preference.Preference
import tachiyomi.core.preference.TriState
import tachiyomi.core.preference.getAndSet
import tachiyomi.core.util.lang.launchIO
import tachiyomi.domain.category.audiobook.interactor.SetAudiobookDisplayMode
import tachiyomi.domain.category.audiobook.interactor.SetSortModeForAudiobookCategory
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.library.audiobook.model.AudiobookLibrarySort
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.library.service.LibraryPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AudiobookLibrarySettingsScreenModel(
    val preferences: BasePreferences = Injekt.get(),
    val libraryPreferences: LibraryPreferences = Injekt.get(),
    private val setAudiobookDisplayMode: SetAudiobookDisplayMode = Injekt.get(),
    private val setSortModeForCategory: SetSortModeForAudiobookCategory = Injekt.get(),
    private val trackerManager: TrackerManager = Injekt.get(),
) : ScreenModel {

    val trackers
        get() = trackerManager.trackers.filter { it.isLoggedIn }

    fun toggleFilter(preference: (LibraryPreferences) -> Preference<TriState>) {
        preference(libraryPreferences).getAndSet {
            it.next()
        }
    }

    fun toggleTracker(id: Int) {
        toggleFilter { libraryPreferences.filterTrackedAudiobooks(id) }
    }

    fun setDisplayMode(mode: LibraryDisplayMode) {
        setAudiobookDisplayMode.await(mode)
    }

    fun setSort(
        category: Category?,
        mode: AudiobookLibrarySort.Type,
        direction: AudiobookLibrarySort.Direction,
    ) {
        screenModelScope.launchIO {
            setSortModeForCategory.await(category, mode, direction)
        }
    }
}
