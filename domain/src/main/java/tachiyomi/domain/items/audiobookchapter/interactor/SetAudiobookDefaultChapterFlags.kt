package tachiyomi.domain.items.audiobookchapter.interactor

import tachiyomi.core.util.lang.withNonCancellableContext
import tachiyomi.domain.entries.audiobook.interactor.GetAudiobookFavorites
import tachiyomi.domain.entries.audiobook.interactor.SetAudiobookChapterFlags
import tachiyomi.domain.entries.audiobook.model.Audiobook
import tachiyomi.domain.library.service.LibraryPreferences

class SetAudiobookDefaultChapterFlags(
    private val libraryPreferences: LibraryPreferences,
    private val setAudiobookChapterFlags: SetAudiobookChapterFlags,
    private val getFavorites: GetAudiobookFavorites,
) {

    suspend fun await(audiobook: Audiobook) {
        withNonCancellableContext {
            with(libraryPreferences) {
                setAudiobookChapterFlags.awaitSetAllFlags(
                    audiobookId = audiobook.id,
                    unreadFilter = filterChapterByRead().get(),
                    downloadedFilter = filterChapterByDownloaded().get(),
                    bookmarkedFilter = filterChapterByBookmarked().get(),
                    sortingMode = sortChapterBySourceOrNumber().get(),
                    sortingDirection = sortChapterByAscendingOrDescending().get(),
                    displayMode = displayChapterByNameOrNumber().get(),
                )
            }
        }
    }

    suspend fun awaitAll() {
        withNonCancellableContext {
            getFavorites.await().forEach { await(it) }
        }
    }
}
