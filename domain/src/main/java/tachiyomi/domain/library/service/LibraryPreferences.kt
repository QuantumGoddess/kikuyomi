package tachiyomi.domain.library.service

import tachiyomi.core.preference.Preference
import tachiyomi.core.preference.PreferenceStore
import tachiyomi.core.preference.TriState
import tachiyomi.core.preference.getEnum
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.audiobook.model.Audiobook
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.library.anime.model.AnimeLibrarySort
import tachiyomi.domain.library.audiobook.model.AudiobookLibrarySort
import tachiyomi.domain.library.manga.model.MangaLibrarySort
import tachiyomi.domain.library.model.LibraryDisplayMode

class LibraryPreferences(
    private val preferenceStore: PreferenceStore,
) {

    // Common options

    fun bottomNavStyle() = preferenceStore.getInt("bottom_nav_style", 0)

    fun isDefaultHomeTabLibraryManga() =
        preferenceStore.getBoolean("default_home_tab_library", false)

    fun displayMode() = preferenceStore.getObject(
        "pref_display_mode_library",
        LibraryDisplayMode.default,
        LibraryDisplayMode.Serializer::serialize,
        LibraryDisplayMode.Serializer::deserialize,
    )

    fun mangaSortingMode() = preferenceStore.getObject(
        "library_sorting_mode",
        MangaLibrarySort.default,
        MangaLibrarySort.Serializer::serialize,
        MangaLibrarySort.Serializer::deserialize,
    )

    fun animeSortingMode() = preferenceStore.getObject(
        "animelib_sorting_mode",
        AnimeLibrarySort.default,
        AnimeLibrarySort.Serializer::serialize,
        AnimeLibrarySort.Serializer::deserialize,
    )

    fun audiobookSortingMode() = preferenceStore.getObject(
        "audiobooklib_sorting_mode",
        AudiobookLibrarySort.default,
        AudiobookLibrarySort.Serializer::serialize,
        AudiobookLibrarySort.Serializer::deserialize,
    )

    fun lastUpdatedTimestamp() = preferenceStore.getLong(Preference.appStateKey("library_update_last_timestamp"), 0L)
    fun autoUpdateInterval() = preferenceStore.getInt("pref_library_update_interval_key", 0)

    fun autoUpdateDeviceRestrictions() = preferenceStore.getStringSet(
        "library_update_restriction",
        setOf(
            DEVICE_ONLY_ON_WIFI,
        ),
    )

    fun autoUpdateItemRestrictions() = preferenceStore.getStringSet(
        "library_update_manga_restriction",
        setOf(
            ENTRY_HAS_UNVIEWED,
            ENTRY_NON_COMPLETED,
            ENTRY_NON_VIEWED,
            ENTRY_OUTSIDE_RELEASE_PERIOD,
        ),
    )

    fun autoUpdateMetadata() = preferenceStore.getBoolean("auto_update_metadata", false)

    fun showContinueViewingButton() =
        preferenceStore.getBoolean("display_continue_reading_button", false)

    // Common Category

    fun categoryTabs() = preferenceStore.getBoolean("display_category_tabs", true)

    fun categoryNumberOfItems() = preferenceStore.getBoolean("display_number_of_items", false)

    fun categorizedDisplaySettings() = preferenceStore.getBoolean("categorized_display", false)

    fun hideHiddenCategoriesSettings() = preferenceStore.getBoolean("hidden_categories", false)

    // Common badges

    fun downloadBadge() = preferenceStore.getBoolean("display_download_badge", false)

    fun localBadge() = preferenceStore.getBoolean("display_local_badge", true)

    fun languageBadge() = preferenceStore.getBoolean("display_language_badge", false)

    fun newShowUpdatesCount() = preferenceStore.getBoolean("library_show_updates_count", true)

    // Common Cache

    fun autoClearItemCache() = preferenceStore.getBoolean("auto_clear_chapter_cache", false)

    // Mixture Columns

    fun animePortraitColumns() = preferenceStore.getInt("pref_animelib_columns_portrait_key", 0)
    fun audiobookPortraitColumns() = preferenceStore.getInt("pref_audiobooklib_columns_portrait_key", 0)
    fun mangaPortraitColumns() = preferenceStore.getInt("pref_library_columns_portrait_key", 0)

    fun animeLandscapeColumns() = preferenceStore.getInt("pref_animelib_columns_landscape_key", 0)
    fun audiobookLandscapeColumns() = preferenceStore.getInt("pref_audiobooklib_columns_landscape_key", 0)
    fun mangaLandscapeColumns() = preferenceStore.getInt("pref_library_columns_landscape_key", 0)

    // Mixture Filter

    fun filterDownloadedAnime() =
        preferenceStore.getEnum("pref_filter_animelib_downloaded_v2", TriState.DISABLED)
    fun filterDownloadedAudiobooks() =
        preferenceStore.getEnum("pref_filter_audiobooklib_downloaded_v2", TriState.DISABLED)

    fun filterDownloadedManga() =
        preferenceStore.getEnum("pref_filter_library_downloaded_v2", TriState.DISABLED)

    fun filterUnseen() =
        preferenceStore.getEnum("pref_filter_animelib_unread_v2", TriState.DISABLED)

    fun filterUnread() =
        preferenceStore.getEnum("pref_filter_library_unread_v2", TriState.DISABLED)

    fun filterStartedAnime() =
        preferenceStore.getEnum("pref_filter_animelib_started_v2", TriState.DISABLED)
    fun filterStartedAudiobooks() =
        preferenceStore.getEnum("pref_filter_audiobooklib_started_v2", TriState.DISABLED)

    fun filterStartedManga() =
        preferenceStore.getEnum("pref_filter_library_started_v2", TriState.DISABLED)

    fun filterBookmarkedAnime() =
        preferenceStore.getEnum("pref_filter_animelib_bookmarked_v2", TriState.DISABLED)
    fun filterBookmarkedAudiobooks() =
        preferenceStore.getEnum("pref_filter_audiobooklib_bookmarked_v2", TriState.DISABLED)

    fun filterBookmarkedManga() =
        preferenceStore.getEnum("pref_filter_library_bookmarked_v2", TriState.DISABLED)

    fun filterCompletedAnime() =
        preferenceStore.getEnum("pref_filter_animelib_completed_v2", TriState.DISABLED)
    fun filterCompletedAudiobooks() =
        preferenceStore.getEnum("pref_filter_audiobooklib_completed_v2", TriState.DISABLED)

    fun filterCompletedManga() =
        preferenceStore.getEnum("pref_filter_library_completed_v2", TriState.DISABLED)

    fun filterIntervalCustomAnime() = preferenceStore.getEnum(
        "pref_filter_anime_library_interval_custom",
        TriState.DISABLED,
    )

    fun filterIntervalCustomManga() = preferenceStore.getEnum(
        "pref_filter_manga_library_interval_custom",
        TriState.DISABLED,
    )

    fun filterIntervalLongAnime() = preferenceStore.getEnum(
        "pref_filter_anime_library_interval_long",
        TriState.DISABLED,
    )

    fun filterIntervalLongManga() = preferenceStore.getEnum(
        "pref_filter_manga_library_interval_long",
        TriState.DISABLED,
    )

    fun filterIntervalLateAnime() = preferenceStore.getEnum(
        "pref_filter_anime_library_interval_late",
        TriState.DISABLED,
    )

    fun filterIntervalLateManga() = preferenceStore.getEnum(
        "pref_filter_manga_library_interval_late",
        TriState.DISABLED,
    )

    fun filterIntervalDroppedAnime() = preferenceStore.getEnum(
        "pref_filter_anime_library_interval_dropped",
        TriState.DISABLED,
    )

    fun filterIntervalDroppedManga() = preferenceStore.getEnum(
        "pref_filter_manga_library_interval_dropped",
        TriState.DISABLED,
    )

    fun filterIntervalPassedAnime() = preferenceStore.getEnum(
        "pref_filter_anime_library_interval_passed",
        TriState.DISABLED,
    )

    fun filterIntervalPassedManga() = preferenceStore.getEnum(
        "pref_filter_manga_library_interval_passed",
        TriState.DISABLED,
    )

    fun filterTrackedAnime(id: Int) =
        preferenceStore.getEnum("pref_filter_animelib_tracked_${id}_v2", TriState.DISABLED)
    fun filterTrackedAudiobooks(id: Int) =
        preferenceStore.getEnum("pref_filter_audiobooklib_tracked_${id}_v2", TriState.DISABLED)

    fun filterTrackedManga(id: Int) =
        preferenceStore.getEnum("pref_filter_library_tracked_${id}_v2", TriState.DISABLED)

    // Mixture Update Count

    fun newMangaUpdatesCount() = preferenceStore.getInt("library_unread_updates_count", 0)
    fun newAnimeUpdatesCount() = preferenceStore.getInt("library_unseen_updates_count", 0)
    fun newAudiobookUpdatesCount() = preferenceStore.getInt("library_unreadaudiobook_updates_count", 0)

    // Mixture Category

    fun defaultAnimeCategory() = preferenceStore.getInt("default_anime_category", -1)
    fun defaultAudiobookCategory() = preferenceStore.getInt("default_audiobook_category", -1)
    fun defaultMangaCategory() = preferenceStore.getInt("default_category", -1)

    fun lastUsedAnimeCategory() = preferenceStore.getInt(Preference.appStateKey("last_used_anime_category"), 0)
    fun lastUsedAudiobookCategory() = preferenceStore.getInt(Preference.appStateKey("last_used_audiobook_category"), 0)
    fun lastUsedMangaCategory() = preferenceStore.getInt(Preference.appStateKey("last_used_category"), 0)

    fun animeUpdateCategories() =
        preferenceStore.getStringSet("animelib_update_categories", emptySet())
    fun audiobookUpdateCategories() =
        preferenceStore.getStringSet("audiobooklib_update_categories", emptySet())

    fun mangaUpdateCategories() =
        preferenceStore.getStringSet("library_update_categories", emptySet())

    fun animeUpdateCategoriesExclude() =
        preferenceStore.getStringSet("animelib_update_categories_exclude", emptySet())
    fun audiobookUpdateCategoriesExclude() =
        preferenceStore.getStringSet("audiobooklib_update_categories_exclude", emptySet())

    fun mangaUpdateCategoriesExclude() =
        preferenceStore.getStringSet("library_update_categories_exclude", emptySet())

    // Mixture Item

    fun filterEpisodeBySeen() =
        preferenceStore.getLong("default_episode_filter_by_seen", Anime.SHOW_ALL)

    fun filterChapterByRead() =
        preferenceStore.getLong("default_chapter_filter_by_read", Manga.SHOW_ALL)

    fun filterEpisodeByDownloaded() =
        preferenceStore.getLong("default_episode_filter_by_downloaded", Anime.SHOW_ALL)

    fun filterChapterByDownloaded() =
        preferenceStore.getLong("default_chapter_filter_by_downloaded", Manga.SHOW_ALL)

    fun filterEpisodeByBookmarked() =
        preferenceStore.getLong("default_episode_filter_by_bookmarked", Anime.SHOW_ALL)

    fun filterChapterByBookmarked() =
        preferenceStore.getLong("default_chapter_filter_by_bookmarked", Manga.SHOW_ALL)

    // and upload date
    fun sortEpisodeBySourceOrNumber() = preferenceStore.getLong(
        "default_episode_sort_by_source_or_number",
        Anime.EPISODE_SORTING_SOURCE,
    )

    fun sortChapterBySourceOrNumber() = preferenceStore.getLong(
        "default_chapter_sort_by_source_or_number",
        Manga.CHAPTER_SORTING_SOURCE,
    )

    fun displayEpisodeByNameOrNumber() = preferenceStore.getLong(
        "default_chapter_display_by_name_or_number",
        Anime.EPISODE_DISPLAY_NAME,
    )

    fun displayChapterByNameOrNumber() = preferenceStore.getLong(
        "default_chapter_display_by_name_or_number",
        Manga.CHAPTER_DISPLAY_NAME,
    )

    fun sortEpisodeByAscendingOrDescending() = preferenceStore.getLong(
        "default_chapter_sort_by_ascending_or_descending",
        Anime.EPISODE_SORT_DESC,
    )

    fun sortChapterByAscendingOrDescending() = preferenceStore.getLong(
        "default_chapter_sort_by_ascending_or_descending",
        Manga.CHAPTER_SORT_DESC,
    )

    fun setEpisodeSettingsDefault(anime: Anime) {
        filterEpisodeBySeen().set(anime.unseenFilterRaw)
        filterEpisodeByDownloaded().set(anime.downloadedFilterRaw)
        filterEpisodeByBookmarked().set(anime.bookmarkedFilterRaw)
        sortEpisodeBySourceOrNumber().set(anime.sorting)
        displayEpisodeByNameOrNumber().set(anime.displayMode)
        sortEpisodeByAscendingOrDescending().set(
            if (anime.sortDescending()) Anime.EPISODE_SORT_DESC else Anime.EPISODE_SORT_ASC,
        )
    }

    fun setChapterSettingsDefault(manga: Manga) {
        filterChapterByRead().set(manga.unreadFilterRaw)
        filterChapterByDownloaded().set(manga.downloadedFilterRaw)
        filterChapterByBookmarked().set(manga.bookmarkedFilterRaw)
        sortChapterBySourceOrNumber().set(manga.sorting)
        displayChapterByNameOrNumber().set(manga.displayMode)
        sortChapterByAscendingOrDescending().set(
            if (manga.sortDescending()) Manga.CHAPTER_SORT_DESC else Manga.CHAPTER_SORT_ASC,
        )
    }

    fun setAudiobookChapterSettingsDefault(audiobook: Audiobook) {
        filterChapterByRead().set(audiobook.unreadFilterRaw)
        filterChapterByDownloaded().set(audiobook.downloadedFilterRaw)
        filterChapterByBookmarked().set(audiobook.bookmarkedFilterRaw)
        sortChapterBySourceOrNumber().set(audiobook.sorting)
        displayChapterByNameOrNumber().set(audiobook.displayMode)
        sortChapterByAscendingOrDescending().set(
            if (audiobook.sortDescending()) Manga.CHAPTER_SORT_DESC else Manga.CHAPTER_SORT_ASC,
        )
    }

    // region Swipe Actions

    fun swipeEpisodeStartAction() =
        preferenceStore.getEnum("pref_episode_swipe_end_action", EpisodeSwipeAction.ToggleSeen)

    fun swipeEpisodeEndAction() = preferenceStore.getEnum(
        "pref_episode_swipe_start_action",
        EpisodeSwipeAction.ToggleBookmark,
    )

    fun swipeChapterStartAction() =
        preferenceStore.getEnum("pref_chapter_swipe_end_action", ChapterSwipeAction.ToggleRead)

    fun swipeChapterEndAction() = preferenceStore.getEnum(
        "pref_chapter_swipe_start_action",
        ChapterSwipeAction.ToggleBookmark,
    )

    // endregion

    enum class EpisodeSwipeAction {
        ToggleSeen,
        ToggleBookmark,
        Download,
        Disabled,
    }

    enum class ChapterSwipeAction {
        ToggleRead,
        ToggleBookmark,
        Download,
        Disabled,
    }

    companion object {
        const val DEVICE_ONLY_ON_WIFI = "wifi"
        const val DEVICE_NETWORK_NOT_METERED = "network_not_metered"
        const val DEVICE_CHARGING = "ac"

        const val ENTRY_NON_COMPLETED = "manga_ongoing"
        const val ENTRY_HAS_UNVIEWED = "manga_fully_read"
        const val ENTRY_NON_VIEWED = "manga_started"
        const val ENTRY_OUTSIDE_RELEASE_PERIOD = "manga_outside_release_period"
    }
}
