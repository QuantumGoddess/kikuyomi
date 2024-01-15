package eu.kanade.tachiyomi.ui.audioplayer

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.track.audiobook.model.toDbTrack
import eu.kanade.domain.track.audiobook.service.DelayedAudiobookTrackingUpdateJob
import eu.kanade.domain.track.audiobook.store.DelayedAudiobookTrackingStore
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.tachiyomi.audiobooksource.AudiobookSource
import eu.kanade.tachiyomi.audiobooksource.online.AudiobookHttpSource
import eu.kanade.tachiyomi.audiobooksource.model.Audio
import eu.kanade.tachiyomi.data.download.audiobook.AudiobookDownloadManager
import eu.kanade.tachiyomi.data.track.AudiobookTracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.ui.audioplayer.loader.ChapterLoader
import eu.kanade.tachiyomi.ui.audioplayer.settings.AudioplayerPreferences
import eu.kanade.tachiyomi.util.system.LocaleHelper
import eu.kanade.tachiyomi.util.system.isOnline
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import logcat.LogPriority
import tachiyomi.core.util.lang.launchIO
import tachiyomi.core.util.lang.withIOContext
import tachiyomi.core.util.lang.withUIContext
import tachiyomi.core.util.system.logcat
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.entries.audiobook.interactor.GetAudiobook
import tachiyomi.domain.entries.audiobook.model.Audiobook
import tachiyomi.domain.history.audiobook.interactor.UpsertAudiobookHistory
import tachiyomi.domain.history.audiobook.model.AudiobookHistoryUpdate
import tachiyomi.domain.items.audiobookchapter.interactor.GetChaptersByAudiobookId
import tachiyomi.domain.items.audiobookchapter.interactor.UpdateChapter
import tachiyomi.domain.items.audiobookchapter.model.Chapter
import tachiyomi.domain.items.audiobookchapter.model.ChapterUpdate
import tachiyomi.domain.source.audiobook.service.AudiobookSourceManager
import tachiyomi.domain.track.audiobook.interactor.GetAudiobookTracks
import tachiyomi.domain.track.audiobook.interactor.InsertAudiobookTrack
import tachiyomi.source.local.entries.audiobook.LocalAudiobookSource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.util.Date

class ExternalIntents {

    /**
     * The common variables
     * Used to dictate what audio is sent an external player.
     */
    lateinit var audiobook: Audiobook
    lateinit var source: AudiobookSource
    lateinit var chapter: Chapter

