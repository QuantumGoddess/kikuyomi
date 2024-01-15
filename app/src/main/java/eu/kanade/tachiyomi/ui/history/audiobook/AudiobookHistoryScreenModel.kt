package eu.kanade.tachiyomi.ui.history.audiobook

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.core.util.insertSeparators
import eu.kanade.presentation.history.audiobook.AudiobookHistoryUiModel
import eu.kanade.tachiyomi.util.lang.toDateKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.util.lang.launchIO
import tachiyomi.core.util.lang.withIOContext
import tachiyomi.core.util.system.logcat
import tachiyomi.domain.history.audiobook.interactor.GetAudiobookHistory
import tachiyomi.domain.history.audiobook.interactor.GetNextChapters
import tachiyomi.domain.history.audiobook.interactor.RemoveAudiobookHistory
import tachiyomi.domain.history.audiobook.model.AudiobookHistoryWithRelations
import tachiyomi.domain.items.audiobookchapter.model.Chapter
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Date

class AudiobookHistoryScreenModel(
    private val getHistory: GetAudiobookHistory = Injekt.get(),
    private val getNextChapters: GetNextChapters = Injekt.get(),
    private val removeHistory: RemoveAudiobookHistory = Injekt.get(),
) : StateScreenModel<AudiobookHistoryScreenModel.State>(State()) {

    private val _events: Channel<Event> = Channel(Channel.UNLIMITED)
    val events: Flow<Event> = _events.receiveAsFlow()

    private val _query: MutableStateFlow<String?> = MutableStateFlow(null)
    val query: StateFlow<String?> = _query.asStateFlow()

    init {
        screenModelScope.launch {
            _query.collectLatest { query ->
                getHistory.subscribe(query ?: "")
                    .distinctUntilChanged()
                    .catch { error ->
                        logcat(LogPriority.ERROR, error)
                        _events.send(Event.InternalError)
                    }
                    .map { it.toAudiobookHistoryUiModels() }
                    .flowOn(Dispatchers.IO)
                    .collect { newList -> mutableState.update { it.copy(list = newList) } }
            }
        }
    }

    fun search(query: String?) {
        screenModelScope.launchIO {
            _query.emit(query)
        }
    }

    private fun List<AudiobookHistoryWithRelations>.toAudiobookHistoryUiModels(): List<AudiobookHistoryUiModel> {
        return map { AudiobookHistoryUiModel.Item(it) }
            .insertSeparators { before, after ->
                val beforeDate = before?.item?.readAt?.time?.toDateKey() ?: Date(0)
                val afterDate = after?.item?.readAt?.time?.toDateKey() ?: Date(0)
                when {
                    beforeDate.time != afterDate.time && afterDate.time != 0L -> AudiobookHistoryUiModel.Header(
                        afterDate,
                    )
                    // Return null to avoid adding a separator between two items.
                    else -> null
                }
            }
    }

    suspend fun getNextChapter(): Chapter? {
        return withIOContext { getNextChapters.await(onlyUnread = false).firstOrNull() }
    }

    fun getNextChapterForAudiobook(audiobookId: Long, chapterId: Long) {
        screenModelScope.launchIO {
            sendNextChapterEvent(getNextChapters.await(audiobookId, chapterId, onlyUnread = false))
        }
    }

    private suspend fun sendNextChapterEvent(chapters: List<Chapter>) {
        val chapter = chapters.firstOrNull()
        _events.send(Event.OpenChapter(chapter))
    }

    fun removeFromHistory(history: AudiobookHistoryWithRelations) {
        screenModelScope.launchIO {
            removeHistory.await(history)
        }
    }

    fun removeAllFromHistory(audiobookId: Long) {
        screenModelScope.launchIO {
            removeHistory.await(audiobookId)
        }
    }

    fun removeAllHistory() {
        screenModelScope.launchIO {
            val result = removeHistory.awaitAll()
            if (!result) return@launchIO
            _events.send(Event.HistoryCleared)
        }
    }

    fun setDialog(dialog: Dialog?) {
        mutableState.update { it.copy(dialog = dialog) }
    }

    @Immutable
    data class State(
        val searchQuery: String? = null,
        val list: List<AudiobookHistoryUiModel>? = null,
        val dialog: Dialog? = null,
    )

    sealed interface Dialog {
        data object DeleteAll : Dialog
        data class Delete(val history: AudiobookHistoryWithRelations) : Dialog
    }

    sealed interface Event {
        data class OpenChapter(val chapter: Chapter?) : Event
        data object InternalError : Event
        data object HistoryCleared : Event
    }
}
