package eu.kanade.tachiyomi.data.library.audiobook

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkQuery
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import eu.kanade.domain.entries.audiobook.interactor.UpdateAudiobook
import eu.kanade.domain.entries.audiobook.model.toSAudiobook
import eu.kanade.domain.items.audiobookchapter.interactor.SyncChaptersWithSource
import eu.kanade.tachiyomi.audiobooksource.UnmeteredSource
import eu.kanade.tachiyomi.audiobooksource.model.AudiobookUpdateStrategy
import eu.kanade.tachiyomi.audiobooksource.model.SAudiobook
import eu.kanade.tachiyomi.data.cache.AudiobookCoverCache
import eu.kanade.tachiyomi.data.download.audiobook.AudiobookDownloadManager
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.shouldDownloadNewChapters
import eu.kanade.tachiyomi.util.storage.getUriCompat
import eu.kanade.tachiyomi.util.system.createFileInCacheDir
import eu.kanade.tachiyomi.util.system.isConnectedToWifi
import eu.kanade.tachiyomi.util.system.isRunning
import eu.kanade.tachiyomi.util.system.workManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import logcat.LogPriority
import tachiyomi.core.i18n.stringResource
import tachiyomi.core.preference.getAndSet
import tachiyomi.core.util.lang.withIOContext
import tachiyomi.core.util.system.logcat
import tachiyomi.domain.category.audiobook.interactor.GetAudiobookCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.entries.audiobook.interactor.AudiobookFetchInterval
import tachiyomi.domain.entries.audiobook.interactor.GetAudiobook
import tachiyomi.domain.entries.audiobook.interactor.GetLibraryAudiobook
import tachiyomi.domain.entries.audiobook.model.Audiobook
import tachiyomi.domain.items.audiobookchapter.model.Chapter
import tachiyomi.domain.items.audiobookchapter.model.NoChaptersException
import tachiyomi.domain.library.audiobook.LibraryAudiobook
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.library.service.LibraryPreferences.Companion.DEVICE_CHARGING
import tachiyomi.domain.library.service.LibraryPreferences.Companion.DEVICE_NETWORK_NOT_METERED
import tachiyomi.domain.library.service.LibraryPreferences.Companion.DEVICE_ONLY_ON_WIFI
import tachiyomi.domain.library.service.LibraryPreferences.Companion.ENTRY_HAS_UNVIEWED
import tachiyomi.domain.library.service.LibraryPreferences.Companion.ENTRY_NON_COMPLETED
import tachiyomi.domain.library.service.LibraryPreferences.Companion.ENTRY_NON_VIEWED
import tachiyomi.domain.library.service.LibraryPreferences.Companion.ENTRY_OUTSIDE_RELEASE_PERIOD
import tachiyomi.domain.source.audiobook.model.AudiobookSourceNotInstalledException
import tachiyomi.domain.source.audiobook.service.AudiobookSourceManager
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.time.Instant
import java.time.ZonedDateTime
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class AudiobookLibraryUpdateJob(private val context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    private val sourceManager: AudiobookSourceManager = Injekt.get()
    private val downloadPreferences: DownloadPreferences = Injekt.get()
    private val libraryPreferences: LibraryPreferences = Injekt.get()
    private val downloadManager: AudiobookDownloadManager = Injekt.get()
    private val coverCache: AudiobookCoverCache = Injekt.get()
    private val getLibraryAudiobook: GetLibraryAudiobook = Injekt.get()
    private val getAudiobook: GetAudiobook = Injekt.get()
    private val updateAudiobook: UpdateAudiobook = Injekt.get()
    private val getCategories: GetAudiobookCategories = Injekt.get()
    private val syncChaptersWithSource: SyncChaptersWithSource = Injekt.get()
    private val audiobookFetchInterval: AudiobookFetchInterval = Injekt.get()

    private val notifier = AudiobookLibraryUpdateNotifier(context)

    private var audiobookToUpdate: List<LibraryAudiobook> = mutableListOf()

    override suspend fun doWork(): Result {
        if (tags.contains(WORK_NAME_AUTO)) {
            val preferences = Injekt.get<LibraryPreferences>()
            val restrictions = preferences.autoUpdateDeviceRestrictions().get()
            if ((DEVICE_ONLY_ON_WIFI in restrictions) && !context.isConnectedToWifi()) {
                return Result.failure()
            }

            // Find a running manual worker. If exists, try again later
            if (context.workManager.isRunning(WORK_NAME_MANUAL)) {
                return Result.retry()
            }
        }

        try {
            setForeground(getForegroundInfo())
        } catch (e: IllegalStateException) {
            logcat(LogPriority.ERROR, e) { "Not allowed to set foreground job" }
        }

        libraryPreferences.lastUpdatedTimestamp().set(Instant.now().toEpochMilli())

        val categoryId = inputData.getLong(KEY_CATEGORY, -1L)
        addAudiobookToQueue(categoryId)

        return withIOContext {
            try {
                updateChapterList()
                Result.success()
            } catch (e: Exception) {
                if (e is CancellationException) {
                    // Assume success although cancelled
                    Result.success()
                } else {
                    logcat(LogPriority.ERROR, e)
                    Result.failure()
                }
            } finally {
                notifier.cancelProgressNotification()
            }
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notifier = AudiobookLibraryUpdateNotifier(context)
        return ForegroundInfo(
            Notifications.ID_LIBRARY_PROGRESS,
            notifier.progressNotificationBuilder.build(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },

        )
    }

    /**
     * Adds list of audiobook to be updated.
     *
     * @param categoryId the ID of the category to update, or -1 if no category specified.
     */
    private fun addAudiobookToQueue(categoryId: Long) {
        val libraryAudiobook = runBlocking { getLibraryAudiobook.await() }

        val listToUpdate = if (categoryId != -1L) {
            libraryAudiobook.filter { it.category == categoryId }
        } else {
            val categoriesToUpdate = libraryPreferences.audiobookUpdateCategories().get().map { it.toLong() }
            val includedAudiobook = if (categoriesToUpdate.isNotEmpty()) {
                libraryAudiobook.filter { it.category in categoriesToUpdate }
            } else {
                libraryAudiobook
            }

            val categoriesToExclude = libraryPreferences.audiobookUpdateCategoriesExclude().get().map { it.toLong() }
            val excludedAudiobookIds = if (categoriesToExclude.isNotEmpty()) {
                libraryAudiobook.filter { it.category in categoriesToExclude }.map { it.audiobook.id }
            } else {
                emptyList()
            }

            includedAudiobook
                .filterNot { it.audiobook.id in excludedAudiobookIds }
                .distinctBy { it.audiobook.id }
        }

        val restrictions = libraryPreferences.autoUpdateItemRestrictions().get()
        val skippedUpdates = mutableListOf<Pair<Audiobook, String?>>()
        val fetchWindow = audiobookFetchInterval.getWindow(ZonedDateTime.now())

        audiobookToUpdate = listToUpdate
            .filter {
                when {
                    it.audiobook.updateStrategy != AudiobookUpdateStrategy.ALWAYS_UPDATE -> {
                        skippedUpdates.add(
                            it.audiobook to context.stringResource(MR.strings.skipped_reason_not_always_update),
                        )
                        false
                    }

                    ENTRY_NON_COMPLETED in restrictions && it.audiobook.status.toInt() == SAudiobook.COMPLETED -> {
                        skippedUpdates.add(
                            it.audiobook to context.stringResource(MR.strings.skipped_reason_completed),
                        )
                        false
                    }

                    ENTRY_HAS_UNVIEWED in restrictions && it.unreadCount != 0L -> {
                        skippedUpdates.add(
                            it.audiobook to context.stringResource(MR.strings.skipped_reason_not_caught_up),
                        )
                        false
                    }

                    ENTRY_NON_VIEWED in restrictions && it.totalChapters > 0L && !it.hasStarted -> {
                        skippedUpdates.add(
                            it.audiobook to context.stringResource(MR.strings.skipped_reason_not_started),
                        )
                        false
                    }

                    ENTRY_OUTSIDE_RELEASE_PERIOD in restrictions && it.audiobook.nextUpdate > fetchWindow.second -> {
                        skippedUpdates.add(
                            it.audiobook to context.stringResource(MR.strings.skipped_reason_not_in_release_period),
                        )
                        false
                    }
                    else -> true
                }
            }
            .sortedBy { it.audiobook.title }

        // Warn when excessively checking a single source
        val maxUpdatesFromSource = audiobookToUpdate
            .groupBy { it.audiobook.source }
            .filterKeys { sourceManager.get(it) !is UnmeteredSource }
            .maxOfOrNull { it.value.size } ?: 0
        if (maxUpdatesFromSource > AUDIOBOOK_PER_SOURCE_QUEUE_WARNING_THRESHOLD) {
            notifier.showQueueSizeWarningNotification()
        }

        if (skippedUpdates.isNotEmpty()) {
            // TODO: surface skipped reasons to user?
            logcat {
                skippedUpdates
                    .groupBy { it.second }
                    .map { (reason, entries) -> "$reason: [${entries.map { it.first.title }.sorted().joinToString()}]" }
                    .joinToString()
            }
            notifier.showUpdateSkippedNotification(skippedUpdates.size)
        }
    }

    /**
     * Method that updates audiobook in [audiobookToUpdate]. It's called in a background thread, so it's safe
     * to do heavy operations or network calls here.
     * For each audiobook it calls [updateAudiobook] and updates the notification showing the current
     * progress.
     *
     * @return an observable delivering the progress of each update.
     */
    private suspend fun updateChapterList() {
        val semaphore = Semaphore(5)
        val progressCount = AtomicInteger(0)
        val currentlyUpdatingAudiobook = CopyOnWriteArrayList<Audiobook>()
        val newUpdates = CopyOnWriteArrayList<Pair<Audiobook, Array<Chapter>>>()
        val failedUpdates = CopyOnWriteArrayList<Pair<Audiobook, String?>>()
        val hasDownloads = AtomicBoolean(false)
        val fetchWindow = audiobookFetchInterval.getWindow(ZonedDateTime.now())

        coroutineScope {
            audiobookToUpdate.groupBy { it.audiobook.source }.values
                .map { audiobookInSource ->
                    async {
                        semaphore.withPermit {
                            audiobookInSource.forEach { libraryAudiobook ->
                                val audiobook = libraryAudiobook.audiobook
                                ensureActive()

                                // Don't continue to update if audiobook is not in library
                                if (getAudiobook.await(audiobook.id)?.favorite != true) {
                                    return@forEach
                                }

                                withUpdateNotification(
                                    currentlyUpdatingAudiobook,
                                    progressCount,
                                    audiobook,
                                ) {
                                    try {
                                        val newChapters = updateAudiobook(audiobook, fetchWindow)
                                            .sortedByDescending { it.sourceOrder }

                                        if (newChapters.isNotEmpty()) {
                                            val categoryIds = getCategories.await(audiobook.id).map { it.id }
                                            if (audiobook.shouldDownloadNewChapters(categoryIds, downloadPreferences)) {
                                                downloadChapters(audiobook, newChapters)
                                                hasDownloads.set(true)
                                            }

                                            libraryPreferences.newAudiobookUpdatesCount()
                                                .getAndSet { it + newChapters.size }

                                            // Convert to the manga that contains new chapters
                                            newUpdates.add(audiobook to newChapters.toTypedArray())
                                        }
                                    } catch (e: Throwable) {
                                        val errorMessage = when (e) {
                                            is NoChaptersException -> context.stringResource(
                                                MR.strings.no_chapters_error,
                                            )
                                            // failedUpdates will already have the source, don't need to copy it into the message
                                            is AudiobookSourceNotInstalledException -> context.stringResource(
                                                MR.strings.loader_not_implemented_error,
                                            )
                                            else -> e.message
                                        }
                                        failedUpdates.add(audiobook to errorMessage)
                                    }
                                }
                            }
                        }
                    }
                }
                .awaitAll()
        }

        notifier.cancelProgressNotification()

        if (newUpdates.isNotEmpty()) {
            notifier.showUpdateNotifications(newUpdates)
            if (hasDownloads.get()) {
                downloadManager.startDownloads()
            }
        }

        if (failedUpdates.isNotEmpty()) {
            val errorFile = writeErrorFile(failedUpdates)
            notifier.showUpdateErrorNotification(
                failedUpdates.size,
                errorFile.getUriCompat(context),
            )
        }
    }

    private fun downloadChapters(audiobook: Audiobook, chapters: List<Chapter>) {
        // We don't want to start downloading while the library is updating, because websites
        // may don't like it and they could ban the user.
        downloadManager.downloadChapters(audiobook, chapters, false)
    }

    /**
     * Updates the chapters for the given audiobook and adds them to the database.
     *
     * @param audiobook the audiobook to update.
     * @return a pair of the inserted and removed chapters.
     */
    private suspend fun updateAudiobook(audiobook: Audiobook, fetchWindow: Pair<Long, Long>): List<Chapter> {
        val source = sourceManager.getOrStub(audiobook.source)

        // Update audiobook metadata if needed
        if (libraryPreferences.autoUpdateMetadata().get()) {
            val networkAudiobook = source.getAudiobookDetails(audiobook.toSAudiobook())
            updateAudiobook.awaitUpdateFromSource(audiobook, networkAudiobook, manualFetch = false, coverCache)
        }

        val chapters = source.getChapterList(audiobook.toSAudiobook())

        // Get audiobook from database to account for if it was removed during the update and
        // to get latest data so it doesn't get overwritten later on
        val dbAudiobook = getAudiobook.await(audiobook.id)?.takeIf { it.favorite } ?: return emptyList()

        return syncChaptersWithSource.await(chapters, dbAudiobook, source, false, fetchWindow)
    }

    private suspend fun withUpdateNotification(
        updatingAudiobook: CopyOnWriteArrayList<Audiobook>,
        completed: AtomicInteger,
        audiobook: Audiobook,
        block: suspend () -> Unit,
    ) = coroutineScope {
        ensureActive()

        updatingAudiobook.add(audiobook)
        notifier.showProgressNotification(
            updatingAudiobook,
            completed.get(),
            audiobookToUpdate.size,
        )

        block()

        ensureActive()

        updatingAudiobook.remove(audiobook)
        completed.getAndIncrement()
        notifier.showProgressNotification(
            updatingAudiobook,
            completed.get(),
            audiobookToUpdate.size,
        )
    }

    /**
     * Writes basic file of update errors to cache dir.
     */
    private fun writeErrorFile(errors: List<Pair<Audiobook, String?>>): File {
        try {
            if (errors.isNotEmpty()) {
                val file = context.createFileInCacheDir("aniyomi_update_errors.txt")
                file.bufferedWriter().use { out ->
                    out.write(
                        context.stringResource(MR.strings.library_errors_help, ERROR_LOG_HELP_URL) + "\n\n",
                    )
                    // Error file format:
                    // ! Error
                    //   # Source
                    //     - Audiobook
                    errors.groupBy({ it.second }, { it.first }).forEach { (error, audiobooks) ->
                        out.write("\n! ${error}\n")
                        audiobooks.groupBy { it.source }.forEach { (srcId, audiobooks) ->
                            val source = sourceManager.getOrStub(srcId)
                            out.write("  # $source\n")
                            audiobooks.forEach {
                                out.write("    - ${it.title}\n")
                            }
                        }
                    }
                }
                return file
            }
        } catch (_: Exception) {}
        return File("")
    }

    companion object {
        private const val TAG = "AudiobookLibraryUpdate"
        private const val WORK_NAME_AUTO = "AudiobookLibraryUpdate-auto"
        private const val WORK_NAME_MANUAL = "AudiobookLibraryUpdate-manual"

        private const val ERROR_LOG_HELP_URL = "https://aniyomi.org/docs/guides/troubleshooting/"

        private const val AUDIOBOOK_PER_SOURCE_QUEUE_WARNING_THRESHOLD = 60

        /**
         * Key for category to update.
         */
        private const val KEY_CATEGORY = "audiobookCategory"

        fun cancelAllWorks(context: Context) {
            context.workManager.cancelAllWorkByTag(TAG)
        }

        fun setupTask(
            context: Context,
            prefInterval: Int? = null,
        ) {
            val preferences = Injekt.get<LibraryPreferences>()
            val interval = prefInterval ?: preferences.autoUpdateInterval().get()
            if (interval > 0) {
                val restrictions = preferences.autoUpdateDeviceRestrictions().get()
                val constraints = Constraints(
                    requiredNetworkType = if (DEVICE_NETWORK_NOT_METERED in restrictions) {
                        NetworkType.UNMETERED
                    } else { NetworkType.CONNECTED },
                    requiresCharging = DEVICE_CHARGING in restrictions,
                    requiresBatteryNotLow = true,
                )

                val request = PeriodicWorkRequestBuilder<AudiobookLibraryUpdateJob>(
                    interval.toLong(),
                    TimeUnit.HOURS,
                    10,
                    TimeUnit.MINUTES,
                )
                    .addTag(TAG)
                    .addTag(WORK_NAME_AUTO)
                    .setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.MINUTES)
                    .build()

                context.workManager.enqueueUniquePeriodicWork(
                    WORK_NAME_AUTO,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    request,
                )
            } else {
                context.workManager.cancelUniqueWork(WORK_NAME_AUTO)
            }
        }
        fun startNow(
            context: Context,
            category: Category? = null,
        ): Boolean {
            val wm = context.workManager
            if (wm.isRunning(TAG)) {
                // Already running either as a scheduled or manual job
                return false
            }

            val inputData = workDataOf(
                KEY_CATEGORY to category?.id,
            )
            val request = OneTimeWorkRequestBuilder<AudiobookLibraryUpdateJob>()
                .addTag(TAG)
                .addTag(WORK_NAME_MANUAL)
                .setInputData(inputData)
                .build()
            wm.enqueueUniqueWork(WORK_NAME_MANUAL, ExistingWorkPolicy.KEEP, request)

            return true
        }

        fun stop(context: Context) {
            val wm = context.workManager
            val workQuery = WorkQuery.Builder.fromTags(listOf(TAG))
                .addStates(listOf(WorkInfo.State.RUNNING))
                .build()
            wm.getWorkInfos(workQuery).get()
                // Should only return one work but just in case
                .forEach {
                    wm.cancelWorkById(it.id)

                    // Re-enqueue cancelled scheduled work
                    if (it.tags.contains(WORK_NAME_AUTO)) {
                        setupTask(context)
                    }
                }
        }
    }
}
