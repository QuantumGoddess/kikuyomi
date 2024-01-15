package eu.kanade.tachiyomi.ui.audioplayer

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.entries.audiobook.interactor.SetAudiobookViewerFlags
import eu.kanade.domain.items.audiobookchapter.model.toDbChapter
import eu.kanade.domain.track.audiobook.interactor.TrackChapter
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.tachiyomi.audiobooksource.AudiobookSource
import eu.kanade.tachiyomi.audiobooksource.model.Track
import eu.kanade.tachiyomi.audiobooksource.model.Audio
import eu.kanade.tachiyomi.audiobooksource.online.AudiobookHttpSource
import eu.kanade.tachiyomi.data.database.models.audiobook.Chapter
import eu.kanade.tachiyomi.data.database.models.audiobook.toDomainChapter
import eu.kanade.tachiyomi.data.download.audiobook.AudiobookDownloadManager
import eu.kanade.tachiyomi.data.download.audiobook.model.AudiobookDownload
import eu.kanade.tachiyomi.data.saver.Image
import eu.kanade.tachiyomi.data.saver.ImageSaver
import eu.kanade.tachiyomi.data.saver.Location
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.anilist.Anilist
import eu.kanade.tachiyomi.data.track.myanimelist.MyAnimeList
import eu.kanade.tachiyomi.network.NetworkPreferences
import eu.kanade.tachiyomi.ui.audioplayer.loader.ChapterLoader
import eu.kanade.tachiyomi.ui.audioplayer.settings.AudioplayerPreferences
import eu.kanade.tachiyomi.ui.audioplayer.viewer.SetAsCover
import eu.kanade.tachiyomi.ui.reader.SaveImageNotifier
import eu.kanade.tachiyomi.util.AniSkipApi
import eu.kanade.tachiyomi.util.Stamp
import eu.kanade.tachiyomi.util.editCover
import eu.kanade.tachiyomi.util.audiobookchapter.filterDownloadedChapters
import eu.kanade.tachiyomi.util.lang.byteSize
import eu.kanade.tachiyomi.util.lang.takeBytes
import eu.kanade.tachiyomi.util.storage.DiskUtil
import eu.kanade.tachiyomi.util.storage.cacheImageDir
import `is`.xyz.mpv.Utils
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import logcat.LogPriority
import tachiyomi.core.util.lang.launchIO
import tachiyomi.core.util.lang.launchNonCancellable
import tachiyomi.core.util.lang.withIOContext
import tachiyomi.core.util.system.logcat
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.entries.audiobook.interactor.GetAudiobook
import tachiyomi.domain.entries.audiobook.model.Audiobook
import tachiyomi.domain.history.audiobook.interactor.GetNextChapters
import tachiyomi.domain.history.audiobook.interactor.UpsertAudiobookHistory
import tachiyomi.domain.history.audiobook.model.AudiobookHistoryUpdate
import tachiyomi.domain.items.audiobookchapter.interactor.GetChaptersByAudiobookId
import tachiyomi.domain.items.audiobookchapter.interactor.UpdateChapter
import tachiyomi.domain.items.audiobookchapter.model.ChapterUpdate
import tachiyomi.domain.items.audiobookchapter.service.getChapterSort
import tachiyomi.domain.source.audiobook.service.AudiobookSourceManager
import tachiyomi.domain.track.audiobook.interactor.GetAudiobookTracks
import tachiyomi.source.local.entries.audiobook.isLocal
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.InputStream
import java.util.Date