    /**
     * Returns the [Intent] to be sent to an external player.
     *
     * @param context the application context.
     * @param audiobookId the id of the audiobook.
     * @param chapterId the id of the chapter.
     */
    suspend fun getExternalIntent(
        context: Context,
        audiobookId: Long?,
        chapterId: Long?,
        chosenAudio: Audio?,
    ): Intent? {
        audiobook = getAudiobook.await(audiobookId!!) ?: return null
        source = sourceManager.get(audiobook.source) ?: return null
        chapter = getChaptersByAudiobookId.await(audiobook.id).find { it.id == chapterId } ?: return null

        val audio = chosenAudio
            ?: ChapterLoader.getLinks(chapter, audiobook, source).firstOrNull()
            ?: throw Exception("Audio list is empty")

        val audioUrl = getAudioUrl(context, audio) ?: return null

        val pkgName = playerPreferences.externalAudioplayerPreference().get()

        return if (pkgName.isEmpty()) {
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndTypeAndNormalize(audioUrl, getMime(audioUrl))
                addExtrasAndFlags(false, this)
                addAudioHeaders(false, audio, this)
            }
        } else {
            getIntentForPackage(pkgName, context, audioUrl, audio)
        }
    }

    /**
     * Returns the [Uri] of the given audio.
     *
     * @param context the application context.
     * @param audio the audio being sent to the external player.
     */
    private suspend fun getAudioUrl(context: Context, audio: Audio): Uri? {
        if (audio.audioUrl == null) {
            makeErrorToast(context, Exception("Audio URL is null."))
            return null
        } else {
            val uri = audio.audioUrl!!.toUri()

            val isOnDevice = if (audiobook.source == LocalAudiobookSource.ID) {
                true
            } else {
                downloadManager.isChapterDownloaded(
                    chapterName = chapter.name,
                    chapterScanlator = chapter.scanlator,
                    audiobookTitle = audiobook.title,
                    sourceId = audiobook.source,
                    skipCache = true,
                )
            }

            return if (isOnDevice && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && uri.scheme != "content") {
                FileProvider.getUriForFile(
                    context,
                    context.applicationContext.packageName + ".provider",
                    File(uri.path!!),
                )
            } else {
                uri
            }
        }
    }

    /**
     * Returns the second to start the external player at.
     */
    private fun getLastSecondRead(): Long {
        val preserveWatchPos = playerPreferences.preserveWatchingPosition().get()
        val isChapterWatched = chapter.lastSecondRead == chapter.totalSeconds

        return if (chapter.read && (!preserveWatchPos || (preserveWatchPos && isChapterWatched))) {
            1L
        } else {
            chapter.lastSecondRead
        }
    }

    /**
     * Display an error toast in this [context].
     *
     * @param context the application context.
     * @param e the exception error to be displayed.
     */
    private suspend fun makeErrorToast(context: Context, e: Exception?) {
        withUIContext { context.toast(e?.message ?: "Cannot open chapter") }
    }

    /**
     * Returns the [Intent] with added data to send to the given external player.
     *
     * @param pkgName the name of the package to send the [Intent] to.
     * @param context the application context.
     * @param uri the path data of the audio.
     * @param audio the audio being sent to the external player.
     */
    private fun getIntentForPackage(pkgName: String, context: Context, uri: Uri, audio: Audio): Intent {
        return when (pkgName) {
            WEB_VIDEO_CASTER -> webAudioCasterIntent(pkgName, context, uri, audio)
            else -> standardIntentForPackage(pkgName, context, uri, audio)
        }
    }

    private fun webAudioCasterIntent(pkgName: String, context: Context, uri: Uri, audio: Audio): Intent {
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "audio/*")
            if (isPackageInstalled(pkgName, context.packageManager)) setPackage(WEB_VIDEO_CASTER)
            addExtrasAndFlags(true, this)

            val headers = Bundle()
            audio.headers?.forEach {
                headers.putString(it.first, it.second)
            }

            val localLangName = LocaleHelper.getSimpleLocaleDisplayName()
            audio.subtitleTracks.firstOrNull {
                it.lang.contains(localLangName)
            }?.let {
                putExtra("subtitle", it.url)
            } ?: audio.subtitleTracks.firstOrNull()?.let {
                putExtra("subtitle", it.url)
            }

            putExtra("android.media.intent.extra.HTTP_HEADERS", headers)
            putExtra("secure_uri", true)
        }
    }

    /**
     * Returns the [Intent] with added data to send to the given external player.
     *
     * @param pkgName the name of the package to send the [Intent] to.
     * @param context the application context.
     * @param uri the path data of the audio.
     * @param audio the audio being sent to the external player.
     */
    private fun standardIntentForPackage(pkgName: String, context: Context, uri: Uri, audio: Audio): Intent {
        return Intent(Intent.ACTION_VIEW).apply {
            if (isPackageInstalled(pkgName, context.packageManager)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && pkgName.contains("vlc")) {
                    setPackage(pkgName)
                } else {
                    component = getComponent(pkgName)
                }
            }
            setDataAndType(uri, "audio/*")
            addExtrasAndFlags(true, this)
            addAudioHeaders(true, audio, this)

            // Add support for Subtitles to external players

            val localLangName = LocaleHelper.getSimpleLocaleDisplayName()
            val langIndex = audio.subtitleTracks.indexOfFirst {
                it.lang.contains(localLangName)
            }
            val requestedLanguage = if (langIndex == -1) 0 else langIndex
            val requestedUrl = audio.subtitleTracks.getOrNull(requestedLanguage)?.url

            // Just, Next, MX Player, mpv
            putExtra("subs", audio.subtitleTracks.map { Uri.parse(it.url) }.toTypedArray())
            putExtra("subs.name", audio.subtitleTracks.map { it.lang }.toTypedArray())
            putExtra("subs.enable", requestedUrl?.let { arrayOf(Uri.parse(it)) } ?: emptyArray())

            // VLC - seems to only work for local sub files
            requestedUrl?.let { putExtra("subtitles_location", it) }
        }
    }

    /**
     * Adds extras and flags to the given [Intent].
     *
     * @param isSupportedPlayer is it a supported external player.
     * @param intent the [Intent] that the extras and flags are added to.
     */
    private fun addExtrasAndFlags(isSupportedPlayer: Boolean, intent: Intent): Intent {
        return intent.apply {
            putExtra("title", audiobook.title + " - " + chapter.name)
            putExtra("position", getLastSecondRead().toInt())
            putExtra("return_result", true)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (isSupportedPlayer) putExtra("secure_uri", true)
        }
    }

    /**
     * Adds the headers of the audio to the given [Intent].
     *
     * @param isSupportedPlayer is it a supported external player.
     * @param audio the [Audio] to get the headers from.
     * @param intent the [Intent] that the headers are added to.
     */
    private fun addAudioHeaders(isSupportedPlayer: Boolean, audio: Audio, intent: Intent): Intent {
        return intent.apply {
            val headers = audio.headers ?: (source as? AudiobookHttpSource)?.headers
            if (headers != null) {
                var headersArray = arrayOf<String>()
                for (header in headers) {
                    headersArray += arrayOf(header.first, header.second)
                }
                putExtra("headers", headersArray)
                val headersString = headersArray.drop(2).joinToString(": ")
                if (!isSupportedPlayer) putExtra("http-header-fields", headersString)
            }
        }
    }

    /**
     * Returns the MIME type based on the audio's extension.
     *
     * @param uri the path data of the audio.
     */
    private fun getMime(uri: Uri): String {
        return when (uri.path?.substringAfterLast(".")) {
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/x-wav"
            else -> "audio/any"
        }
    }

    /**
     * Returns the specific activity to be called.
     * If the package is a part of the supported external players
     *
     * @param packageName the name of the package.
     */
    private fun getComponent(packageName: String): ComponentName? {
        return when (packageName) {
            MPV_PLAYER -> ComponentName(packageName, "$packageName.MPVActivity")
            MX_PLAYER, MX_PLAYER_FREE, MX_PLAYER_PRO -> ComponentName(
                packageName,
                "$packageName.ActivityScreen",
            )
            VLC_PLAYER -> ComponentName(packageName, "$packageName.gui.video.VideoPlayerActivity")
            MPV_REMOTE -> ComponentName(packageName, "$packageName.MainActivity")
            JUST_PLAYER -> ComponentName(packageName, "$packageName.PlayerActivity")
            NEXT_PLAYER -> ComponentName(packageName, "$packageName.feature.player.PlayerActivity")
            X_PLAYER -> ComponentName(packageName, "com.inshot.xplayer.activities.PlayerActivity")
            else -> null
        }
    }

    /**
     * Returns true if the given package is installed on the device.
     *
     * @param packageName the name of the package to be found.
     * @param packageManager the instance of the package manager provided by the device.
     */
    private fun isPackageInstalled(packageName: String, packageManager: PackageManager): Boolean {
        return try {
            packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * Saves the chapter's data based on whats returned by the external player.
     *
     * @param intent the [Intent] that contains the chapter's position and duration.
     */
    @OptIn(DelicateCoroutinesApi::class)
    @Suppress("DEPRECATION")
    fun onActivityResult(intent: Intent?) {
        val data = intent ?: return
        val audiobook = audiobook
        val currentExtChapter = chapter
        val currentPosition: Long
        val duration: Long
        val cause = data.getStringExtra("end_by") ?: ""

        // Check for position and duration as Long values
        if (cause.isNotEmpty()) {
            val positionExtra = data.extras?.get("position")
            currentPosition = if (positionExtra is Int) {
                positionExtra.toLong()
            } else {
                positionExtra as? Long ?: 0L
            }
            val durationExtra = data.extras?.get("duration")
            duration = if (durationExtra is Int) {
                durationExtra.toLong()
            } else {
                durationExtra as? Long ?: 0L
            }
        } else {
            if (data.extras?.get("extra_position") != null) {
                currentPosition = data.getLongExtra("extra_position", 0L)
                duration = data.getLongExtra("extra_duration", 0L)
            } else {
                currentPosition = data.getIntExtra("position", 0).toLong()
                duration = data.getIntExtra("duration", 0).toLong()
            }
        }

        // Update the chapter's progress and history
        launchIO {
            if (cause == "playback_completion" || (currentPosition == duration && duration == 0L)) {
                saveChapterProgress(
                    currentExtChapter,
                    audiobook,
                    currentExtChapter.totalSeconds,
                    currentExtChapter.totalSeconds,
                )
            } else {
                saveChapterProgress(currentExtChapter, audiobook, currentPosition, duration)
            }
            saveChapterHistory(currentExtChapter)
        }
    }

    // List of all the required Injectable classes
    private val upsertHistory: UpsertAudiobookHistory = Injekt.get()
    private val updateChapter: UpdateChapter = Injekt.get()
    private val getAudiobook: GetAudiobook = Injekt.get()
    private val sourceManager: AudiobookSourceManager = Injekt.get()
    private val getChaptersByAudiobookId: GetChaptersByAudiobookId = Injekt.get()
    private val getTracks: GetAudiobookTracks = Injekt.get()
    private val insertTrack: InsertAudiobookTrack = Injekt.get()
    private val downloadManager: AudiobookDownloadManager by injectLazy()
    private val delayedTrackingStore: DelayedAudiobookTrackingStore = Injekt.get()
    private val playerPreferences: AudioplayerPreferences = Injekt.get()
    private val downloadPreferences: DownloadPreferences = Injekt.get()
    private val trackPreferences: TrackPreferences = Injekt.get()
    private val basePreferences: BasePreferences by injectLazy()

    /**
     * Saves this chapter's last read history if incognito mode isn't on.
     *
     * @param currentChapter the chapter to update.
     */
    private suspend fun saveChapterHistory(currentChapter: Chapter) {
        if (basePreferences.incognitoMode().get()) return
        upsertHistory.await(
            AudiobookHistoryUpdate(currentChapter.id, Date()),
        )
    }

    /**
     * Saves this chapter's progress (last read second and whether it's read).
     * Only if incognito mode isn't on
     *
     * @param currentChapter the chapter to update.
     * @param audiobook the audiobook of the chapter.
     * @param lastSecondRead the position of the chapter.
     * @param totalSeconds the duration of the chapter.
     */
    private suspend fun saveChapterProgress(
        currentChapter: Chapter?,
        audiobook: Audiobook,
        lastSecondRead: Long,
        totalSeconds: Long,
    ) {
        if (basePreferences.incognitoMode().get()) return
        val currEp = currentChapter ?: return

        if (totalSeconds > 0L) {
            val progress = playerPreferences.progressPreference().get()
            val read = if (!currEp.read) lastSecondRead >= totalSeconds * progress else true
            updateChapter.await(
                ChapterUpdate(
                    id = currEp.id,
                    read = read,
                    bookmark = currEp.bookmark,
                    lastSecondRead = lastSecondRead,
                    totalSeconds = totalSeconds,
                ),
            )
            if (trackPreferences.autoUpdateTrack().get() && currEp.read) {
                updateTrackChapterRead(currEp.chapterNumber.toDouble(), audiobook)
            }
            if (read) {
                deleteChapterIfNeeded(currentChapter, audiobook)
            }
        }
    }

    /**
     * Determines if deleting option is enabled and nth to last chapter actually exists.
     * If both conditions are satisfied enqueues chapter for delete
     *
     * @param chapter the chapter, which is going to be marked as read.
     * @param audiobook the audiobook of the chapter.
     */
    private suspend fun deleteChapterIfNeeded(chapter: Chapter, audiobook: Audiobook) {
        // Determine which chapter should be deleted and enqueue
        val sortFunction: (Chapter, Chapter) -> Int = when (audiobook.sorting) {
            Audiobook.CHAPTER_SORTING_SOURCE -> { c1, c2 -> c2.sourceOrder.compareTo(c1.sourceOrder) }
            Audiobook.CHAPTER_SORTING_NUMBER -> { c1, c2 -> c1.chapterNumber.compareTo(c2.chapterNumber) }
            Audiobook.CHAPTER_SORTING_UPLOAD_DATE -> { c1, c2 -> c1.dateUpload.compareTo(c2.dateUpload) }
            else -> throw NotImplementedError("Unknown sorting method")
        }

        val chapters = getChaptersByAudiobookId.await(audiobook.id)
            .sortedWith { e1, e2 -> sortFunction(e1, e2) }

        val currentChapterPosition = chapters.indexOf(chapter)
        val removeAfterReadSlots = downloadPreferences.removeAfterReadSlots().get()
        val chapterToDelete = chapters.getOrNull(currentChapterPosition - removeAfterReadSlots)

        // Check if deleting option is enabled and chapter exists
        if (removeAfterReadSlots != -1 && chapterToDelete != null) {
            enqueueDeleteReadChapters(chapterToDelete, audiobook)
        }
    }

    /**
     * Starts the service that updates the last chapter read in sync services.
     * This operation will run in a background thread and errors are ignored.
     *
     * @param chapterNumber the chapter number to be updated.
     * @param audiobook the audiobook of the chapter.
     */
    private suspend fun updateTrackChapterRead(chapterNumber: Double, audiobook: Audiobook) {
        if (!trackPreferences.autoUpdateTrack().get()) return

        val trackerManager = Injekt.get<TrackerManager>()
        val context = Injekt.get<Application>()

        withIOContext {
            getTracks.await(audiobook.id)
                .mapNotNull { track ->
                    val tracker = trackerManager.get(track.syncId)
                    if (tracker != null && tracker.isLoggedIn &&
                        tracker is AudiobookTracker && chapterNumber > track.lastChapterRead
                    ) {
                        val updatedTrack = track.copy(lastChapterRead = chapterNumber)

                        // We want these to execute even if the presenter is destroyed and leaks
                        // for a while. The view can still be garbage collected.
                        async {
                            runCatching {
                                if (context.isOnline()) {
                                    tracker.audiobookService.update(updatedTrack.toDbTrack(), true)
                                    insertTrack.await(updatedTrack)
                                } else {
                                    delayedTrackingStore.addAudiobook(track.audiobookId, lastChapterRead = chapterNumber)
                                    DelayedAudiobookTrackingUpdateJob.setupTask(context)
                                }
                            }
                        }
                    } else {
                        null
                    }
                }
                .awaitAll()
                .mapNotNull { it.exceptionOrNull() }
                .forEach { logcat(LogPriority.INFO, it) }
        }
    }

    /**
     * Enqueues an [Chapter] to be deleted later.
     *
     * @param chapter the chapter being deleted.
     * @param audiobook the audiobook of the chapter.
     */
    private suspend fun enqueueDeleteReadChapters(chapter: Chapter, audiobook: Audiobook) {
        if (chapter.read) {
            withIOContext {
                downloadManager.enqueueChaptersToDelete(
                    listOf(chapter),
                    audiobook,
                )
            }
        }
    }

    companion object {

        val externalIntents: ExternalIntents by injectLazy()

        /**
         * Used to direct the [Intent] of a chosen chapter to an external player.
         *
         * @param context the application context.
         * @param audiobookId the id of the audiobook.
         * @param chapterId the id of the chapter.
         */
        suspend fun newIntent(context: Context, audiobookId: Long?, chapterId: Long?, audio: Audio?): Intent? {
            return externalIntents.getExternalIntent(context, audiobookId, chapterId, audio)
        }
    }
}

// List of supported external players and their packages
const val MPV_PLAYER = "is.xyz.mpv"
const val MX_PLAYER = "com.mxtech.videoplayer"
const val MX_PLAYER_FREE = "com.mxtech.videoplayer.ad"
const val MX_PLAYER_PRO = "com.mxtech.videoplayer.pro"
const val VLC_PLAYER = "org.videolan.vlc"
const val MPV_REMOTE = "com.husudosu.mpvremote"
const val JUST_PLAYER = "com.brouken.player"
const val NEXT_PLAYER = "dev.anilbeesetti.nextplayer"
const val X_PLAYER = "video.player.videoplayer"
const val WEB_VIDEO_CASTER = "com.instantbits.cast.webvideo"
