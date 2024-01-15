package eu.kanade.domain

import eu.kanade.domain.download.anime.interactor.DeleteEpisodeDownload
import eu.kanade.domain.download.manga.interactor.DeleteChapterDownload
import eu.kanade.domain.entries.anime.interactor.SetAnimeViewerFlags
import eu.kanade.domain.entries.anime.interactor.UpdateAnime
import eu.kanade.domain.entries.audiobook.interactor.SetAudiobookViewerFlags
import eu.kanade.domain.entries.audiobook.interactor.UpdateAudiobook
import eu.kanade.domain.entries.manga.interactor.GetExcludedScanlators
import eu.kanade.domain.entries.manga.interactor.SetExcludedScanlators
import eu.kanade.domain.entries.manga.interactor.SetMangaViewerFlags
import eu.kanade.domain.entries.manga.interactor.UpdateManga
import eu.kanade.domain.extension.anime.interactor.GetAnimeExtensionLanguages
import eu.kanade.domain.extension.anime.interactor.GetAnimeExtensionSources
import eu.kanade.domain.extension.anime.interactor.GetAnimeExtensionsByType
import eu.kanade.domain.extension.audiobook.interactor.GetAudiobookExtensionLanguages
import eu.kanade.domain.extension.audiobook.interactor.GetAudiobookExtensionSources
import eu.kanade.domain.extension.audiobook.interactor.GetAudiobookExtensionsByType
import eu.kanade.domain.extension.manga.interactor.GetExtensionSources
import eu.kanade.domain.extension.manga.interactor.GetMangaExtensionLanguages
import eu.kanade.domain.extension.manga.interactor.GetMangaExtensionsByType
import eu.kanade.domain.items.chapter.interactor.GetAvailableScanlators
import eu.kanade.domain.items.chapter.interactor.SetReadStatus
import eu.kanade.domain.items.chapter.interactor.SyncChaptersWithSource
import eu.kanade.domain.items.episode.interactor.SetSeenStatus
import eu.kanade.domain.items.episode.interactor.SyncEpisodesWithSource
import eu.kanade.domain.source.anime.interactor.GetAnimeSourcesWithFavoriteCount
import eu.kanade.domain.source.anime.interactor.GetEnabledAnimeSources
import eu.kanade.domain.source.anime.interactor.GetLanguagesWithAnimeSources
import eu.kanade.domain.source.anime.interactor.ToggleAnimeSource
import eu.kanade.domain.source.anime.interactor.ToggleAnimeSourcePin
import eu.kanade.domain.source.audiobook.interactor.GetAudiobookSourcesWithFavoriteCount
import eu.kanade.domain.source.audiobook.interactor.GetEnabledAudiobookSources
import eu.kanade.domain.source.audiobook.interactor.GetLanguagesWithAudiobookSources
import eu.kanade.domain.source.audiobook.interactor.ToggleAudiobookSource
import eu.kanade.domain.source.audiobook.interactor.ToggleAudiobookSourcePin
import eu.kanade.domain.source.interactor.SetMigrateSorting
import eu.kanade.domain.source.interactor.ToggleLanguage
import eu.kanade.domain.source.manga.interactor.GetEnabledMangaSources
import eu.kanade.domain.source.manga.interactor.GetLanguagesWithMangaSources
import eu.kanade.domain.source.manga.interactor.GetMangaSourcesWithFavoriteCount
import eu.kanade.domain.source.manga.interactor.ToggleMangaSource
import eu.kanade.domain.source.manga.interactor.ToggleMangaSourcePin
import eu.kanade.domain.track.anime.interactor.AddAnimeTracks
import eu.kanade.domain.track.anime.interactor.RefreshAnimeTracks
import eu.kanade.domain.track.anime.interactor.SyncEpisodeProgressWithTrack
import eu.kanade.domain.track.anime.interactor.TrackEpisode
import eu.kanade.domain.track.audiobook.interactor.AddAudiobookTracks
import eu.kanade.domain.track.audiobook.interactor.RefreshAudiobookTracks
import eu.kanade.domain.track.manga.interactor.AddMangaTracks
import eu.kanade.domain.track.manga.interactor.RefreshMangaTracks
import eu.kanade.domain.track.manga.interactor.SyncChapterProgressWithTrack
import eu.kanade.domain.track.manga.interactor.TrackChapter
import tachiyomi.data.category.anime.AnimeCategoryRepositoryImpl
import tachiyomi.data.category.audiobook.AudiobookCategoryRepositoryImpl
import tachiyomi.data.category.manga.MangaCategoryRepositoryImpl
import tachiyomi.data.entries.anime.AnimeRepositoryImpl
import tachiyomi.data.entries.audiobook.AudiobookRepositoryImpl
import tachiyomi.data.entries.manga.MangaRepositoryImpl
import tachiyomi.data.history.anime.AnimeHistoryRepositoryImpl
import tachiyomi.data.history.audiobook.AudiobookHistoryRepositoryImpl
import tachiyomi.data.history.manga.MangaHistoryRepositoryImpl
import tachiyomi.data.items.chapter.ChapterRepositoryImpl
import tachiyomi.data.items.audiobookchapter.ChapterRepositoryImpl as AudiobookChapterRepositoryImpl
import tachiyomi.data.items.episode.EpisodeRepositoryImpl
import tachiyomi.data.release.ReleaseServiceImpl
import tachiyomi.data.source.anime.AnimeSourceRepositoryImpl
import tachiyomi.data.source.anime.AnimeStubSourceRepositoryImpl
import tachiyomi.data.source.audiobook.AudiobookSourceRepositoryImpl
import tachiyomi.data.source.audiobook.AudiobookStubSourceRepositoryImpl
import tachiyomi.data.source.manga.MangaSourceRepositoryImpl
import tachiyomi.data.source.manga.MangaStubSourceRepositoryImpl
import tachiyomi.data.track.anime.AnimeTrackRepositoryImpl
import tachiyomi.data.track.audiobook.AudiobookTrackRepositoryImpl
import tachiyomi.data.track.manga.MangaTrackRepositoryImpl
import tachiyomi.data.updates.anime.AnimeUpdatesRepositoryImpl
import tachiyomi.data.updates.audiobook.AudiobookUpdatesRepositoryImpl
import tachiyomi.data.updates.manga.MangaUpdatesRepositoryImpl
import tachiyomi.domain.category.anime.interactor.CreateAnimeCategoryWithName
import tachiyomi.domain.category.anime.interactor.DeleteAnimeCategory
import tachiyomi.domain.category.anime.interactor.GetAnimeCategories
import tachiyomi.domain.category.anime.interactor.GetVisibleAnimeCategories
import tachiyomi.domain.category.anime.interactor.HideAnimeCategory
import tachiyomi.domain.category.anime.interactor.RenameAnimeCategory
import tachiyomi.domain.category.anime.interactor.ReorderAnimeCategory
import tachiyomi.domain.category.anime.interactor.ResetAnimeCategoryFlags
import tachiyomi.domain.category.anime.interactor.SetAnimeCategories
import tachiyomi.domain.category.anime.interactor.SetAnimeDisplayMode
import tachiyomi.domain.category.anime.interactor.SetSortModeForAnimeCategory
import tachiyomi.domain.category.anime.interactor.UpdateAnimeCategory
import tachiyomi.domain.category.anime.repository.AnimeCategoryRepository
import tachiyomi.domain.category.audiobook.interactor.CreateAudiobookCategoryWithName
import tachiyomi.domain.category.audiobook.interactor.DeleteAudiobookCategory
import tachiyomi.domain.category.audiobook.interactor.GetAudiobookCategories
import tachiyomi.domain.category.audiobook.interactor.GetVisibleAudiobookCategories
import tachiyomi.domain.category.audiobook.interactor.HideAudiobookCategory
import tachiyomi.domain.category.audiobook.interactor.RenameAudiobookCategory
import tachiyomi.domain.category.audiobook.interactor.ReorderAudiobookCategory
import tachiyomi.domain.category.audiobook.interactor.ResetAudiobookCategoryFlags
import tachiyomi.domain.category.audiobook.interactor.SetAudiobookCategories
import tachiyomi.domain.category.audiobook.interactor.SetAudiobookDisplayMode
import tachiyomi.domain.category.audiobook.interactor.SetSortModeForAudiobookCategory
import tachiyomi.domain.category.audiobook.interactor.UpdateAudiobookCategory
import tachiyomi.domain.category.audiobook.repository.AudiobookCategoryRepository
import tachiyomi.domain.category.manga.interactor.CreateMangaCategoryWithName
import tachiyomi.domain.category.manga.interactor.DeleteMangaCategory
import tachiyomi.domain.category.manga.interactor.GetMangaCategories
import tachiyomi.domain.category.manga.interactor.GetVisibleMangaCategories
import tachiyomi.domain.category.manga.interactor.HideMangaCategory
import tachiyomi.domain.category.manga.interactor.RenameMangaCategory
import tachiyomi.domain.category.manga.interactor.ReorderMangaCategory
import tachiyomi.domain.category.manga.interactor.ResetMangaCategoryFlags
import tachiyomi.domain.category.manga.interactor.SetMangaCategories
import tachiyomi.domain.category.manga.interactor.SetMangaDisplayMode
import tachiyomi.domain.category.manga.interactor.SetSortModeForMangaCategory
import tachiyomi.domain.category.manga.interactor.UpdateMangaCategory
import tachiyomi.domain.category.manga.repository.MangaCategoryRepository
import tachiyomi.domain.entries.anime.interactor.AnimeFetchInterval
import tachiyomi.domain.entries.anime.interactor.GetAnime
import tachiyomi.domain.entries.anime.interactor.GetAnimeFavorites
import tachiyomi.domain.entries.anime.interactor.GetAnimeWithEpisodes
import tachiyomi.domain.entries.anime.interactor.GetDuplicateLibraryAnime
import tachiyomi.domain.entries.anime.interactor.GetLibraryAnime
import tachiyomi.domain.entries.anime.interactor.GetMangaByUrlAndSourceId
import tachiyomi.domain.entries.anime.interactor.NetworkToLocalAnime
import tachiyomi.domain.entries.anime.interactor.ResetAnimeViewerFlags
import tachiyomi.domain.entries.anime.interactor.SetAnimeEpisodeFlags
import tachiyomi.domain.entries.anime.repository.AnimeRepository
import tachiyomi.domain.entries.audiobook.interactor.AudiobookFetchInterval
import tachiyomi.domain.entries.audiobook.interactor.GetAudiobook
import tachiyomi.domain.entries.audiobook.interactor.GetAudiobookByUrlAndSourceId
import tachiyomi.domain.entries.audiobook.interactor.GetAudiobookFavorites
import tachiyomi.domain.entries.audiobook.interactor.GetAudiobookWithChapters
import tachiyomi.domain.entries.audiobook.interactor.GetDuplicateLibraryAudiobook
import tachiyomi.domain.entries.audiobook.interactor.GetLibraryAudiobook
import tachiyomi.domain.entries.audiobook.interactor.NetworkToLocalAudiobook
import tachiyomi.domain.entries.audiobook.interactor.ResetAudiobookViewerFlags
import tachiyomi.domain.entries.audiobook.interactor.SetAudiobookChapterFlags
import tachiyomi.domain.entries.audiobook.repository.AudiobookRepository
import tachiyomi.domain.entries.manga.interactor.GetAnimeByUrlAndSourceId
import tachiyomi.domain.entries.manga.interactor.GetDuplicateLibraryManga
import tachiyomi.domain.entries.manga.interactor.GetLibraryManga
import tachiyomi.domain.entries.manga.interactor.GetManga
import tachiyomi.domain.entries.manga.interactor.GetMangaFavorites
import tachiyomi.domain.entries.manga.interactor.GetMangaWithChapters
import tachiyomi.domain.entries.manga.interactor.MangaFetchInterval
import tachiyomi.domain.entries.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.entries.manga.interactor.ResetMangaViewerFlags
import tachiyomi.domain.entries.manga.interactor.SetMangaChapterFlags
import tachiyomi.domain.entries.manga.repository.MangaRepository
import tachiyomi.domain.history.anime.interactor.GetAnimeHistory
import tachiyomi.domain.history.anime.interactor.GetNextEpisodes
import tachiyomi.domain.history.anime.interactor.RemoveAnimeHistory
import tachiyomi.domain.history.anime.interactor.UpsertAnimeHistory
import tachiyomi.domain.history.anime.repository.AnimeHistoryRepository
import tachiyomi.domain.history.audiobook.interactor.GetAudiobookHistory
import tachiyomi.domain.history.audiobook.interactor.RemoveAudiobookHistory
import tachiyomi.domain.history.audiobook.interactor.UpsertAudiobookHistory
import tachiyomi.domain.history.audiobook.repository.AudiobookHistoryRepository
import tachiyomi.domain.history.manga.interactor.GetMangaHistory
import tachiyomi.domain.history.manga.interactor.GetNextChapters
import tachiyomi.domain.history.manga.interactor.GetTotalReadDuration
import tachiyomi.domain.history.manga.interactor.RemoveMangaHistory
import tachiyomi.domain.history.manga.interactor.UpsertMangaHistory
import tachiyomi.domain.history.manga.repository.MangaHistoryRepository
import tachiyomi.domain.items.audiobookchapter.interactor.GetChapterByUrlAndAudiobookId
import tachiyomi.domain.items.audiobookchapter.interactor.GetChaptersByAudiobookId
import tachiyomi.domain.items.audiobookchapter.interactor.SetAudiobookDefaultChapterFlags
import tachiyomi.domain.items.chapter.interactor.GetChapter
import tachiyomi.domain.items.chapter.interactor.GetChapterByUrlAndMangaId
import tachiyomi.domain.items.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.items.chapter.interactor.SetMangaDefaultChapterFlags
import tachiyomi.domain.items.chapter.interactor.ShouldUpdateDbChapter
import tachiyomi.domain.items.chapter.interactor.UpdateChapter
import tachiyomi.domain.items.chapter.repository.ChapterRepository
import tachiyomi.domain.items.audiobookchapter.repository.ChapterRepository as AudiobookChapterRepository
import tachiyomi.domain.items.episode.interactor.GetEpisode
import tachiyomi.domain.items.episode.interactor.GetEpisodeByUrlAndAnimeId
import tachiyomi.domain.items.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.items.episode.interactor.SetAnimeDefaultEpisodeFlags
import tachiyomi.domain.items.episode.interactor.ShouldUpdateDbEpisode
import tachiyomi.domain.items.episode.interactor.UpdateEpisode
import tachiyomi.domain.items.episode.repository.EpisodeRepository
import tachiyomi.domain.release.interactor.GetApplicationRelease
import tachiyomi.domain.release.service.ReleaseService
import tachiyomi.domain.source.anime.interactor.GetAnimeSourcesWithNonLibraryAnime
import tachiyomi.domain.source.anime.interactor.GetRemoteAnime
import tachiyomi.domain.source.anime.repository.AnimeSourceRepository
import tachiyomi.domain.source.anime.repository.AnimeStubSourceRepository
import tachiyomi.domain.source.audiobook.interactor.GetAudiobookSourcesWithNonLibraryAudiobook
import tachiyomi.domain.source.audiobook.interactor.GetRemoteAudiobook
import tachiyomi.domain.source.audiobook.repository.AudiobookSourceRepository
import tachiyomi.domain.source.audiobook.repository.AudiobookStubSourceRepository
import tachiyomi.domain.source.manga.interactor.GetMangaSourcesWithNonLibraryManga
import tachiyomi.domain.source.manga.interactor.GetRemoteManga
import tachiyomi.domain.source.manga.repository.MangaSourceRepository
import tachiyomi.domain.source.manga.repository.MangaStubSourceRepository
import tachiyomi.domain.track.anime.interactor.DeleteAnimeTrack
import tachiyomi.domain.track.anime.interactor.GetAnimeTracks
import tachiyomi.domain.track.anime.interactor.GetTracksPerAnime
import tachiyomi.domain.track.anime.interactor.InsertAnimeTrack
import tachiyomi.domain.track.anime.repository.AnimeTrackRepository
import tachiyomi.domain.track.audiobook.interactor.DeleteAudiobookTrack
import tachiyomi.domain.track.audiobook.interactor.GetAudiobookTracks
import tachiyomi.domain.track.audiobook.interactor.GetTracksPerAudiobook
import tachiyomi.domain.track.audiobook.interactor.InsertAudiobookTrack
import tachiyomi.domain.track.audiobook.repository.AudiobookTrackRepository
import tachiyomi.domain.track.manga.interactor.DeleteMangaTrack
import tachiyomi.domain.track.manga.interactor.GetMangaTracks
import tachiyomi.domain.track.manga.interactor.GetTracksPerManga
import tachiyomi.domain.track.manga.interactor.InsertMangaTrack
import tachiyomi.domain.track.manga.repository.MangaTrackRepository
import tachiyomi.domain.updates.anime.interactor.GetAnimeUpdates
import tachiyomi.domain.updates.anime.repository.AnimeUpdatesRepository
import tachiyomi.domain.updates.audiobook.interactor.GetAudiobookUpdates
import tachiyomi.domain.updates.audiobook.repository.AudiobookUpdatesRepository
import tachiyomi.domain.updates.manga.interactor.GetMangaUpdates
import tachiyomi.domain.updates.manga.repository.MangaUpdatesRepository
import uy.kohesive.injekt.api.InjektModule
import uy.kohesive.injekt.api.InjektRegistrar
import uy.kohesive.injekt.api.addFactory
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get
import eu.kanade.domain.download.audiobook.interactor.DeleteChapterDownload as DeleteAudiobookChapterDownload
import eu.kanade.domain.items.audiobookchapter.interactor.SetReadStatus as SetAudiobookReadStatus
import eu.kanade.domain.items.audiobookchapter.interactor.SyncChaptersWithSource as SyncAudiobookChaptersWithSource
import eu.kanade.domain.track.audiobook.interactor.SyncChapterProgressWithTrack as SyncAudiobookChapterProgressWithTrack
import eu.kanade.domain.track.audiobook.interactor.TrackChapter as TrackAudiobookChapter
import tachiyomi.domain.history.audiobook.interactor.GetNextChapters as GetNextAudiobookChapters
import tachiyomi.domain.items.audiobookchapter.interactor.GetChapter as GetAudiobookChapter
import tachiyomi.domain.items.audiobookchapter.interactor.ShouldUpdateDbChapter as ShouldUpdateAudiobookDbChapter
import tachiyomi.domain.items.audiobookchapter.interactor.UpdateChapter as UpdateAudiobookChapter

