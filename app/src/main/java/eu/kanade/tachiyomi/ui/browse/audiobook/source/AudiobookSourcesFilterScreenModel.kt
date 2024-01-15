package eu.kanade.tachiyomi.ui.browse.audiobook.source

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.source.audiobook.interactor.GetLanguagesWithAudiobookSources
import eu.kanade.domain.source.audiobook.interactor.ToggleAudiobookSource
import eu.kanade.domain.source.interactor.ToggleLanguage
import eu.kanade.domain.source.service.SourcePreferences
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.domain.source.audiobook.model.AudiobookSource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.SortedMap

class AudiobookSourcesFilterScreenModel(
    private val preferences: SourcePreferences = Injekt.get(),
    private val getLanguagesWithSources: GetLanguagesWithAudiobookSources = Injekt.get(),
    private val toggleSource: ToggleAudiobookSource = Injekt.get(),
    private val toggleLanguage: ToggleLanguage = Injekt.get(),
) : StateScreenModel<AudiobookSourcesFilterScreenModel.State>(State.Loading) {

    init {
        screenModelScope.launch {
            combine(
                getLanguagesWithSources.subscribe(),
                preferences.enabledLanguages().changes(),
                preferences.disabledAudiobookSources().changes(),
            ) { a, b, c -> Triple(a, b, c) }
                .catch { throwable ->
                    mutableState.update {
                        State.Error(
                            throwable = throwable,
                        )
                    }
                }
                .collectLatest { (languagesWithSources, enabledLanguages, disabledSources) ->
                    mutableState.update {
                        State.Success(
                            items = languagesWithSources,
                            enabledLanguages = enabledLanguages,
                            disabledSources = disabledSources,
                        )
                    }
                }
        }
    }

    fun toggleSource(source: AudiobookSource) {
        toggleSource.await(source)
    }

    fun toggleLanguage(language: String) {
        toggleLanguage.await(language)
    }

    sealed interface State {

        @Immutable
        data object Loading : State

        @Immutable
        data class Error(
            val throwable: Throwable,
        ) : State

        @Immutable
        data class Success(
            val items: SortedMap<String, List<AudiobookSource>>,
            val enabledLanguages: Set<String>,
            val disabledSources: Set<String>,
        ) : State {

            val isEmpty: Boolean
                get() = items.isEmpty()
        }
    }
}
