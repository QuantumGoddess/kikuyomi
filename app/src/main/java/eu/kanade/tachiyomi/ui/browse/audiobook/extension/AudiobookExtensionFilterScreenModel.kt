package eu.kanade.tachiyomi.ui.browse.audiobook.extension

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.extension.audiobook.interactor.GetAudiobookExtensionLanguages
import eu.kanade.domain.source.interactor.ToggleLanguage
import eu.kanade.domain.source.service.SourcePreferences
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AudiobookExtensionFilterScreenModel(
    private val preferences: SourcePreferences = Injekt.get(),
    private val getExtensionLanguages: GetAudiobookExtensionLanguages = Injekt.get(),
    private val toggleLanguage: ToggleLanguage = Injekt.get(),
) : StateScreenModel<AudiobookExtensionFilterState>(AudiobookExtensionFilterState.Loading) {

    private val _events: Channel<AudiobookExtensionFilterEvent> = Channel()
    val events: Flow<AudiobookExtensionFilterEvent> = _events.receiveAsFlow()

    init {
        screenModelScope.launch {
            combine(
                getExtensionLanguages.subscribe(),
                preferences.enabledLanguages().changes(),
            ) { a, b -> a to b }
                .catch { throwable ->
                    logcat(LogPriority.ERROR, throwable)
                    _events.send(AudiobookExtensionFilterEvent.FailedFetchingLanguages)
                }
                .collectLatest { (extensionLanguages, enabledLanguages) ->
                    mutableState.update {
                        AudiobookExtensionFilterState.Success(
                            languages = extensionLanguages.toImmutableList(),
                            enabledLanguages = enabledLanguages.toImmutableSet(),
                        )
                    }
                }
        }
    }

    fun toggle(language: String) {
        toggleLanguage.await(language)
    }
}

sealed interface AudiobookExtensionFilterEvent {
    data object FailedFetchingLanguages : AudiobookExtensionFilterEvent
}

sealed interface AudiobookExtensionFilterState {

    @Immutable
    data object Loading : AudiobookExtensionFilterState

    @Immutable
    data class Success(
        val languages: ImmutableList<String>,
        val enabledLanguages: ImmutableSet<String> = persistentSetOf(),
    ) : AudiobookExtensionFilterState {

        val isEmpty: Boolean
            get() = languages.isEmpty()
    }
}
