package eu.kanade.tachiyomi.util

import eu.kanade.domain.entries.audiobook.interactor.UpdateAudiobook
import eu.kanade.domain.entries.audiobook.model.hasCustomCover
import eu.kanade.domain.entries.audiobook.model.toSAudiobook
import eu.kanade.tachiyomi.audiobooksource.model.SAudiobook
import eu.kanade.tachiyomi.data.cache.AudiobookCoverCache
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.entries.audiobook.model.Audiobook
import tachiyomi.source.local.entries.audiobook.isLocal
import tachiyomi.source.local.image.audiobook.LocalAudiobookCoverManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.InputStream
import java.time.Instant

/**
 * Call before updating [Audiobook.thumbnail_url] to ensure old cover can be cleared from cache
 */
fun Audiobook.prepUpdateCover(coverCache: AudiobookCoverCache, remoteAudiobook: SAudiobook, refreshSameUrl: Boolean): Audiobook {
    // Never refresh covers if the new url is null, as the current url has possibly become invalid
    val newUrl = remoteAudiobook.thumbnail_url ?: return this

    // Never refresh covers if the url is empty to avoid "losing" existing covers
    if (newUrl.isEmpty()) return this

    if (!refreshSameUrl && thumbnailUrl == newUrl) return this

    return when {
        isLocal() -> {
            this.copy(coverLastModified = Instant.now().toEpochMilli())
        }
        hasCustomCover(coverCache) -> {
            coverCache.deleteFromCache(this, false)
            this
        }
        else -> {
            coverCache.deleteFromCache(this, false)
            this.copy(coverLastModified = Instant.now().toEpochMilli())
        }
    }
}

fun Audiobook.removeCovers(coverCache: AudiobookCoverCache = Injekt.get()): Audiobook {
    if (isLocal()) return this
    return if (coverCache.deleteFromCache(this, true) > 0) {
        return copy(coverLastModified = Instant.now().toEpochMilli())
    } else {
        this
    }
}

fun Audiobook.shouldDownloadNewChapters(dbCategories: List<Long>, preferences: DownloadPreferences): Boolean {
    if (!favorite) return false

    val categories = dbCategories.ifEmpty { listOf(0L) }

    // Boolean to determine if user wants to automatically download new chapters.
    val downloadNewChapters = preferences.downloadNewChapters().get()
    if (!downloadNewChapters) return false

    val includedCategories = preferences.downloadNewChapterCategories().get().map { it.toLong() }
    val excludedCategories = preferences.downloadNewChapterCategoriesExclude().get().map { it.toLong() }

    // Default: Download from all categories
    if (includedCategories.isEmpty() && excludedCategories.isEmpty()) return true

    // In excluded category
    if (categories.any { it in excludedCategories }) return false

    // Included category not selected
    if (includedCategories.isEmpty()) return true

    // In included category
    return categories.any { it in includedCategories }
}

suspend fun Audiobook.editCover(
    coverManager: LocalAudiobookCoverManager,
    stream: InputStream,
    updateAudiobook: UpdateAudiobook = Injekt.get(),
    coverCache: AudiobookCoverCache = Injekt.get(),
) {
    if (isLocal()) {
        coverManager.update(toSAudiobook(), stream)
        updateAudiobook.awaitUpdateCoverLastModified(id)
    } else if (favorite) {
        coverCache.setCustomCoverToCache(this, stream)
        updateAudiobook.awaitUpdateCoverLastModified(id)
    }
}
