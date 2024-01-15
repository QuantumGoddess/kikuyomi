package eu.kanade.tachiyomi.ui.browse.audiobook.migration.audiobook

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.audiobooksource.AudiobookSource
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.util.system.logcat
import tachiyomi.domain.entries.audiobook.interactor.GetAudiobookFavorites
import tachiyomi.domain.entries.audiobook.model.Audiobook
import tachiyomi.domain.source.audiobook.service.AudiobookSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MigrateAudiobookScreenModel(
    private val sourceId: Long,
    private val sourceManager: AudiobookSourceManager = Injekt.get(),
    private val getFavorites: GetAudiobookFavorites = Injekt.get(),
) : StateScreenModel<MigrateAudiobookScreenModel.State>(State()) {

    private val _events: Channel<MigrationAudiobookEvent> = Channel()
    val events: Flow<MigrationAudiobookEvent> = _events.receiveAsFlow()

    init {
        screenModelScope.launch {
            mutableState.update { state ->
                state.copy(source = sourceManager.getOrStub(sourceId))
            }

            getFavorites.subscribe(sourceId)
                .catch {
                    logcat(LogPriority.ERROR, it)
                    _events.send(MigrationAudiobookEvent.FailedFetchingFavorites)
                    mutableState.update { state ->
                        state.copy(titleList = persistentListOf())
                    }
                }
                .map { audiobook ->
                    audiobook
                        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
                        .toImmutableList()
                }
                .collectLatest { list ->
                    mutableState.update { it.copy(titleList = list) }
                }
        }
    }

    @Immutable
    data class State(
        val source: AudiobookSource? = null,
        private val titleList: ImmutableList<Audiobook>? = null,
    ) {

        val titles: ImmutableList<Audiobook>
            get() = titleList ?: persistentListOf()

        val isLoading: Boolean
            get() = source == null || titleList == null

        val isEmpty: Boolean
            get() = titles.isEmpty()
    }
}

sealed interface MigrationAudiobookEvent {
    data object FailedFetchingFavorites : MigrationAudiobookEvent
}
