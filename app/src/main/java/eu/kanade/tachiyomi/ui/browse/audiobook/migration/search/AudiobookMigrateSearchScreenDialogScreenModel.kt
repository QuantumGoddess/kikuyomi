package eu.kanade.tachiyomi.ui.browse.audiobook.migration.search

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.domain.entries.audiobook.interactor.GetAudiobook
import tachiyomi.domain.entries.audiobook.model.Audiobook
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AudiobookMigrateSearchScreenDialogScreenModel(
    val audiobookId: Long,
    getAudiobook: GetAudiobook = Injekt.get(),
) : StateScreenModel<AudiobookMigrateSearchScreenDialogScreenModel.State>(State()) {

    init {
        screenModelScope.launch {
            val audiobook = getAudiobook.await(audiobookId)!!

            mutableState.update {
                it.copy(audiobook = audiobook)
            }
        }
    }

    fun setDialog(dialog: Dialog?) {
        mutableState.update {
            it.copy(dialog = dialog)
        }
    }

    @Immutable
    data class State(
        val audiobook: Audiobook? = null,
        val dialog: Dialog? = null,
    )

    sealed interface Dialog {
        data class Migrate(val audiobook: Audiobook) : Dialog
    }
}
