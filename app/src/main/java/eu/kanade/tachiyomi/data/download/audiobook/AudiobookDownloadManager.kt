package eu.kanade.tachiyomi.data.download.audiobook

import android.content.Context
import eu.kanade.tachiyomi.audiobooksource.AudiobookSource
import eu.kanade.tachiyomi.audiobooksource.model.Audio
import eu.kanade.tachiyomi.data.download.audiobook.model.AudiobookDownload
import eu.kanade.tachiyomi.util.size
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.runBlocking
import logcat.LogPriority
import tachiyomi.core.i18n.stringResource
import tachiyomi.core.util.lang.launchIO
import tachiyomi.core.util.system.logcat
import tachiyomi.domain.category.audiobook.interactor.GetAudiobookCategories
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.entries.audiobook.model.Audiobook
import tachiyomi.domain.items.audiobookchapter.model.Chapter
import tachiyomi.domain.source.audiobook.service.AudiobookSourceManager
import tachiyomi.domain.storage.service.StorageManager
import tachiyomi.i18n.MR
import tachiyomi.source.local.entries.audiobook.LocalAudiobookSource
import tachiyomi.source.local.io.ArchiveAudiobook
import tachiyomi.source.local.io.audiobook.LocalAudiobookSourceFileSystem
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * This class is used to manage chapter downloads in the application. It must be instantiated once
 * and retrieved through dependency injection. You can use this class to queue new chapters or query
 * downloaded chapters.
 */
