package eu.kanade.domain.track.audiobook.service

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import eu.kanade.domain.track.audiobook.interactor.TrackChapter
import eu.kanade.domain.track.audiobook.store.DelayedAudiobookTrackingStore
import eu.kanade.tachiyomi.util.system.workManager
import logcat.LogPriority
import tachiyomi.core.util.lang.withIOContext
import tachiyomi.core.util.system.logcat
import tachiyomi.domain.track.audiobook.interactor.GetAudiobookTracks
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

class DelayedAudiobookTrackingUpdateJob(private val context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        if (runAttemptCount > 3) {
            return Result.failure()
        }

        val getTracks = Injekt.get<GetAudiobookTracks>()
        val trackChapter = Injekt.get<TrackChapter>()

        val delayedTrackingStore = Injekt.get<DelayedAudiobookTrackingStore>()

        withIOContext {
            delayedTrackingStore.getAudiobookItems()
                .mapNotNull {
                    val track = getTracks.awaitOne(it.trackId)
                    if (track == null) {
                        delayedTrackingStore.removeAudiobookItem(it.trackId)
                    }
                    track?.copy(lastChapterRead = it.lastChapterRead.toDouble())
                }
                .forEach { audiobookTrack ->
                    logcat(LogPriority.DEBUG) {
                        "Updating delayed track item: ${audiobookTrack.audiobookId}" +
                            ", last chapter read: ${audiobookTrack.lastChapterRead}"
                    }
                    trackChapter.await(context, audiobookTrack.audiobookId, audiobookTrack.lastChapterRead)
                }
        }

        return if (delayedTrackingStore.getAudiobookItems().isEmpty()) Result.success() else Result.retry()
    }

    companion object {
        private const val TAG = "DelayedTrackingUpdate"

        fun setupTask(context: Context) {
            val constraints = Constraints(
                requiredNetworkType = NetworkType.CONNECTED,
            )

            val request = OneTimeWorkRequestBuilder<DelayedAudiobookTrackingUpdateJob>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5.minutes.toJavaDuration())
                .addTag(TAG)
                .build()

            context.workManager.enqueueUniqueWork(TAG, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
