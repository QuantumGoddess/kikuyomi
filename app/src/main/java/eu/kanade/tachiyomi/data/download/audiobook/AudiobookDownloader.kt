package eu.kanade.tachiyomi.data.download.audiobook

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.FFprobeSession
import com.arthenica.ffmpegkit.Level
import com.arthenica.ffmpegkit.LogCallback
import com.arthenica.ffmpegkit.SessionState
import com.hippo.unifile.UniFile
import eu.kanade.domain.items.audiobookchapter.model.toSChapter
import eu.kanade.tachiyomi.audiobooksource.UnmeteredSource
import eu.kanade.tachiyomi.audiobooksource.model.Audio
import eu.kanade.tachiyomi.audiobooksource.online.AudiobookHttpSource
import eu.kanade.tachiyomi.data.cache.AudiobookChapterCache
import eu.kanade.tachiyomi.data.download.audiobook.model.AudiobookDownload
import eu.kanade.tachiyomi.data.library.audiobook.AudiobookLibraryUpdateNotifier
import eu.kanade.tachiyomi.data.notification.NotificationHandler
import eu.kanade.tachiyomi.util.storage.DiskUtil
import eu.kanade.tachiyomi.util.storage.toFFmpegString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import logcat.LogPriority
import okhttp3.HttpUrl.Companion.toHttpUrl
import okio.Buffer
import rx.subjects.PublishSubject
import tachiyomi.core.i18n.stringResource
import tachiyomi.core.storage.extension
import tachiyomi.core.util.lang.launchIO
import tachiyomi.core.util.system.ImageUtil
import tachiyomi.core.util.system.logcat
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.entries.audiobook.model.Audiobook
import tachiyomi.domain.items.audiobookchapter.model.Chapter
import tachiyomi.domain.source.audiobook.service.AudiobookSourceManager
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException

/**
 * This class is the one in charge of downloading chapters.
 *
 * Its queue contains the list of chapters to download. In order to download them, the downloader
 * subscription must be running and the list of chapters must be sent to them by [downloaderJob].
 *
 * The queue manipulation must be done in one thread (currently the main thread) to avoid unexpected
 * behavior, but it's safe to read it from multiple threads.
 */
