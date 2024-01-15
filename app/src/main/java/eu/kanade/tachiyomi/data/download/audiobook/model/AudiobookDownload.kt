package eu.kanade.tachiyomi.data.download.audiobook.model

import eu.kanade.tachiyomi.audiobooksource.model.Audio
import eu.kanade.tachiyomi.audiobooksource.online.AudiobookHttpSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import rx.subjects.PublishSubject
import tachiyomi.domain.entries.audiobook.interactor.GetAudiobook
import tachiyomi.domain.entries.audiobook.model.Audiobook
import tachiyomi.domain.items.audiobookchapter.interactor.GetChapter
import tachiyomi.domain.items.audiobookchapter.model.Chapter
import tachiyomi.domain.source.audiobook.service.AudiobookSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

data class AudiobookDownload(
    val source: AudiobookHttpSource,
    val audiobook: Audiobook,
    val chapter: Chapter,
    val changeDownloader: Boolean = false,
    var audio: Audio? = null,
) {

    @Volatile
    @Transient
    var totalProgress: Int = 0
        set(progress) {
            field = progress
            progressSubject?.onNext(this)
            progressCallback?.invoke(this)
        }

    @Volatile
    @Transient
    var downloadedImages: Int = 0

    @Transient
    private val _statusFlow = MutableStateFlow(State.NOT_DOWNLOADED)

    @Transient
    val statusFlow = _statusFlow.asStateFlow()
    var status: State
        get() = _statusFlow.value
        set(status) {
            _statusFlow.value = status
        }

    @Transient
    val progressFlow = flow {
        if (audio == null) {
            emit(0)
            while (audio == null) {
                delay(50)
            }
        }

        val progressFlows = audio!!.progressFlow
        emitAll(combine(progressFlows) { it.average().toInt() })
    }
        .distinctUntilChanged()
        .debounce(50)

    @Transient
    var progressSubject: PublishSubject<AudiobookDownload>? = null

    @Transient
    var progressCallback: ((AudiobookDownload) -> Unit)? = null

    val progress: Int
        get() {
            val audio = audio ?: return 0
            return audio.progress
        }

    enum class State(val value: Int) {
        NOT_DOWNLOADED(0),
        QUEUE(1),
        DOWNLOADING(2),
        DOWNLOADED(3),
        ERROR(4),
    }

    companion object {
        suspend fun fromChapterId(
            chapterId: Long,
            getChapter: GetChapter = Injekt.get(),
            getAudiobookById: GetAudiobook = Injekt.get(),
            sourceManager: AudiobookSourceManager = Injekt.get(),
        ): AudiobookDownload? {
            val chapter = getChapter.await(chapterId) ?: return null
            val audiobook = getAudiobookById.await(chapter.audiobookId) ?: return null
            val source = sourceManager.get(audiobook.source) as? AudiobookHttpSource ?: return null

            return AudiobookDownload(source, audiobook, chapter)
        }
    }
}
