package eu.kanade.tachiyomi.data.library.audiobook

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkQuery
import androidx.work.WorkerParameters
import eu.kanade.domain.entries.audiobook.interactor.UpdateAudiobook
import eu.kanade.domain.entries.audiobook.model.copyFrom
import eu.kanade.domain.entries.audiobook.model.toSAudiobook
import eu.kanade.tachiyomi.data.cache.AudiobookCoverCache
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.source.UnmeteredSource
import eu.kanade.tachiyomi.util.prepUpdateCover
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
import tachiyomi.core.util.lang.withIOContext
import tachiyomi.core.util.system.logcat
import tachiyomi.domain.entries.audiobook.interactor.GetLibraryAudiobook
import tachiyomi.domain.entries.audiobook.model.Audiobook
import tachiyomi.domain.entries.audiobook.model.toAudiobookUpdate
import tachiyomi.domain.library.audiobook.LibraryAudiobook
import tachiyomi.domain.source.audiobook.service.AudiobookSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

class AudiobookMetadataUpdateJob(private val context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    private val sourceManager: AudiobookSourceManager = Injekt.get()
    private val coverCache: AudiobookCoverCache = Injekt.get()
    private val getLibraryAudiobook: GetLibraryAudiobook = Injekt.get()
    private val updateAudiobook: UpdateAudiobook = Injekt.get()

    private val notifier = AudiobookLibraryUpdateNotifier(context)

    private var audiobookToUpdate: List<LibraryAudiobook> = mutableListOf()

    override suspend fun doWork(): Result {
        try {
            setForeground(getForegroundInfo())
        } catch (e: IllegalStateException) {
            logcat(LogPriority.ERROR, e) { "Not allowed to set foreground job" }
        }

        addAudiobookToQueue()

        return withIOContext {
            try {
                updateMetadata()
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
     */
    private fun addAudiobookToQueue() {
        audiobookToUpdate = runBlocking { getLibraryAudiobook.await() }

        // Warn when excessively checking a single source
        val maxUpdatesFromSource = audiobookToUpdate
            .groupBy { it.audiobook.source }
            .filterKeys { sourceManager.get(it) !is UnmeteredSource }
            .maxOfOrNull { it.value.size } ?: 0
        if (maxUpdatesFromSource > MANGA_PER_SOURCE_QUEUE_WARNING_THRESHOLD) {
            notifier.showQueueSizeWarningNotification()
        }
    }

    private suspend fun updateMetadata() {
        val semaphore = Semaphore(5)
        val progressCount = AtomicInteger(0)
        val currentlyUpdatingAudiobook = CopyOnWriteArrayList<Audiobook>()

        coroutineScope {
            audiobookToUpdate.groupBy { it.audiobook.source }
                .values
                .map { audiobookInSource ->
                    async {
                        semaphore.withPermit {
                            audiobookInSource.forEach { libraryAudiobook ->
                                val audiobook = libraryAudiobook.audiobook
                                ensureActive()

                                withUpdateNotification(
                                    currentlyUpdatingAudiobook,
                                    progressCount,
                                    audiobook,
                                ) {
                                    val source = sourceManager.get(audiobook.source) ?: return@withUpdateNotification
                                    try {
                                        val networkAudiobook = source.getAudiobookDetails(audiobook.toSAudiobook())
                                        val updatedAudiobook = audiobook.prepUpdateCover(coverCache, networkAudiobook, true)
                                            .copyFrom(networkAudiobook)
                                        try {
                                            updateAudiobook.await(updatedAudiobook.toAudiobookUpdate())
                                        } catch (e: Exception) {
                                            logcat(LogPriority.ERROR) { "Audiobook doesn't exist anymore" }
                                        }
                                    } catch (e: Throwable) {
                                        // Ignore errors and continue
                                        logcat(LogPriority.ERROR, e)
                                    }
                                }
                            }
                        }
                    }
                }
                .awaitAll()
        }

        notifier.cancelProgressNotification()
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

    companion object {
        private const val TAG = "MetadataUpdate"
        private const val WORK_NAME_MANUAL = "MetadataUpdate"

        private const val MANGA_PER_SOURCE_QUEUE_WARNING_THRESHOLD = 60

        fun startNow(context: Context): Boolean {
            val wm = context.workManager
            if (wm.isRunning(TAG)) {
                // Already running either as a scheduled or manual job
                return false
            }
            val request = OneTimeWorkRequestBuilder<AudiobookMetadataUpdateJob>()
                .addTag(TAG)
                .addTag(WORK_NAME_MANUAL)
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
                }
        }
    }
}
