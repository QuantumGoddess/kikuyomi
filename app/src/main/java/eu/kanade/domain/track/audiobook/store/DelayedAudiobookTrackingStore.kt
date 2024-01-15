package eu.kanade.domain.track.audiobook.store

import android.content.Context
import androidx.core.content.edit
import logcat.LogPriority
import tachiyomi.core.util.system.logcat

class DelayedAudiobookTrackingStore(context: Context) {

    /**
     * Preference file where queued tracking updates are stored.
     */
    private val preferences = context.getSharedPreferences("tracking_queue", Context.MODE_PRIVATE)

    fun addAudiobook(trackId: Long, lastChapterRead: Double) {
        val previousLastChapterRead = preferences.getFloat(trackId.toString(), 0f)
        if (lastChapterRead > previousLastChapterRead) {
            logcat(LogPriority.DEBUG) { "Queuing track item: $trackId, last chapter read: $lastChapterRead" }
            preferences.edit {
                putFloat(trackId.toString(), lastChapterRead.toFloat())
            }
        }
    }

    fun removeAudiobookItem(trackId: Long) {
        preferences.edit {
            remove(trackId.toString())
        }
    }

    fun getAudiobookItems(): List<DelayedAudiobookTrackingItem> {
        return preferences.all.mapNotNull {
            DelayedAudiobookTrackingItem(
                trackId = it.key.toLong(),
                lastChapterRead = it.value.toString().toFloat(),
            )
        }
    }

    data class DelayedAudiobookTrackingItem(
        val trackId: Long,
        val lastChapterRead: Float,
    )
}