class PlayerViewModel @JvmOverloads constructor(
    private val savedState: SavedStateHandle,
    private val sourceManager: AudiobookSourceManager = Injekt.get(),
    private val downloadManager: AudiobookDownloadManager = Injekt.get(),
    private val imageSaver: ImageSaver = Injekt.get(),
    private val downloadPreferences: DownloadPreferences = Injekt.get(),
    private val trackPreferences: TrackPreferences = Injekt.get(),
    private val trackChapter: TrackChapter = Injekt.get(),
    private val getAudiobook: GetAudiobook = Injekt.get(),
    private val getNextChapters: GetNextChapters = Injekt.get(),
    private val getChaptersByAudiobookId: GetChaptersByAudiobookId = Injekt.get(),
    private val getTracks: GetAudiobookTracks = Injekt.get(),
    private val upsertHistory: UpsertAudiobookHistory = Injekt.get(),
    private val updateChapter: UpdateChapter = Injekt.get(),
    private val setAudiobookViewerFlags: SetAudiobookViewerFlags = Injekt.get(),
    internal val networkPreferences: NetworkPreferences = Injekt.get(),
    internal val playerPreferences: AudioplayerPreferences = Injekt.get(),
    private val basePreferences: BasePreferences = Injekt.get(),
    uiPreferences: UiPreferences = Injekt.get(),
) : ViewModel() {

    val mutableState = MutableStateFlow(State())
    val state = mutableState.asStateFlow()

    private val eventChannel = Channel<Event>()
    val eventFlow = eventChannel.receiveAsFlow()

    private val incognitoMode = basePreferences.incognitoMode().get()
    private val downloadAheadAmount = downloadPreferences.autoDownloadWhileWatching().get()

    internal val relativeTime = uiPreferences.relativeTime().get()
    internal val dateFormat = UiPreferences.dateFormat(uiPreferences.dateFormat().get())

    /**
     * The chapter playlist loaded in the player. It can be empty when instantiated for a short time.
     */
    val currentPlaylist: List<Chapter>
        get() = filterChapterList(state.value.chapterList)

    /**
     * The chapter loaded in the player. It can be null when instantiated for a short time.
     */
    val currentChapter: Chapter?
        get() = state.value.chapter

    /**
     * The audiobook loaded in the player. It can be null when instantiated for a short time.
     */
    val currentAudiobook: Audiobook?
        get() = state.value.audiobook

    /**
     * The source used. It can be null when instantiated for a short time.
     */
    val currentSource: AudiobookSource?
        get() = state.value.source

    /**
     * The position in the current audio. Used to restore from process kill.
     */
    private var chapterPosition = savedState.get<Long>("chapter_position")
        set(value) {
            savedState["chapter_position"] = value
            field = value
        }

    /**
     * The current audio's quality index. Used to restore from process kill.
     */
    var qualityIndex = savedState.get<Int>("quality_index") ?: 0
        set(value) {
            savedState["quality_index"] = value
            field = value
        }

    /**
     * The chapter id of the currently loaded chapter. Used to restore from process kill.
     */
    private var chapterId = savedState.get<Long>("chapter_id") ?: -1L
        set(value) {
            savedState["chapter_id"] = value
            field = value
        }

    private var chapterToDownload: AudiobookDownload? = null

    private var currentAudioList: List<Audio>? = null

    private fun filterChapterList(chapters: List<Chapter>): List<Chapter> {
        val audiobook = currentAudiobook ?: return chapters
        val selectedChapter = chapters.find { it.id == chapterId }
            ?: error("Requested chapter of id $chapterId not found in chapter list")

        val chaptersForPlayer = chapters.filterNot {
            audiobook.unreadFilterRaw == Audiobook.CHAPTER_SHOW_READ && !it.read ||
                audiobook.unreadFilterRaw == Audiobook.CHAPTER_SHOW_UNREAD && it.read ||
                audiobook.downloadedFilterRaw == Audiobook.CHAPTER_SHOW_DOWNLOADED && !downloadManager.isChapterDownloaded(
                    it.name,
                    it.scanlator,
                    audiobook.title,
                    audiobook.source,
                ) ||
                audiobook.downloadedFilterRaw == Audiobook.CHAPTER_SHOW_NOT_DOWNLOADED && downloadManager.isChapterDownloaded(
                    it.name,
                    it.scanlator,
                    audiobook.title,
                    audiobook.source,
                ) ||
                audiobook.bookmarkedFilterRaw == Audiobook.CHAPTER_SHOW_BOOKMARKED && !it.bookmark ||
                audiobook.bookmarkedFilterRaw == Audiobook.CHAPTER_SHOW_NOT_BOOKMARKED && it.bookmark
        }.toMutableList()

        if (chaptersForPlayer.all { it.id != chapterId }) {
            chaptersForPlayer += listOf(selectedChapter)
        }

        return chaptersForPlayer
    }

    fun getCurrentChapterIndex(): Int {
        return this.currentPlaylist.indexOfFirst { currentChapter?.id == it.id }
    }

    fun getAdjacentChapterId(previous: Boolean): Long {
        val newIndex = if (previous) getCurrentChapterIndex() - 1 else getCurrentChapterIndex() + 1

        return when {
            previous && getCurrentChapterIndex() == 0 -> -1L
            !previous && this.currentPlaylist.lastIndex == getCurrentChapterIndex() -> -1L
            else -> this.currentPlaylist[newIndex].id ?: -1L
        }
    }

    override fun onCleared() {
        if (currentChapter != null) {
            saveWatchingProgress(currentChapter!!)
            chapterToDownload?.let {
                downloadManager.addDownloadsToStartOfQueue(listOf(it))
            }
        }
    }

    /**
     * Called when the activity is saved and not changing configurations. It updates the database
     * to persist the current progress of the active chapter.
     */
    fun onSaveInstanceStateNonConfigurationChange() {
        val currentChapter = currentChapter ?: return
        viewModelScope.launchNonCancellable {
            saveChapterProgress(currentChapter)
        }
    }

    /**
     * Whether this presenter is initialized yet.
     */
    private fun needsInit(): Boolean {
        return currentAudiobook == null || currentChapter == null
    }

    /**
     * Initializes this presenter with the given [audiobookId] and [initialChapterId]. This method will
     * fetch the audiobook from the database and initialize the chapter.
     */
    suspend fun init(audiobookId: Long, initialChapterId: Long): Pair<InitResult, Result<Boolean>> {
        val defaultResult = InitResult(currentAudioList, 0, null)
        if (!needsInit()) return Pair(defaultResult, Result.success(true))
        return try {
            val audiobook = getAudiobook.await(audiobookId)
            if (audiobook != null) {
                if (chapterId == -1L) chapterId = initialChapterId

                checkTrackers(audiobook)

                mutableState.update { it.copy(chapterList = initChapterList(audiobook)) }
                val chapter = this.currentPlaylist.first { it.id == chapterId }

                val source = sourceManager.getOrStub(audiobook.source)

                mutableState.update { it.copy(chapter = chapter, audiobook = audiobook, source = source) }

                val currentEp = currentChapter ?: throw Exception("No chapter loaded.")

                ChapterLoader.getLinks(currentEp.toDomainChapter()!!, audiobook, source)
                    .takeIf { it.isNotEmpty() }
                    ?.also { currentAudioList = it }
                    ?: run {
                        currentAudioList = null
                        throw Exception("Audio list is empty.")
                    }

                val result = InitResult(
                    audioList = currentAudioList,
                    audioIndex = qualityIndex,
                    position = chapterPosition,
                )
                Pair(result, Result.success(true))
            } else {
                // Unlikely but okay
                Pair(defaultResult, Result.success(false))
            }
        } catch (e: Throwable) {
            Pair(defaultResult, Result.failure(e))
        }
    }

    data class InitResult(
        val audioList: List<Audio>?,
        val audioIndex: Int,
        val position: Long?,
    )

    private fun initChapterList(audiobook: Audiobook): List<Chapter> {
        val chapters = runBlocking { getChaptersByAudiobookId.await(audiobook.id) }

        return chapters
            .sortedWith(getChapterSort(audiobook, sortDescending = false))
            .run {
                if (basePreferences.downloadedOnly().get()) {
                    filterDownloadedChapters(audiobook)
                } else {
                    this
                }
            }
            .map { it.toDbChapter() }
    }

    private var hasTrackers: Boolean = false
    private val checkTrackers: (Audiobook) -> Unit = { audiobook ->
        val tracks = runBlocking { getTracks.await(audiobook.id) }
        hasTrackers = tracks.isNotEmpty()
    }

    fun isChapterOnline(): Boolean? {
        val audiobook = currentAudiobook ?: return null
        val chapter = currentChapter ?: return null
        return currentSource is AudiobookHttpSource && !ChapterLoader.isDownload(
            chapter.toDomainChapter()!!,
            audiobook,
        )
    }

    suspend fun loadChapter(chapterId: Long?): Pair<List<Audio>?, String>? {
        val audiobook = currentAudiobook ?: return null
        val source = sourceManager.getOrStub(audiobook.source)

        val chosenChapter = this.currentPlaylist.firstOrNull { ep -> ep.id == chapterId } ?: return null

        mutableState.update { it.copy(chapter = chosenChapter) }

        return withIOContext {
            try {
                val currentChapter = currentChapter ?: throw Exception("No chapter loaded.")
                currentAudioList = ChapterLoader.getLinks(
                    currentChapter.toDomainChapter()!!,
                    audiobook,
                    source,
                )
                this@PlayerViewModel.chapterId = currentChapter.id!!
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { e.message ?: "Error getting links" }
            }

            Pair(currentAudioList, audiobook.title + " - " + chosenChapter.name)
        }
    }

    /**
     * Called every time a second is reached in the player. Used to mark the flag of chapter being
     * read, update tracking services, enqueue downloaded chapter deletion and download next chapter.
     */
    fun onSecondReached(position: Int, duration: Int) {
        if (state.value.isLoadingChapter) return
        val currentEp = currentChapter ?: return
        if (chapterId == -1L) return

        val seconds = position * 1000L
        val totalSeconds = duration * 1000L
        // Save last second read and mark as read if needed
        currentEp.last_second_read = seconds
        currentEp.total_seconds = totalSeconds

        chapterPosition = seconds

        val progress = playerPreferences.progressPreference().get()
        val shouldTrack = !incognitoMode || hasTrackers
        if (seconds >= totalSeconds * progress && shouldTrack) {
            currentEp.read = true
            updateTrackChapterRead(currentEp)
            deleteChapterIfNeeded(currentEp)
        }

        saveWatchingProgress(currentEp)

        val inDownloadRange = seconds.toDouble() / totalSeconds > 0.35
        if (inDownloadRange) {
            downloadNextChapters()
        }
    }

    private fun downloadNextChapters() {
        if (downloadAheadAmount == 0) return
        val audiobook = currentAudiobook ?: return

        // Only download ahead if current + next chapter is already downloaded too to avoid jank
        if (getCurrentChapterIndex() == this.currentPlaylist.lastIndex) return
        val currentChapter = currentChapter ?: return

        val nextChapter = this.currentPlaylist[getCurrentChapterIndex() + 1]
        val chaptersAreDownloaded =
            ChapterLoader.isDownload(currentChapter.toDomainChapter()!!, audiobook) &&
                ChapterLoader.isDownload(nextChapter.toDomainChapter()!!, audiobook)

        viewModelScope.launchIO {
            if (!chaptersAreDownloaded) {
                return@launchIO
            }
            val chaptersToDownload = getNextChapters.await(audiobook.id, nextChapter.id!!)
                .take(downloadAheadAmount)
            downloadManager.downloadChapters(audiobook, chaptersToDownload)
        }
    }

    /**
     * Determines if deleting option is enabled and nth to last chapter actually exists.
     * If both conditions are satisfied enqueues chapter for delete
     * @param chosenChapter current chapter, which is going to be marked as read.
     */
    private fun deleteChapterIfNeeded(chosenChapter: Chapter) {
        // Determine which chapter should be deleted and enqueue
        val currentChapterPosition = this.currentPlaylist.indexOf(chosenChapter)
        val removeAfterReadSlots = downloadPreferences.removeAfterReadSlots().get()
        val chapterToDelete = this.currentPlaylist.getOrNull(
            currentChapterPosition - removeAfterReadSlots,
        )
        // If chapter is completely read no need to download it
        chapterToDownload = null

        // Check if deleting option is enabled and chapter exists
        if (removeAfterReadSlots != -1 && chapterToDelete != null) {
            enqueueDeleteReadChapters(chapterToDelete)
        }
    }

    fun saveCurrentChapterWatchingProgress() {
        currentChapter?.let { saveWatchingProgress(it) }
    }

    /**
     * Called when chapter is changed in player or when activity is paused.
     */
    private fun saveWatchingProgress(chapter: Chapter) {
        viewModelScope.launchNonCancellable {
            saveChapterProgress(chapter)
            saveChapterHistory(chapter)
        }
    }

    /**
     * Saves this [chapter] progress (last second read and whether it's read).
     * If incognito mode isn't on or has at least 1 tracker
     */
    private suspend fun saveChapterProgress(chapter: Chapter) {
        if (!incognitoMode || hasTrackers) {
            updateChapter.await(
                ChapterUpdate(
                    id = chapter.id!!,
                    read = chapter.read,
                    bookmark = chapter.bookmark,
                    lastSecondRead = chapter.last_second_read,
                    totalSeconds = chapter.total_seconds,
                ),
            )
        }
    }

    /**
     * Saves this [chapter] last read history if incognito mode isn't on.
     */
    private suspend fun saveChapterHistory(chapter: Chapter) {
        if (!incognitoMode) {
            val chapterId = chapter.id!!
            val readAt = Date()
            upsertHistory.await(
                AudiobookHistoryUpdate(chapterId, readAt),
            )
        }
    }

    /**
     * Bookmarks the currently active chapter.
     */
    fun bookmarkChapter(chapterId: Long?, bookmarked: Boolean) {
        viewModelScope.launchNonCancellable {
            updateChapter.await(
                ChapterUpdate(
                    id = chapterId!!,
                    bookmark = bookmarked,
                ),
            )
        }
    }

    /**
     * Saves the screenshot on the pictures directory and notifies the UI of the result.
     * There's also a notification to allow sharing the image somewhere else or deleting it.
     */
    fun saveImage(imageStream: () -> InputStream, timePos: Int?) {
        val audiobook = currentAudiobook ?: return

        val context = Injekt.get<Application>()
        val notifier = SaveImageNotifier(context)
        notifier.onClear()

        val seconds = timePos?.let { Utils.prettyTime(it) } ?: return
        val filename = generateFilename(audiobook, seconds) ?: return

        // Pictures directory.
        val relativePath = DiskUtil.buildValidFilename(audiobook.title)

        // Copy file in background.
        viewModelScope.launchNonCancellable {
            try {
                val uri = imageSaver.save(
                    image = Image.Page(
                        inputStream = imageStream,
                        name = filename,
                        location = Location.Pictures(relativePath),
                    ),
                )
                notifier.onComplete(uri)
                eventChannel.send(Event.SavedImage(SaveImageResult.Success(uri)))
            } catch (e: Throwable) {
                notifier.onError(e.message)
                eventChannel.send(Event.SavedImage(SaveImageResult.Error(e)))
            }
        }
    }

    /**
     * Shares the screenshot and notifies the UI with the path of the file to share.
     * The image must be first copied to the internal partition because there are many possible
     * formats it can come from, like a zipped chapter, in which case it's not possible to directly
     * get a path to the file and it has to be decompressed somewhere first. Only the last shared
     * image will be kept so it won't be taking lots of internal disk space.
     */
    fun shareImage(imageStream: () -> InputStream, timePos: Int?) {
        val audiobook = currentAudiobook ?: return

        val context = Injekt.get<Application>()
        val destDir = context.cacheImageDir

        val seconds = timePos?.let { Utils.prettyTime(it) } ?: return
        val filename = generateFilename(audiobook, seconds) ?: return

        try {
            viewModelScope.launchIO {
                destDir.deleteRecursively()
                val uri = imageSaver.save(
                    image = Image.Page(
                        inputStream = imageStream,
                        name = filename,
                        location = Location.Cache,
                    ),
                )
                eventChannel.send(Event.ShareImage(uri, seconds))
            }
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e)
        }
    }

    /**
     * Sets the screenshot as cover and notifies the UI of the result.
     */
    fun setAsCover(imageStream: () -> InputStream) {
        val audiobook = currentAudiobook ?: return

        viewModelScope.launchNonCancellable {
            val result = try {
                audiobook.editCover(Injekt.get(), imageStream())
                if (audiobook.isLocal() || audiobook.favorite) {
                    SetAsCover.Success
                } else {
                    SetAsCover.AddToLibraryFirst
                }
            } catch (e: Exception) {
                SetAsCover.Error
            }
            eventChannel.send(Event.SetCoverResult(result))
        }
    }

    /**
     * Results of the save image feature.
     */
    sealed class SaveImageResult {
        class Success(val uri: Uri) : SaveImageResult()
        class Error(val error: Throwable) : SaveImageResult()
    }

    private fun updateTrackChapterRead(chapter: Chapter) {
        if (basePreferences.incognitoMode().get() || !hasTrackers) return
        if (!trackPreferences.autoUpdateTrack().get()) return

        val audiobook = currentAudiobook ?: return
        val context = Injekt.get<Application>()

        viewModelScope.launchNonCancellable {
            trackChapter.await(context, audiobook.id, chapter.chapter_number.toDouble())
        }
    }

    /**
     * Enqueues this [chapter] to be deleted when [deletePendingChapters] is called. The download
     * manager handles persisting it across process deaths.
     */
    private fun enqueueDeleteReadChapters(chapter: Chapter) {
        if (!chapter.read) return
        val audiobook = currentAudiobook ?: return
        viewModelScope.launchNonCancellable {
            downloadManager.enqueueChaptersToDelete(listOf(chapter.toDomainChapter()!!), audiobook)
        }
    }

    /**
     * Deletes all the pending chapters. This operation will run in a background thread and errors
     * are ignored.
     */
    fun deletePendingChapters() {
        viewModelScope.launchNonCancellable {
            downloadManager.deletePendingChapters()
        }
    }

    /**
     * Returns the skipIntroLength used by this audiobook or the default one.
     */
    fun getAudiobookSkipIntroLength(resolveDefault: Boolean = true): Int {
        val default = playerPreferences.defaultIntroLength().get()
        val audiobook = currentAudiobook ?: return default
        val skipIntroLength = audiobook.skipIntroLength
        return when {
            resolveDefault && skipIntroLength <= 0 -> default
            else -> audiobook.skipIntroLength
        }
    }

    /**
     * Updates the skipIntroLength for the open audiobook.
     */
    fun setAudiobookSkipIntroLength(skipIntroLength: Long) {
        val audiobook = currentAudiobook ?: return
        viewModelScope.launchIO {
            setAudiobookViewerFlags.awaitSetSkipIntroLength(audiobook.id, skipIntroLength)
            logcat(LogPriority.INFO) { "New Skip Intro Length is ${audiobook.skipIntroLength}" }
            mutableState.update {
                it.copy(
                    audiobook = getAudiobook.await(audiobook.id),
                )
            }
            eventChannel.send(Event.SetAudiobookSkipIntro(getAudiobookSkipIntroLength()))
        }
    }

    /**
     * Generate a filename for the given [audiobook] and [timePos]
     */
    private fun generateFilename(
        audiobook: Audiobook,
        timePos: String,
    ): String? {
        val chapter = currentChapter ?: return null
        val filenameSuffix = " - $timePos"
        return DiskUtil.buildValidFilename(
            "${audiobook.title} - ${chapter.name}".takeBytes(
                DiskUtil.MAX_FILE_NAME_BYTES - filenameSuffix.byteSize(),
            ),
        ) + filenameSuffix
    }

    /**
     * Returns the response of the AniSkipApi for this chapter.
     * just works if tracking is enabled.
     */
    suspend fun aniSkipResponse(playerDuration: Int?): List<Stamp>? {
        val audiobookId = currentAudiobook?.id ?: return null
        val trackerManager = Injekt.get<TrackerManager>()
        var malId: Long?
        val chapterNumber = currentChapter?.chapter_number?.toInt() ?: return null
        if (getTracks.await(audiobookId).isEmpty()) {
            logcat { "AniSkip: No tracks found for audiobook $audiobookId" }
            return null
        }

        getTracks.await(audiobookId).map { track ->
            val tracker = trackerManager.get(track.syncId)
            malId = when (tracker) {
                is MyAnimeList -> track.remoteId
                is Anilist -> AniSkipApi().getMalIdFromAL(track.remoteId)
                else -> null
            }
            val duration = playerDuration ?: return null
            return malId?.let {
                AniSkipApi().getResult(it.toInt(), chapterNumber, duration.toLong())
            }
        }
        return null
    }

    fun showChapterList() {
        mutableState.update { it.copy(dialog = Dialog.ChapterList) }
    }

    fun showSpeedPicker() {
        mutableState.update { it.copy(dialog = Dialog.SpeedPicker) }
    }

    fun showPlayerSettings() {
        mutableState.update { it.copy(sheet = Sheet.PlayerSettings) }
    }

    fun showAudioChapters() {
        mutableState.update { it.copy(sheet = Sheet.AudioChapters) }
    }

    fun showStreamsCatalog() {
        mutableState.update { it.copy(sheet = Sheet.StreamsCatalog) }
    }

    fun closeDialogSheet() {
        mutableState.update { it.copy(dialog = null, sheet = null) }
    }

    @Immutable
    data class State(
        val chapterList: List<Chapter> = emptyList(),
        val chapter: Chapter? = null,
        val audiobook: Audiobook? = null,
        val source: AudiobookSource? = null,
        val audioStreams: AudioStreams = AudioStreams(),
        val isLoadingChapter: Boolean = false,
        val dialog: Dialog? = null,
        val sheet: Sheet? = null,
    )

    class AudioStreams(val quality: Stream, val subtitle: Stream, val audio: Stream) {
        constructor() : this(Stream(), Stream(), Stream())
        class Stream(var index: Int = 0, var tracks: Array<Track> = emptyArray())
    }

    sealed class Dialog {
        object ChapterList : Dialog()
        object SpeedPicker : Dialog()
    }

    sealed class Sheet {
        object PlayerSettings : Sheet()
        object AudioChapters : Sheet()
        object StreamsCatalog : Sheet()
    }

    sealed class Event {
        data class SetAudiobookSkipIntro(val duration: Int) : Event()
        data class SetCoverResult(val result: SetAsCover) : Event()
        data class SavedImage(val result: SaveImageResult) : Event()
        data class ShareImage(val uri: Uri, val seconds: String) : Event()
    }
}