class AudiobookDownloadManager(
    private val context: Context,
    private val storageManager: StorageManager = Injekt.get(),
    private val provider: AudiobookDownloadProvider = Injekt.get(),
    private val cache: AudiobookDownloadCache = Injekt.get(),
    private val getCategories: GetAudiobookCategories = Injekt.get(),
    private val sourceManager: AudiobookSourceManager = Injekt.get(),
    private val downloadPreferences: DownloadPreferences = Injekt.get(),
) {

    /**
     * Downloader whose only task is to download chapters.
     */
    private val downloader = AudiobookDownloader(context, provider, cache, sourceManager)

    val isRunning: Boolean
        get() = downloader.isRunning

    /**
     * Queue to delay the deletion of a list of chapters until triggered.
     */
    private val pendingDeleter = AudiobookDownloadPendingDeleter(context)

    val queueState
        get() = downloader.queueState

    // For use by DownloadService only
    fun downloaderStart() = downloader.start()
    fun downloaderStop(reason: String? = null) = downloader.stop(reason)

    val isDownloaderRunning
        get() = AudiobookDownloadJob.isRunningFlow(context)

    /**
     * Tells the downloader to begin downloads.
     */
    fun startDownloads() {
        if (downloader.isRunning) return

        if (AudiobookDownloadJob.isRunning(context)) {
            downloader.start()
        } else {
            AudiobookDownloadJob.start(context)
        }
    }

    /**
     * Tells the downloader to pause downloads.
     */
    fun pauseDownloads() {
        downloader.pause()
        downloader.stop()
    }

    /**
     * Empties the download queue.
     */
    fun clearQueue() {
        downloader.clearQueue()
        downloader.stop()
    }

    /**
     * Returns the download from queue if the chapter is queued for download
     * else it will return null which means that the chapter is not queued for download
     *
     * @param chapterId the chapter to check.
     */
    fun getQueuedDownloadOrNull(chapterId: Long): AudiobookDownload? {
        return queueState.value.find { it.chapter.id == chapterId }
    }

    fun startDownloadNow(chapterId: Long) {
        val existingDownload = getQueuedDownloadOrNull(chapterId)
        // If not in queue try to start a new download
        val toAdd = existingDownload ?: runBlocking { AudiobookDownload.fromChapterId(chapterId) } ?: return
        queueState.value.toMutableList().apply {
            existingDownload?.let { remove(it) }
            add(0, toAdd)
            reorderQueue(this)
        }
        startDownloads()
    }

    /**
     * Reorders the download queue.
     *
     * @param downloads value to set the download queue to
     */
    fun reorderQueue(downloads: List<AudiobookDownload>) {
        downloader.updateQueue(downloads)
    }

    /**
     * Tells the downloader to enqueue the given list of chapters.
     *
     * @param audiobook the audiobook of the chapters.
     * @param chapters the list of chapters to enqueue.
     * @param autoStart whether to start the downloader after enqueuing the chapters.
     * @param alt whether to use the alternative downloader
     */
    fun downloadChapters(
        audiobook: Audiobook,
        chapters: List<Chapter>,
        autoStart: Boolean = true,
        alt: Boolean = false,
        audio: Audio? = null,
    ) {
        downloader.queueChapters(audiobook, chapters, autoStart, alt, audio)
    }

    /**
     * Tells the downloader to enqueue the given list of downloads at the start of the queue.
     *
     * @param downloads the list of downloads to enqueue.
     */
    fun addDownloadsToStartOfQueue(downloads: List<AudiobookDownload>) {
        if (downloads.isEmpty()) return
        queueState.value.toMutableList().apply {
            addAll(0, downloads)
            reorderQueue(this)
        }
        if (!AudiobookDownloadJob.isRunning(context)) startDownloads()
    }

    /**
     * Builds the page list of a downloaded chapter.
     *
     * @param source the source of the chapter.
     * @param audiobook the audiobook of the chapter.
     * @param chapter the downloaded chapter.
     * @return an observable containing the list of pages from the chapter.
     */
    fun buildAudio(source: AudiobookSource, audiobook: Audiobook, chapter: Chapter): Audio {
        val chapterDir =
            provider.findChapterDir(chapter.name, chapter.scanlator, audiobook.title, source)
        val files = chapterDir?.listFiles().orEmpty()
            .filter { "audio" in it.type.orEmpty() }

        if (files.isEmpty()) {
            throw Exception(context.stringResource(MR.strings.audio_list_empty_error))
        }

        val file = files[0]

        return Audio(
            file.uri.toString(),
            "download: " + file.uri.toString(),
            file.uri.toString(),
            file.uri,
        ).apply { status = Audio.State.READY }
    }

    /**
     * Returns true if the chapter is downloaded.
     *
     * @param chapterName the name of the chapter to query.
     * @param chapterScanlator scanlator of the chapter to query
     * @param audiobookTitle the title of the audiobook to query.
     * @param sourceId the id of the source of the chapter.
     * @param skipCache whether to skip the directory cache and check in the filesystem.
     */
    fun isChapterDownloaded(
        chapterName: String,
        chapterScanlator: String?,
        audiobookTitle: String,
        sourceId: Long,
        skipCache: Boolean = false,
    ): Boolean {
        return cache.isChapterDownloaded(
            chapterName,
            chapterScanlator,
            audiobookTitle,
            sourceId,
            skipCache,
        )
    }

    /**
     * Returns the amount of downloaded chapters.
     */
    fun getDownloadCount(): Int {
        return cache.getTotalDownloadCount()
    }

    /**
     * Returns the amount of downloaded/local chapters for an audiobook.
     *
     * @param audiobook the audiobook to check.
     */
    fun getDownloadCount(audiobook: Audiobook): Int {
        return if (audiobook.source == LocalAudiobookSource.ID) {
            LocalAudiobookSourceFileSystem(storageManager).getFilesInAudiobookDirectory(audiobook.url)
                .count { ArchiveAudiobook.isSupported(it) }
        } else {
            cache.getDownloadCount(audiobook)
        }
    }

    /**
     * Returns the size of downloaded chapters.
     */
    fun getDownloadSize(): Long {
        return cache.getTotalDownloadSize()
    }

    /**
     * Returns the size of downloaded/local chapters for an audiobook.
     *
     * @param audiobook the audiobook to check.
     */
    fun getDownloadSize(audiobook: Audiobook): Long {
        return if (audiobook.source == LocalAudiobookSource.ID) {
            LocalAudiobookSourceFileSystem(storageManager).getAudiobookDirectory(audiobook.url)
                ?.size() ?: 0L
        } else {
            cache.getDownloadSize(audiobook)
        }
    }

    fun cancelQueuedDownloads(downloads: List<AudiobookDownload>) {
        removeFromDownloadQueue(downloads.map { it.chapter })
    }

    /**
     * Deletes the directories of a list of downloaded chapters.
     *
     * @param chapters the list of chapters to delete.
     * @param audiobook the audiobook of the chapters.
     * @param source the source of the chapters.
     */
    fun deleteChapters(chapters: List<Chapter>, audiobook: Audiobook, source: AudiobookSource) {
        launchIO {
            val filteredChapters = getChaptersToDelete(chapters, audiobook)
            if (filteredChapters.isEmpty()) {
                return@launchIO
            }

            removeFromDownloadQueue(filteredChapters)
            val (audiobookDir, chapterDirs) = provider.findChapterDirs(
                filteredChapters,
                audiobook,
                source,
            )
            chapterDirs.forEach { it.delete() }
            cache.removeChapters(filteredChapters, audiobook)

            // Delete audiobook directory if empty
            if (audiobookDir?.listFiles()?.isEmpty() == true) {
                deleteAudiobook(audiobook, source, removeQueued = false)
            }
        }
    }

    /**
     * Deletes the directory of a downloaded audiobook.
     *
     * @param audiobook the audiobook to delete.
     * @param source the source of the audiobook.
     * @param removeQueued whether to also remove queued downloads.
     */
    fun deleteAudiobook(audiobook: Audiobook, source: AudiobookSource, removeQueued: Boolean = true) {
        launchIO {
            if (removeQueued) {
                downloader.removeFromQueue(audiobook)
            }
            provider.findAudiobookDir(audiobook.title, source)?.delete()
            cache.removeAudiobook(audiobook)
            // Delete source directory if empty
            val sourceDir = provider.findSourceDir(source)
            if (sourceDir?.listFiles()?.isEmpty() == true) {
                sourceDir.delete()
                cache.removeSource(source)
            }
        }
    }

    private fun removeFromDownloadQueue(chapters: List<Chapter>) {
        val wasRunning = downloader.isRunning
        if (wasRunning) {
            downloader.pause()
        }

        downloader.removeFromQueue(chapters)

        if (wasRunning) {
            if (queueState.value.isEmpty()) {
                downloader.stop()
            } else if (queueState.value.isNotEmpty()) {
                downloader.start()
            }
        }
    }

    /**
     * Adds a list of chapters to be deleted later.
     *
     * @param chapters the list of chapters to delete.
     * @param audiobook the audiobook of the chapters.
     */
    suspend fun enqueueChaptersToDelete(chapters: List<Chapter>, audiobook: Audiobook) {
        pendingDeleter.addChapters(getChaptersToDelete(chapters, audiobook), audiobook)
    }

    /**
     * Triggers the execution of the deletion of pending chapters.
     */
    fun deletePendingChapters() {
        val pendingChapters = pendingDeleter.getPendingChapters()
        for ((audiobook, chapters) in pendingChapters) {
            val source = sourceManager.get(audiobook.source) ?: continue
            deleteChapters(chapters, audiobook, source)
        }
    }

    /**
     * Renames source download folder
     *
     * @param oldSource the old source.
     * @param newSource the new source.
     */
    fun renameSource(oldSource: AudiobookSource, newSource: AudiobookSource) {
        val oldFolder = provider.findSourceDir(oldSource) ?: return
        val newName = provider.getSourceDirName(newSource)

        if (oldFolder.name == newName) return

        val capitalizationChanged = oldFolder.name.equals(newName, ignoreCase = true)
        if (capitalizationChanged) {
            val tempName = newName + AudiobookDownloader.TMP_DIR_SUFFIX
            if (oldFolder.renameTo(tempName).not()) {
                logcat(LogPriority.ERROR) { "Failed to rename source download folder: ${oldFolder.name}" }
                return
            }
        }

        if (oldFolder.renameTo(newName).not()) {
            logcat(LogPriority.ERROR) { "Failed to rename source download folder: ${oldFolder.name}" }
        }
    }

    /**
     * Renames an already downloaded chapter
     *
     * @param source the source of the audiobook.
     * @param audiobook the audiobook of the chapter.
     * @param oldChapter the existing chapter with the old name.
     * @param newChapter the target chapter with the new name.
     */
    fun renameChapter(source: AudiobookSource, audiobook: Audiobook, oldChapter: Chapter, newChapter: Chapter) {
        val oldNames = provider.getValidChapterDirNames(oldChapter.name, oldChapter.scanlator)
        val audiobookDir = provider.getAudiobookDir(audiobook.title, source)

        // Assume there's only 1 version of the chapter name formats present
        val oldFolder = oldNames.asSequence()
            .mapNotNull { audiobookDir.findFile(it) }
            .firstOrNull()

        val newName = provider.getChapterDirName(newChapter.name, newChapter.scanlator)

        if (oldFolder?.name == newName) return

        if (oldFolder?.renameTo(newName) == true) {
            cache.removeChapter(oldChapter, audiobook)
            cache.addChapter(newName, audiobookDir, audiobook)
        } else {
            logcat(LogPriority.ERROR) { "Could not rename downloaded chapter: ${oldNames.joinToString()}" }
        }
    }

    private suspend fun getChaptersToDelete(chapters: List<Chapter>, audiobook: Audiobook): List<Chapter> {
        // Retrieve the categories that are set to exclude from being deleted on read
        val categoriesToExclude =
            downloadPreferences.removeExcludeAudiobookCategories().get().map(String::toLong)

        val categoriesForAudiobook = getCategories.await(audiobook.id)
            .map { it.id }
            .ifEmpty { listOf(0) }
        val filteredCategoryAudiobook = if (categoriesForAudiobook.intersect(categoriesToExclude).isNotEmpty()) {
            chapters.filterNot { it.read }
        } else {
            chapters
        }

        return if (!downloadPreferences.removeBookmarkedChapters().get()) {
            filteredCategoryAudiobook.filterNot { it.bookmark }
        } else {
            filteredCategoryAudiobook
        }
    }

    fun statusFlow(): Flow<AudiobookDownload> = queueState
        .flatMapLatest { downloads ->
            downloads
                .map { download ->
                    download.statusFlow.drop(1).map { download }
                }
                .merge()
        }
        .onStart {
            emitAll(
                queueState.value.filter { download -> download.status == AudiobookDownload.State.DOWNLOADING }
                    .asFlow(),
            )
        }

    fun progressFlow(): Flow<AudiobookDownload> = queueState
        .flatMapLatest { downloads ->
            downloads
                .map { download ->
                    download.progressFlow.drop(1).map { download }
                }
                .merge()
        }
        .onStart {
            emitAll(
                queueState.value.filter { download -> download.status == AudiobookDownload.State.DOWNLOADING }
                    .asFlow(),
            )
        }
}