class AudiobookDownloader(
    private val context: Context,
    private val provider: AudiobookDownloadProvider,
    private val cache: AudiobookDownloadCache,
    private val sourceManager: AudiobookSourceManager = Injekt.get(),
    private val chapterCache: AudiobookChapterCache = Injekt.get(),
) {

    /**
     * Store for persisting downloads across restarts.
     */
    private val store = AudiobookDownloadStore(context)

    /**
     * Queue where active downloads are kept.
     */
    private val _queueState = MutableStateFlow<List<AudiobookDownload>>(emptyList())
    val queueState = _queueState.asStateFlow()

    /**
     * Notifier for the downloader state and progress.
     */
    private val notifier by lazy { AudiobookDownloadNotifier(context) }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var downloaderJob: Job? = null

    /**
     * Preference for user's choice of external downloader
     */
    private val preferences: DownloadPreferences by injectLazy()

    /**
     * Whether the downloader is running.
     */
    val isRunning: Boolean
        get() = downloaderJob?.isActive ?: false

    /**
     * Whether the downloader is paused
     */
    @Volatile
    var isPaused: Boolean = false

    /**
     * Whether FFmpeg is running.
     */
    @Volatile
    var isFFmpegRunning: Boolean = false

    init {
        scope.launch {
            val chapters = async { store.restore() }
            addAllToQueue(chapters.await())
        }
    }

    /**
     * Starts the downloader. It doesn't do anything if it's already running or there isn't anything
     * to download.
     *
     * @return true if the downloader is started, false otherwise.
     */
    fun start(): Boolean {
        if (isRunning || queueState.value.isEmpty()) {
            return false
        }

        val pending = queueState.value.filter { it.status != AudiobookDownload.State.DOWNLOADED }
        pending.forEach { if (it.status != AudiobookDownload.State.QUEUE) it.status = AudiobookDownload.State.QUEUE }

        isPaused = false

        launchDownloaderJob()

        return pending.isNotEmpty()
    }

    /**
     * Stops the downloader.
     */
    fun stop(reason: String? = null) {
        cancelDownloaderJob()
        queueState.value
            .filter { it.status == AudiobookDownload.State.DOWNLOADING }
            .forEach { it.status = AudiobookDownload.State.ERROR }

        if (reason != null) {
            notifier.onWarning(reason)
            return
        }

        if (isPaused && queueState.value.isNotEmpty()) {
            notifier.onPaused()
        } else {
            notifier.onComplete()
        }

        isPaused = false

        AudiobookDownloadJob.stop(context)
    }

    /**
     * Pauses the downloader
     */
    fun pause() {
        cancelDownloaderJob()
        queueState.value
            .filter { it.status == AudiobookDownload.State.DOWNLOADING }
            .forEach { it.status = AudiobookDownload.State.QUEUE }
        isPaused = true
    }

    /**
     * Removes everything from the queue.
     */
    fun clearQueue() {
        cancelDownloaderJob()

        _clearQueue()
        notifier.dismissProgress()
    }

    /**
     * Prepares the jobs to start downloading.
     */
    private fun launchDownloaderJob() {
        if (isRunning) return

        downloaderJob = scope.launch {
            val activeDownloadsFlow = queueState.transformLatest { queue ->
                while (true) {
                    val activeDownloads = queue.asSequence()
                        .filter {
                            it.status.value <= AudiobookDownload.State.DOWNLOADING.value
                        } // Ignore completed downloads, leave them in the queue
                        .groupBy { it.source }
                        .toList().take(3) // Concurrently download from 5 different sources
                        .map { (_, downloads) -> downloads.first() }
                    emit(activeDownloads)

                    if (activeDownloads.isEmpty()) break

                    // Suspend until a download enters the ERROR state
                    val activeDownloadsErroredFlow =
                        combine(activeDownloads.map(AudiobookDownload::statusFlow)) { states ->
                            states.contains(AudiobookDownload.State.ERROR)
                        }.filter { it }
                    activeDownloadsErroredFlow.first()
                }
            }.distinctUntilChanged()

            // Use supervisorScope to cancel child jobs when the downloader job is cancelled
            supervisorScope {
                val downloadJobs = mutableMapOf<AudiobookDownload, Job>()

                activeDownloadsFlow.collectLatest { activeDownloads ->
                    val downloadJobsToStop = downloadJobs.filter { it.key !in activeDownloads }
                    downloadJobsToStop.forEach { (download, job) ->
                        job.cancel()
                        downloadJobs.remove(download)
                    }

                    val downloadsToStart = activeDownloads.filter { it !in downloadJobs }
                    downloadsToStart.forEach { download ->
                        downloadJobs[download] = launchDownloadJob(download)
                    }
                }
            }
        }
    }

    private fun CoroutineScope.launchDownloadJob(download: AudiobookDownload) = launchIO {
        try {
            downloadChapter(download)

            // Remove successful download from queue
            if (download.status == AudiobookDownload.State.DOWNLOADED) {
                removeFromQueue(download)
            }

            if (download.status == AudiobookDownload.State.QUEUE) {
                pause()
            }

            if (areAllAudiobookDownloadsFinished()) {
                stop()
            }
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            logcat(LogPriority.ERROR, e)
            notifier.onError(e.message)
            stop()
        }
    }

    /**
     * Destroys the downloader subscriptions.
     */
    private fun cancelDownloaderJob() {
        isFFmpegRunning = false
        FFmpegKitConfig.getSessions().filter {
            it.isFFmpeg && (it.state == SessionState.CREATED || it.state == SessionState.RUNNING)
        }.forEach {
            it.cancel()
        }

        downloaderJob?.cancel()
        downloaderJob = null
    }

    /**
     * Creates a download object for every chapter and adds them to the downloads queue.
     *
     * @param audiobook the audiobook of the chapters to download.
     * @param chapters the list of chapters to download.
     * @param autoStart whether to start the downloader after enqueing the chapters.
     */
    fun queueChapters(
        audiobook: Audiobook,
        chapters: List<Chapter>,
        autoStart: Boolean,
        changeDownloader: Boolean = false,
        audio: Audio? = null,
    ) {
        if (chapters.isEmpty()) return

        val source = sourceManager.get(audiobook.source) as? AudiobookHttpSource ?: return
        val wasEmpty = queueState.value.isEmpty()

        val chaptersToQueue = chapters.asSequence()
            // Filter out those already downloaded.
            .filter { provider.findChapterDir(it.name, it.scanlator, audiobook.title, source) == null }
            // Add chapters to queue from the start.
            .sortedByDescending { it.sourceOrder }
            // Filter out those already enqueued.
            .filter { chapter -> queueState.value.none { it.chapter.id == chapter.id } }
            // Create a download for each one.
            .map { AudiobookDownload(source, audiobook, it, changeDownloader, audio) }
            .toList()

        if (chaptersToQueue.isNotEmpty()) {
            addAllToQueue(chaptersToQueue)

            // Start downloader if needed
            if (autoStart && wasEmpty) {
                val queuedDownloads =
                    queueState.value.count { it: AudiobookDownload -> it.source !is UnmeteredSource }
                val maxDownloadsFromSource = queueState.value
                    .groupBy { it.source }
                    .filterKeys { it !is UnmeteredSource }
                    .maxOfOrNull { it.value.size }
                    ?: 0
                // TODO: show warnings in stable
                if (
                    queuedDownloads > DOWNLOADS_QUEUED_WARNING_THRESHOLD ||
                    maxDownloadsFromSource > CHAPTERS_PER_SOURCE_QUEUE_WARNING_THRESHOLD
                ) {
                    notifier.onWarning(
                        context.stringResource(MR.strings.download_queue_size_warning),
                        WARNING_NOTIF_TIMEOUT_MS,
                        NotificationHandler.openUrl(
                            context,
                            AudiobookLibraryUpdateNotifier.HELP_WARNING_URL,
                        ),
                    )
                }
                AudiobookDownloadJob.start(context)
            }
        }
    }

    /**
     * Returns the observable which downloads an chapter.
     *
     * @param download the chapter to be downloaded.
     */
    private suspend fun downloadChapter(download: AudiobookDownload) {
        val audiobookDir = provider.getAudiobookDir(download.audiobook.title, download.source)

        val availSpace = DiskUtil.getAvailableStorageSpace(audiobookDir)
        if (availSpace != -1L && availSpace < MIN_DISK_SPACE) {
            download.status = AudiobookDownload.State.ERROR
            notifier.onError(
                context.stringResource(MR.strings.download_insufficient_space),
                download.chapter.name,
                download.audiobook.title,
            )
            return
        }

        val chapterDirname = provider.getChapterDirName(download.chapter.name, download.chapter.scanlator)
        val tmpDir = audiobookDir.createDirectory(chapterDirname + TMP_DIR_SUFFIX)!!
        notifier.onProgressChange(download)

        val audio = if (download.audio == null) {
            // Pull audio from network and add them to download object
            try {
                val fetchedAudio = download.source.getAudioList(download.chapter.toSChapter()).first()
                download.audio = fetchedAudio
                fetchedAudio
            } catch (e: Exception) {
                throw Exception(context.stringResource(MR.strings.audio_list_empty_error))
            }
        } else {
            // Or if the audio already exists, return it
            download.audio!!
        }

        if (download.audio!!.bytesDownloaded == 0L) {
            // Delete all temporary (unfinished) files
            tmpDir.listFiles()
                ?.filter { it.extension == ".tmp" }
                ?.forEach { it.delete() }
        }

        download.downloadedImages = 0
        download.status = AudiobookDownload.State.DOWNLOADING

        val progressJob = scope.launch {
            while (download.status == AudiobookDownload.State.DOWNLOADING) {
                delay(50)
                val progress = download.audio!!.progress
                if (download.totalProgress != progress) {
                    download.totalProgress = progress
                    notifier.onProgressChange(download)
                }
            }
        }

        try {
            // Replace this with your actual download logic
            getOrAudiobookDownloadAudio(audio, download, tmpDir)
        } catch (e: Exception) {
            download.status = AudiobookDownload.State.ERROR
            notifier.onError(e.message, download.chapter.name, download.audiobook.title)
        } finally {
            progressJob.cancel()
        }

        try {
            ensureSuccessfulAudiobookDownload(download, audiobookDir, tmpDir, chapterDirname)
            if (download.status == AudiobookDownload.State.DOWNLOADED) {
                notifier.dismissProgress()
            }
        } catch (e: Exception) {
            download.status = AudiobookDownload.State.ERROR
            notifier.onError(e.message, download.chapter.name, download.audiobook.title)
        }
    }

    /**
     * Returns the observable which gets the image from the filesystem if it exists or downloads it
     * otherwise.
     *
     * @param audio the audio to download.
     * @param download the download of the audio.
     * @param tmpDir the temporary directory of the download.
     */
    private suspend fun getOrAudiobookDownloadAudio(
        audio: Audio,
        download: AudiobookDownload,
        tmpDir: UniFile,
    ): Audio {
        // If the audio URL is empty, do nothing
        if (audio.audioUrl == null) {
            return audio
        }

        val filename = DiskUtil.buildValidFilename(download.chapter.name)

        if (audio.bytesDownloaded == 0L) {
            val tmpFile = tmpDir.findFile("$filename.tmp")

            // Delete temp file if it exists
            tmpFile?.delete()
        }

        val audioFile = tmpDir.listFiles()?.firstOrNull { it.name!!.startsWith("$filename.") }

        // If the audio is already downloaded, do nothing. Otherwise download from network
        val file = when {
            audioFile != null -> audioFile
            chapterCache.isImageInCache(audio.audioUrl!!) -> copyAudioFromCache(
                chapterCache.getAudioFile(audio.audioUrl!!),
                tmpDir,
                filename,
            )
            else -> {
                if (preferences.useExternalDownloader().get() == download.changeDownloader) {
                    downloadAudio(audio, download, tmpDir, filename)
                } else {
                    val betterFileName = DiskUtil.buildValidFilename(
                        "${download.audiobook.title} - ${download.chapter.name}",
                    )
                    downloadAudioExternal(audio, download.source, tmpDir, betterFileName)
                }
            }
        }

        // When the audio is ready, set image path, progress (just in case) and status
        try {
            audio.audioUrl = file.uri.path
            audio.progress = 100
            download.downloadedImages++
            audio.status = Audio.State.READY
        } catch (e: Exception) {
            audio.progress = 0
            audio.status = Audio.State.ERROR
            notifier.onError(e.message, download.chapter.name, download.audiobook.title)
        }

        return audio
    }

    /**
     * Returns the observable which downloads the audio from network.
     *
     * @param audio the audio to download.
     * @param download the AudiobookDownload.
     * @param tmpDir the temporary directory of the download.
     * @param filename the filename of the audio.
     */
    private suspend fun downloadAudio(
        audio: Audio,
        download: AudiobookDownload,
        tmpDir: UniFile,
        filename: String,
    ): UniFile {
        audio.status = Audio.State.DOWNLOAD_IMAGE
        audio.progress = 0
        var tries = 0

        // Define a suspend function to encapsulate the retry logic
        suspend fun attemptDownload(): UniFile {
            return try {
                newDownload(audio, download, tmpDir, filename)
            } catch (e: Exception) {
                if (tries >= 2) throw e
                tries++
                delay((2 shl (tries - 1)) * 1000L)
                attemptDownload()
            }
        }

        return attemptDownload()
    }

    private fun isMpd(audio: Audio): Boolean {
        return audio.audioUrl?.toHttpUrl()?.encodedPath?.endsWith(".mpd") ?: false
    }

    private fun isHls(audio: Audio): Boolean {
        return audio.audioUrl?.toHttpUrl()?.encodedPath?.endsWith(".m3u8") ?: false
    }

    private suspend fun ffmpegDownload(
        audio: Audio,
        download: AudiobookDownload,
        tmpDir: UniFile,
        filename: String,
    ): UniFile = coroutineScope {
        isFFmpegRunning = true
        val headers = audio.headers ?: download.source.headers
        val headerOptions = headers.joinToString("", "-headers '", "'") {
            "${it.first}: ${it.second}\r\n"
        }
        val audioFile = tmpDir.findFile("$filename.tmp") ?: tmpDir.createFile("$filename.tmp")!!
        val ffmpegFilename = { audioFile.uri.toFFmpegString(context) }

        val ffmpegOptions = getFFmpegOptions(audio, headerOptions, ffmpegFilename())
        val ffprobeCommand = { file: String, ffprobeHeaders: String? ->
            FFmpegKitConfig.parseArguments(
                "${ffprobeHeaders?.plus(" ") ?: ""}-v error -show_entries " +
                    "format=duration -of default=noprint_wrappers=1:nokey=1 \"$file\"",
            )
        }
        var duration = 0L
        var nextLineIsDuration = false
        val logCallback = LogCallback { log ->
            if (nextLineIsDuration) {
                parseDuration(log.message)?.let { duration = it }
                nextLineIsDuration = false
            }
            if (log.level <= Level.AV_LOG_WARNING) log.message?.let { logcat { it } }
            if (duration != 0L && log.message.startsWith("frame=")) {
                val outTime = log.message
                    .substringAfter("time=", "")
                    .substringBefore(" ", "")
                    .let { parseTimeStringToSeconds(it) }
                if (outTime != null && outTime > 0L) audio.progress = (100 * outTime / duration).toInt()
            }
        }
        val session = FFmpegSession.create(ffmpegOptions, {}, logCallback, {})
        val inputDuration = getDuration(ffprobeCommand(audio.audioUrl!!, headerOptions)) ?: 0F
        duration = inputDuration.toLong()
        FFmpegKitConfig.ffmpegExecute(session)
        val outputDuration = getDuration(ffprobeCommand(ffmpegFilename(), null)) ?: 0F
        // allow for slight errors
        if (inputDuration > outputDuration * 1.01F) {
            tmpDir.findFile("$filename.tmp")?.delete()
        }
        session.failStackTrace?.let { trace ->
            logcat(LogPriority.ERROR) { trace }
            throw Exception("Error in ffmpeg!")
        }

        val file = tmpDir.findFile("$filename.tmp")?.apply {
            renameTo("$filename.mkv")
        }
        file ?: throw Exception("Downloaded file not found")
    }

    private fun parseTimeStringToSeconds(timeString: String): Long? {
        val parts = timeString.split(":")
        if (parts.size != 3) {
            // Invalid format
            return null
        }

        return try {
            val hours = parts[0].toInt()
            val minutes = parts[1].toInt()
            val secondsAndMilliseconds = parts[2].split(".")
            val seconds = secondsAndMilliseconds[0].toInt()
            val milliseconds = secondsAndMilliseconds[1].toInt()

            (hours * 3600 + minutes * 60 + seconds + milliseconds / 100.0).toLong()
        } catch (e: NumberFormatException) {
            // Invalid number format
            null
        }
    }

    private fun getFFmpegOptions(audio: Audio, headerOptions: String, ffmpegFilename: String): Array<String> {
        val subtitleInputs = audio.subtitleTracks.joinToString(" ", postfix = " ") {
            "-i \"${it.url}\""
        }
        val subtitleMaps = audio.subtitleTracks.indices.joinToString(" ") {
            val index = it + 1
            "-map $index:s"
        }
        val subtitleMetadata = audio.subtitleTracks.mapIndexed { i, sub ->
            "-metadata:s:s:$i \"title=${sub.lang}\""
        }.joinToString(" ")

        Locale("")
        return FFmpegKitConfig.parseArguments(
            headerOptions +
                " -i \"${audio.audioUrl}\" " + subtitleInputs +
                "-map 0:v -map 0:a " + subtitleMaps + " -map 0:s?" +
                " -f matroska -c:a copy -c:v copy -c:s ass " +
                subtitleMetadata +
                " \"$ffmpegFilename\" -y",
        )
    }

    private fun getDuration(ffprobeCommand: Array<String>): Float? {
        val session = FFprobeSession.create(ffprobeCommand)
        FFmpegKitConfig.ffprobeExecute(session)
        return session.allLogsAsString.trim().toFloatOrNull()
    }

    /**
     * Returns the parsed duration in milliseconds
     *
     * @param durationString the string formatted in HOURS:MINUTES:SECONDS.HUNDREDTHS
     */
    private fun parseDuration(durationString: String): Long? {
        val splitString = durationString.split(":")
        if (splitString.lastIndex != 2) return null
        val hours = splitString[0].toLong()
        val minutes = splitString[1].toLong()
        val secondsString = splitString[2].split(".")
        if (secondsString.lastIndex != 1) return null
        val fullSeconds = secondsString[0].toLong()
        val hundredths = secondsString[1].toLong()
        return hours * 3600000L + minutes * 60000L + fullSeconds * 1000L + hundredths * 10L
    }

    private suspend fun newDownload(
        audio: Audio,
        download: AudiobookDownload,
        tmpDir: UniFile,
        filename: String,
    ): UniFile {
        // Check if the download is paused before starting
        while (isPaused) {
            delay(1000) // This is a pause check delay, adjust the timing as needed.
        }

        if (isHls(audio) || isMpd(audio)) {
            return ffmpegDownload(audio, download, tmpDir, filename)
        } else {
            val response = download.source.getAudio(audio)
            val file = tmpDir.findFile("$filename.tmp") ?: tmpDir.createFile("$filename.tmp")!!

            // Write to file with pause/resume capability
            try {
                response.body.source().use { source ->
                    file.openOutputStream(true).use { output ->
                        val buffer = Buffer()
                        var totalBytesRead = 0L
                        var bytesRead: Int
                        val bufferSize = 4 * 1024
                        while (source.read(buffer, bufferSize.toLong()).also { bytesRead = it.toInt() } != -1L) {
                            // Check if the download is paused, if so, wait
                            while (isPaused) {
                                delay(1000) // Wait for 1 second before checking again
                            }

                            // Write the bytes to the file
                            output.write(bytesRead)
                            totalBytesRead += bytesRead
                            audio.progress = (totalBytesRead * 100 / response.body.contentLength()).toInt()
                            // Update progress here if needed
                        }
                    }
                }
                // After download is complete, rename the file to its final name
                file.renameTo("$filename.mp4")
                return file
            } catch (e: Exception) {
                response.close()
                if (!queueState.value.equals(download)) file.delete()
                throw e
            }
        }
    }

    /**
     * Returns the observable which downloads the audio with an external downloader.
     *
     * @param audio the audio to download.
     * @param source the source of the audio.
     * @param tmpDir the temporary directory of the download.
     * @param filename the filename of the audio.
     */
    private fun downloadAudioExternal(
        audio: Audio,
        source: AudiobookHttpSource,
        tmpDir: UniFile,
        filename: String,
    ): UniFile {
        audio.status = Audio.State.DOWNLOAD_IMAGE
        audio.progress = 0

        try {
            val file = tmpDir.createFile("$filename.mp4")!!

            // TODO: support other file formats!!
            // start download with intent
            val pm = context.packageManager
            val pkgName = preferences.externalDownloaderSelection().get()
            val intent: Intent
            if (pkgName.isNotEmpty()) {
                intent = pm.getLaunchIntentForPackage(pkgName) ?: throw Exception(
                    "Launch intent not found",
                )
                when {
                    // 1DM
                    pkgName.startsWith("idm.internet.download.manager") -> {
                        intent.apply {
                            component = ComponentName(
                                pkgName,
                                "idm.internet.download.manager.Downloader",
                            )
                            action = Intent.ACTION_VIEW
                            data = Uri.parse(audio.audioUrl)
                            putExtra("extra_filename", filename)
                        }
                    }
                    // ADM
                    pkgName.startsWith("com.dv.adm") -> {
                        val headers = (audio.headers ?: source.headers).toList()
                        val bundle = Bundle()
                        headers.forEach { a ->
                            bundle.putString(
                                a.first,
                                a.second.replace("http", "h_ttp"),
                            )
                        }

                        intent.apply {
                            component = ComponentName(pkgName, "$pkgName.AEditor")
                            action = Intent.ACTION_VIEW
                            putExtra(
                                "com.dv.get.ACTION_LIST_ADD",
                                "${Uri.parse(audio.audioUrl)}<info>$filename.mp4",
                            )
                            putExtra(
                                "com.dv.get.ACTION_LIST_PATH",
                                tmpDir.filePath!!.substringBeforeLast("_"),
                            )
                            putExtra("android.media.intent.extra.HTTP_HEADERS", bundle)
                        }
                        file.delete()
                        tmpDir.delete()
                        queueState.value.find { audiobook -> audiobook.audio == audio }?.let { download ->
                            download.status = AudiobookDownload.State.DOWNLOADED
                            // Delete successful downloads from queue
                            if (download.status == AudiobookDownload.State.DOWNLOADED) {
                                // Remove downloaded chapter from queue
                                removeFromQueue(download)
                            }
                            if (areAllAudiobookDownloadsFinished()) {
                                stop()
                            }
                        }
                    }
                }
            } else {
                intent = Intent(Intent.ACTION_VIEW).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    setDataAndType(Uri.parse(audio.audioUrl), "audio/*")
                    putExtra("extra_filename", filename)
                }
            }
            context.startActivity(intent)
            return file
        } catch (e: Exception) {
            tmpDir.findFile("$filename.mp4")?.delete()
            throw e
        }
    }

    /**
     * Return the observable which copies the audio from cache.
     *
     * @param cacheFile the file from cache.
     * @param tmpDir the temporary directory of the download.
     * @param filename the filename of the audio.
     */
    private fun copyAudioFromCache(cacheFile: File, tmpDir: UniFile, filename: String): UniFile {
        val tmpFile = tmpDir.createFile("$filename.tmp")!!
        cacheFile.inputStream().use { input ->
            tmpFile.openOutputStream().use { output ->
                input.copyTo(output)
            }
        }
        val extension = ImageUtil.findImageType(cacheFile.inputStream())
        if (extension != null) {
            tmpFile.renameTo("$filename.${extension.extension}")
        }
        cacheFile.delete()
        return tmpFile
    }

    /**
     * Checks if the download was successful.
     *
     * @param download the download to check.
     * @param audiobookDir the audiobook directory of the download.
     * @param tmpDir the directory where the download is currently stored.
     * @param dirname the real (non temporary) directory name of the download.
     */
    private fun ensureSuccessfulAudiobookDownload(
        download: AudiobookDownload,
        audiobookDir: UniFile,
        tmpDir: UniFile,
        dirname: String,
    ) {
        // Ensure that the chapter folder has the full audio
        val downloadedAudio = tmpDir.listFiles().orEmpty().filterNot { it.extension == ".tmp" }

        download.status = if (downloadedAudio.size == 1) {
            // Only rename the directory if it's downloaded
            tmpDir.renameTo(dirname)
            cache.addChapter(dirname, audiobookDir, download.audiobook)

            DiskUtil.createNoMediaFile(tmpDir, context)
            AudiobookDownload.State.DOWNLOADED
        } else {
            AudiobookDownload.State.ERROR
        }
    }

    /**
     * Returns true if all the queued downloads are in DOWNLOADED or ERROR state.
     */
    private fun areAllAudiobookDownloadsFinished(): Boolean {
        return queueState.value.none { it.status.value <= AudiobookDownload.State.DOWNLOADING.value }
    }

    private val progressSubject = PublishSubject.create<AudiobookDownload>()

    private fun setProgressFor(download: AudiobookDownload) {
        if (download.status == AudiobookDownload.State.DOWNLOADED || download.status == AudiobookDownload.State.ERROR) {
            setProgressSubject(download.audio, null)
        }
    }

    private fun setProgressSubject(audio: Audio?, subject: PublishSubject<Audio.State>?) {
        audio?.progressSubject = subject
    }

    private fun addAllToQueue(downloads: List<AudiobookDownload>) {
        _queueState.update {
            downloads.forEach { download ->
                download.progressSubject = progressSubject
                download.progressCallback = ::setProgressFor
                download.status = AudiobookDownload.State.QUEUE
            }
            store.addAll(downloads)
            it + downloads
        }
    }

    private fun removeFromQueue(download: AudiobookDownload) {
        _queueState.update {
            store.remove(download)
            download.progressSubject = null
            download.progressCallback = null
            if (download.status == AudiobookDownload.State.DOWNLOADING || download.status == AudiobookDownload.State.QUEUE) {
                download.status = AudiobookDownload.State.NOT_DOWNLOADED
            }
            it - download
        }
    }

    private inline fun removeFromQueueIf(predicate: (AudiobookDownload) -> Boolean) {
        _queueState.update { queue ->
            val downloads = queue.filter { predicate(it) }
            store.removeAll(downloads)
            downloads.forEach { download ->
                if (download.status == AudiobookDownload.State.DOWNLOADING ||
                    download.status == AudiobookDownload.State.QUEUE
                ) {
                    download.status = AudiobookDownload.State.NOT_DOWNLOADED
                }
            }
            queue - downloads.toSet()
        }
    }

    fun removeFromQueue(chapters: List<Chapter>) {
        val chapterIds = chapters.map { it.id }
        removeFromQueueIf { it.chapter.id in chapterIds }
    }

    fun removeFromQueue(audiobook: Audiobook) {
        removeFromQueueIf { it.audiobook.id == audiobook.id }
    }

    private fun _clearQueue() {
        _queueState.update {
            it.forEach { download ->
                download.progressSubject = null
                download.progressCallback = null
                if (download.status == AudiobookDownload.State.DOWNLOADING ||
                    download.status == AudiobookDownload.State.QUEUE
                ) {
                    download.status = AudiobookDownload.State.NOT_DOWNLOADED
                }
            }
            store.clear()
            emptyList()
        }
    }

    fun updateQueue(downloads: List<AudiobookDownload>) {
        if (queueState == downloads) return
        val wasRunning = isRunning

        if (downloads.isEmpty()) {
            clearQueue()
            stop()
            return
        }

        pause()
        _clearQueue()
        addAllToQueue(downloads)

        if (wasRunning) {
            start()
        }
    }

    companion object {
        const val TMP_DIR_SUFFIX = "_tmp"
        const val WARNING_NOTIF_TIMEOUT_MS = 30_000L
        const val CHAPTERS_PER_SOURCE_QUEUE_WARNING_THRESHOLD = 10
        private const val DOWNLOADS_QUEUED_WARNING_THRESHOLD = 20
    }
}

// Arbitrary minimum required space to start a download: 200 MB
private const val MIN_DISK_SPACE = 200L * 1024 * 1024
