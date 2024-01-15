package eu.kanade.tachiyomi.data.download.audiobook

import android.content.Context
import androidx.core.content.edit
import eu.kanade.tachiyomi.audiobooksource.online.AudiobookHttpSource
import eu.kanade.tachiyomi.data.download.audiobook.model.AudiobookDownload
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import tachiyomi.domain.entries.audiobook.interactor.GetAudiobook
import tachiyomi.domain.entries.audiobook.model.Audiobook
import tachiyomi.domain.items.audiobookchapter.interactor.GetChapter
import tachiyomi.domain.source.audiobook.service.AudiobookSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * This class is used to persist active downloads across application restarts.
 */
class AudiobookDownloadStore(
    context: Context,
    private val sourceManager: AudiobookSourceManager = Injekt.get(),
    private val json: Json = Injekt.get(),
    private val getAudiobook: GetAudiobook = Injekt.get(),
    private val getChapter: GetChapter = Injekt.get(),
) {

    /**
     * Preference file where active downloads are stored.
     */
    private val preferences = context.getSharedPreferences("active_downloads", Context.MODE_PRIVATE)

    /**
     * Counter used to keep the queue order.
     */
    private var counter = 0

    /**
     * Adds a list of downloads to the store.
     *
     * @param downloads the list of downloads to add.
     */
    fun addAll(downloads: List<AudiobookDownload>) {
        preferences.edit {
            downloads.forEach { putString(getKey(it), serialize(it)) }
        }
    }

    /**
     * Removes a download from the store.
     *
     * @param download the download to remove.
     */
    fun remove(download: AudiobookDownload) {
        preferences.edit {
            remove(getKey(download))
        }
    }

    /**
     * Removes a list of downloads from the store.
     *
     * @param downloads the download to remove.
     */
    fun removeAll(downloads: List<AudiobookDownload>) {
        preferences.edit {
            downloads.forEach { remove(getKey(it)) }
        }
    }

    /**
     * Removes all the downloads from the store.
     */
    fun clear() {
        preferences.edit {
            clear()
        }
    }

    /**
     * Returns the preference's key for the given download.
     *
     * @param download the download.
     */
    private fun getKey(download: AudiobookDownload): String {
        return download.chapter.id.toString()
    }

    /**
     * Returns the list of downloads to restore. It should be called in a background thread.
     */
    fun restore(): List<AudiobookDownload> {
        val objs = preferences.all
            .mapNotNull { it.value as? String }
            .mapNotNull { deserialize(it) }
            .sortedBy { it.order }

        val downloads = mutableListOf<AudiobookDownload>()
        if (objs.isNotEmpty()) {
            val cachedAudiobook = mutableMapOf<Long, Audiobook?>()
            for ((audiobookId, chapterId) in objs) {
                val audiobook = cachedAudiobook.getOrPut(audiobookId) {
                    runBlocking { getAudiobook.await(audiobookId) }
                } ?: continue
                val source = sourceManager.get(audiobook.source) as? AudiobookHttpSource ?: continue
                val chapter = runBlocking { getChapter.await(chapterId) } ?: continue
                downloads.add(AudiobookDownload(source, audiobook, chapter))
            }
        }

        // Clear the store, downloads will be added again immediately.
        clear()
        return downloads
    }

    /**
     * Converts a download to a string.
     *
     * @param download the download to serialize.
     */
    private fun serialize(download: AudiobookDownload): String {
        val obj = AudiobookDownloadObject(download.audiobook.id, download.chapter.id!!, counter++)
        return json.encodeToString(obj)
    }

    /**
     * Restore a download from a string.
     *
     * @param string the download as string.
     */
    private fun deserialize(string: String): AudiobookDownloadObject? {
        return try {
            json.decodeFromString<AudiobookDownloadObject>(string)
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * Class used for download serialization
 *
 * @param audiobookId the id of the audiobook.
 * @param chapterId the id of the chapter.
 * @param order the order of the download in the queue.
 */
@Serializable
private data class AudiobookDownloadObject(val audiobookId: Long, val chapterId: Long, val order: Int)