class DomainModule : InjektModule {

    override fun InjektRegistrar.registerInjectables() {
        addSingletonFactory<AudiobookCategoryRepository> { AudiobookCategoryRepositoryImpl(get()) }
        addFactory { GetAudiobookCategories(get()) }
        addFactory { GetVisibleAudiobookCategories(get()) }
        addFactory { ResetAudiobookCategoryFlags(get(), get()) }
        addFactory { SetAudiobookDisplayMode(get()) }
        addFactory { SetSortModeForAudiobookCategory(get(), get()) }
        addFactory { CreateAudiobookCategoryWithName(get(), get()) }
        addFactory { RenameAudiobookCategory(get()) }
        addFactory { ReorderAudiobookCategory(get()) }
        addFactory { UpdateAudiobookCategory(get()) }
        addFactory { HideAudiobookCategory(get()) }
        addFactory { DeleteAudiobookCategory(get()) }
        
        addSingletonFactory<AnimeCategoryRepository> { AnimeCategoryRepositoryImpl(get()) }
        addFactory { GetAnimeCategories(get()) }
        addFactory { GetVisibleAnimeCategories(get()) }
        addFactory { ResetAnimeCategoryFlags(get(), get()) }
        addFactory { SetAnimeDisplayMode(get()) }
        addFactory { SetSortModeForAnimeCategory(get(), get()) }
        addFactory { CreateAnimeCategoryWithName(get(), get()) }
        addFactory { RenameAnimeCategory(get()) }
        addFactory { ReorderAnimeCategory(get()) }
        addFactory { UpdateAnimeCategory(get()) }
        addFactory { HideAnimeCategory(get()) }
        addFactory { DeleteAnimeCategory(get()) }

        addSingletonFactory<MangaCategoryRepository> { MangaCategoryRepositoryImpl(get()) }
        addFactory { GetMangaCategories(get()) }
        addFactory { GetVisibleMangaCategories(get()) }
        addFactory { ResetMangaCategoryFlags(get(), get()) }
        addFactory { SetMangaDisplayMode(get()) }
        addFactory { SetSortModeForMangaCategory(get(), get()) }
        addFactory { CreateMangaCategoryWithName(get(), get()) }
        addFactory { RenameMangaCategory(get()) }
        addFactory { ReorderMangaCategory(get()) }
        addFactory { UpdateMangaCategory(get()) }
        addFactory { HideMangaCategory(get()) }
        addFactory { DeleteMangaCategory(get()) }

        addSingletonFactory<AudiobookRepository> { AudiobookRepositoryImpl(get()) }
        addFactory { GetDuplicateLibraryAudiobook(get()) }
        addFactory { GetAudiobookFavorites(get()) }
        addFactory { GetLibraryAudiobook(get()) }
        addFactory { GetAudiobookWithChapters(get(), get()) }
        addFactory { GetAudiobookByUrlAndSourceId(get()) }
        addFactory { GetAudiobook(get()) }
        addFactory { GetNextAudiobookChapters(get(), get(), get()) }
        addFactory { ResetAudiobookViewerFlags(get()) }
        addFactory { SetAudiobookChapterFlags(get()) }
        addFactory { AudiobookFetchInterval(get()) }
        addFactory { SetAudiobookDefaultChapterFlags(get(), get(), get()) }
        addFactory { SetAudiobookViewerFlags(get()) }
        addFactory { NetworkToLocalAudiobook(get()) }
        addFactory { UpdateAudiobook(get(), get()) }
        addFactory { SetAudiobookCategories(get()) }

        addSingletonFactory<AnimeRepository> { AnimeRepositoryImpl(get()) }
        addFactory { GetDuplicateLibraryAnime(get()) }
        addFactory { GetAnimeFavorites(get()) }
        addFactory { GetLibraryAnime(get()) }
        addFactory { GetAnimeWithEpisodes(get(), get()) }
        addFactory { GetAnimeByUrlAndSourceId(get()) }
        addFactory { GetAnime(get()) }
        addFactory { GetNextEpisodes(get(), get(), get()) }
        addFactory { ResetAnimeViewerFlags(get()) }
        addFactory { SetAnimeEpisodeFlags(get()) }
        addFactory { AnimeFetchInterval(get()) }
        addFactory { SetAnimeDefaultEpisodeFlags(get(), get(), get()) }
        addFactory { SetAnimeViewerFlags(get()) }
        addFactory { NetworkToLocalAnime(get()) }
        addFactory { UpdateAnime(get(), get()) }
        addFactory { SetAnimeCategories(get()) }

        addSingletonFactory<MangaRepository> { MangaRepositoryImpl(get()) }
        addFactory { GetDuplicateLibraryManga(get()) }
        addFactory { GetMangaFavorites(get()) }
        addFactory { GetLibraryManga(get()) }
        addFactory { GetMangaWithChapters(get(), get()) }
        addFactory { GetMangaByUrlAndSourceId(get()) }
        addFactory { GetManga(get()) }
        addFactory { GetNextChapters(get(), get(), get()) }
        addFactory { ResetMangaViewerFlags(get()) }
        addFactory { SetMangaChapterFlags(get()) }
        addFactory { MangaFetchInterval(get()) }
        addFactory {
            SetMangaDefaultChapterFlags(
                get(),
                get(),
                get(),
            )
        }
        addFactory { SetMangaViewerFlags(get()) }
        addFactory { NetworkToLocalManga(get()) }
        addFactory { UpdateManga(get(), get()) }
        addFactory { SetMangaCategories(get()) }
        addFactory { GetExcludedScanlators(get()) }
        addFactory { SetExcludedScanlators(get()) }

        addSingletonFactory<ReleaseService> { ReleaseServiceImpl(get(), get()) }
        addFactory { GetApplicationRelease(get(), get()) }

        addSingletonFactory<AudiobookTrackRepository> { AudiobookTrackRepositoryImpl(get()) }
        addFactory { TrackAudiobookChapter(get(), get(), get(), get()) }
        addFactory { AddAudiobookTracks(get(), get(), get(), get()) }
        addFactory { RefreshAudiobookTracks(get(), get(), get(), get()) }
        addFactory { DeleteAudiobookTrack(get()) }
        addFactory { GetTracksPerAudiobook(get()) }
        addFactory { GetAudiobookTracks(get()) }
        addFactory { InsertAudiobookTrack(get()) }
        addFactory { SyncAudiobookChapterProgressWithTrack(get(), get(), get()) }

        addSingletonFactory<AnimeTrackRepository> { AnimeTrackRepositoryImpl(get()) }
        addFactory { TrackEpisode(get(), get(), get(), get()) }
        addFactory { AddAnimeTracks(get(), get(), get(), get()) }
        addFactory { RefreshAnimeTracks(get(), get(), get(), get()) }
        addFactory { DeleteAnimeTrack(get()) }
        addFactory { GetTracksPerAnime(get()) }
        addFactory { GetAnimeTracks(get()) }
        addFactory { InsertAnimeTrack(get()) }
        addFactory { SyncEpisodeProgressWithTrack(get(), get(), get()) }

        addSingletonFactory<MangaTrackRepository> { MangaTrackRepositoryImpl(get()) }
        addFactory { TrackChapter(get(), get(), get(), get()) }
        addFactory { AddMangaTracks(get(), get(), get(), get()) }
        addFactory { RefreshMangaTracks(get(), get(), get(), get()) }
        addFactory { DeleteMangaTrack(get()) }
        addFactory { GetTracksPerManga(get()) }
        addFactory { GetMangaTracks(get()) }
        addFactory { InsertMangaTrack(get()) }
        addFactory { SyncChapterProgressWithTrack(get(), get(), get()) }

        addSingletonFactory<AudiobookChapterRepository> { AudiobookChapterRepositoryImpl(get()) }
        addFactory { GetAudiobookChapter(get()) }
        addFactory { GetChaptersByAudiobookId(get()) }
        addFactory { GetChapterByUrlAndAudiobookId(get()) }
        addFactory { UpdateAudiobookChapter(get()) }
        addFactory { SetAudiobookReadStatus(get(), get(), get(), get()) }
        addFactory { ShouldUpdateAudiobookDbChapter() }
        addFactory { SyncAudiobookChaptersWithSource(get(), get(), get(), get(), get(), get(), get()) }

        addSingletonFactory<EpisodeRepository> { EpisodeRepositoryImpl(get()) }
        addFactory { GetEpisode(get()) }
        addFactory { GetEpisodesByAnimeId(get()) }
        addFactory { GetEpisodeByUrlAndAnimeId(get()) }
        addFactory { UpdateEpisode(get()) }
        addFactory { SetSeenStatus(get(), get(), get(), get()) }
        addFactory { ShouldUpdateDbEpisode() }
        addFactory { SyncEpisodesWithSource(get(), get(), get(), get(), get(), get(), get()) }

        addSingletonFactory<ChapterRepository> { ChapterRepositoryImpl(get()) }
        addFactory { GetChapter(get()) }
        addFactory { GetChaptersByMangaId(get()) }
        addFactory { GetChapterByUrlAndMangaId(get()) }
        addFactory { UpdateChapter(get()) }
        addFactory { SetReadStatus(get(), get(), get(), get()) }
        addFactory { ShouldUpdateDbChapter() }
        addFactory { SyncChaptersWithSource(get(), get(), get(), get(), get(), get(), get(), get()) }
        addFactory { GetAvailableScanlators(get()) }

        addSingletonFactory<AudiobookHistoryRepository> { AudiobookHistoryRepositoryImpl(get()) }
        addFactory { GetAudiobookHistory(get()) }
        addFactory { UpsertAudiobookHistory(get()) }
        addFactory { RemoveAudiobookHistory(get()) }

        addFactory { DeleteAudiobookChapterDownload(get(), get()) }

        addFactory { GetAudiobookExtensionsByType(get(), get()) }
        addFactory { GetAudiobookExtensionSources(get()) }
        addFactory { GetAudiobookExtensionLanguages(get(), get()) }

        addSingletonFactory<AnimeHistoryRepository> { AnimeHistoryRepositoryImpl(get()) }
        addFactory { GetAnimeHistory(get()) }
        addFactory { UpsertAnimeHistory(get()) }
        addFactory { RemoveAnimeHistory(get()) }

        addFactory { DeleteEpisodeDownload(get(), get()) }

        addFactory { GetAnimeExtensionsByType(get(), get()) }
        addFactory { GetAnimeExtensionSources(get()) }
        addFactory { GetAnimeExtensionLanguages(get(), get()) }

        addSingletonFactory<MangaHistoryRepository> { MangaHistoryRepositoryImpl(get()) }
        addFactory { GetMangaHistory(get()) }
        addFactory { UpsertMangaHistory(get()) }
        addFactory { RemoveMangaHistory(get()) }
        addFactory { GetTotalReadDuration(get()) }

        addFactory { DeleteChapterDownload(get(), get()) }

        addFactory { GetMangaExtensionsByType(get(), get()) }
        addFactory { GetExtensionSources(get()) }
        addFactory { GetMangaExtensionLanguages(get(), get()) }

        addSingletonFactory<AudiobookUpdatesRepository> { AudiobookUpdatesRepositoryImpl(get()) }
        addFactory { GetAudiobookUpdates(get()) }

        addSingletonFactory<AnimeUpdatesRepository> { AnimeUpdatesRepositoryImpl(get()) }
        addFactory { GetAnimeUpdates(get()) }

        addSingletonFactory<MangaUpdatesRepository> { MangaUpdatesRepositoryImpl(get()) }
        addFactory { GetMangaUpdates(get()) }

        addSingletonFactory<AudiobookSourceRepository> { AudiobookSourceRepositoryImpl(get(), get()) }
        addSingletonFactory<AudiobookStubSourceRepository> { AudiobookStubSourceRepositoryImpl(get()) }
        addFactory { GetEnabledAudiobookSources(get(), get()) }
        addFactory { GetLanguagesWithAudiobookSources(get(), get()) }
        addFactory { GetRemoteAudiobook(get()) }
        addFactory { GetAudiobookSourcesWithFavoriteCount(get(), get()) }
        addFactory { GetAudiobookSourcesWithNonLibraryAudiobook(get()) }
        addFactory { ToggleAudiobookSource(get()) }
        addFactory { ToggleAudiobookSourcePin(get()) }

        addSingletonFactory<AnimeSourceRepository> { AnimeSourceRepositoryImpl(get(), get()) }
        addSingletonFactory<AnimeStubSourceRepository> { AnimeStubSourceRepositoryImpl(get()) }
        addFactory { GetEnabledAnimeSources(get(), get()) }
        addFactory { GetLanguagesWithAnimeSources(get(), get()) }
        addFactory { GetRemoteAnime(get()) }
        addFactory { GetAnimeSourcesWithFavoriteCount(get(), get()) }
        addFactory { GetAnimeSourcesWithNonLibraryAnime(get()) }
        addFactory { ToggleAnimeSource(get()) }
        addFactory { ToggleAnimeSourcePin(get()) }

        addSingletonFactory<MangaSourceRepository> { MangaSourceRepositoryImpl(get(), get()) }
        addSingletonFactory<MangaStubSourceRepository> { MangaStubSourceRepositoryImpl(get()) }
        addFactory { GetEnabledMangaSources(get(), get()) }
        addFactory { GetLanguagesWithMangaSources(get(), get()) }
        addFactory { GetRemoteManga(get()) }
        addFactory { GetMangaSourcesWithFavoriteCount(get(), get()) }
        addFactory { GetMangaSourcesWithNonLibraryManga(get()) }
        addFactory { SetMigrateSorting(get()) }
        addFactory { ToggleLanguage(get()) }
        addFactory { ToggleMangaSource(get()) }
        addFactory { ToggleMangaSourcePin(get()) }
    }
}
