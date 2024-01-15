package eu.kanade.tachiyomi.ui.deeplink.audiobook

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.entries.audiobook.model.toDomainAudiobook
import eu.kanade.domain.entries.audiobook.model.toSAudiobook
import eu.kanade.domain.items.audiobookchapter.interactor.SyncChaptersWithSource
import eu.kanade.tachiyomi.audiobooksource.AudiobookSource
import eu.kanade.tachiyomi.audiobooksource.model.SAudiobook
import eu.kanade.tachiyomi.audiobooksource.model.SChapter
import eu.kanade.tachiyomi.audiobooksource.online.ResolvableAudiobookSource
import eu.kanade.tachiyomi.audiobooksource.online.UriType
import kotlinx.coroutines.flow.update
import tachiyomi.core.util.lang.launchIO
import tachiyomi.domain.entries.audiobook.interactor.NetworkToLocalAudiobook
import tachiyomi.domain.entries.audiobook.model.Audiobook
import tachiyomi.domain.entries.audiobook.interactor.GetAudiobookByUrlAndSourceId
import tachiyomi.domain.items.audiobookchapter.interactor.GetChapterByUrlAndAudiobookId
import tachiyomi.domain.items.audiobookchapter.model.Chapter
import tachiyomi.domain.source.audiobook.service.AudiobookSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class DeepLinkAudiobookScreenModel(
    query: String = "",
    private val sourceManager: AudiobookSourceManager = Injekt.get(),
    private val networkToLocalAudiobook: NetworkToLocalAudiobook = Injekt.get(),
    private val getChapterByUrlAndAudiobookId: GetChapterByUrlAndAudiobookId = Injekt.get(),
    private val getAudiobookByUrlAndSourceId: GetAudiobookByUrlAndSourceId = Injekt.get(),
    private val syncChaptersWithSource: SyncChaptersWithSource = Injekt.get(),
) : StateScreenModel<DeepLinkAudiobookScreenModel.State>(State.Loading) {

    init {
        screenModelScope.launchIO {
            val source = sourceManager.getCatalogueSources()
                .filterIsInstance<ResolvableAudiobookSource>()
                .firstOrNull { it.getUriType(query) != UriType.Unknown }

            val audiobook = source?.getAudiobook(query)?.let {
                getAudiobookFromSAudiobook(it, source.id)
            }

            val chapter = if (source?.getUriType(query) == UriType.Chapter && audiobook != null) {
                source.getChapter(query)?.let { getChapterFromSChapter(it, audiobook, source) }
            } else {
                null
            }

            mutableState.update {
                if (audiobook == null) {
                    State.NoResults
                } else {
                    if (chapter == null) {
                        State.Result(audiobook)
                    } else {
                        State.Result(audiobook, chapter.id)
                    }
                }
            }
        }
    }

    private suspend fun getChapterFromSChapter(sChapter: SChapter, audiobook: Audiobook, source: AudiobookSource): Chapter? {
        val localChapter = getChapterByUrlAndAudiobookId.await(sChapter.url, audiobook.id)

        return if (localChapter == null) {
            val sourceChapters = source.getChapterList(audiobook.toSAudiobook())
            val newChapters = syncChaptersWithSource.await(sourceChapters, audiobook, source, false)
            newChapters.find { it.url == sChapter.url }
        } else {
            localChapter
        }
    }

    private suspend fun getAudiobookFromSAudiobook(sAudiobook: SAudiobook, sourceId: Long): Audiobook {
        return getAudiobookByUrlAndSourceId.awaitAudiobook(sAudiobook.url, sourceId)
            ?: networkToLocalAudiobook.await(sAudiobook.toDomainAudiobook(sourceId))
    }

    sealed interface State {
        @Immutable
        data object Loading : State

        @Immutable
        data object NoResults : State

        @Immutable
        data class Result(val audiobook: Audiobook, val chapterId: Long? = null) : State
    }
}
