package eu.kanade.domain.entries.audiobook.model

import eu.kanade.domain.base.BasePreferences
import eu.kanade.tachiyomi.audiobooksource.model.SAudiobook
import eu.kanade.tachiyomi.data.cache.AudiobookCoverCache
import tachiyomi.core.preference.TriState
import tachiyomi.domain.entries.audiobook.model.Audiobook
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// TODO: move these into the domain model
val Audiobook.downloadedFilter: TriState
    get() {
        if (forceDownloaded()) return TriState.ENABLED_IS
        return when (downloadedFilterRaw) {
            Audiobook.CHAPTER_SHOW_DOWNLOADED -> TriState.ENABLED_IS
            Audiobook.CHAPTER_SHOW_NOT_DOWNLOADED -> TriState.ENABLED_NOT
            else -> TriState.DISABLED
        }
    }
fun Audiobook.chaptersFiltered(): Boolean {
    return unreadFilter != TriState.DISABLED ||
        downloadedFilter != TriState.DISABLED ||
        bookmarkedFilter != TriState.DISABLED
}
fun Audiobook.forceDownloaded(): Boolean {
    return favorite && Injekt.get<BasePreferences>().downloadedOnly().get()
}

fun Audiobook.toSAudiobook(): SAudiobook = SAudiobook.create().also {
    it.url = url
    it.title = title
    it.artist = artist
    it.author = author
    it.description = description
    it.genre = genre.orEmpty().joinToString()
    it.status = status.toInt()
    it.thumbnail_url = thumbnailUrl
    it.initialized = initialized
}

fun Audiobook.copyFrom(other: SAudiobook): Audiobook {
    val author = other.author ?: author
    val artist = other.artist ?: artist
    val description = other.description ?: description
    val genres = if (other.genre != null) {
        other.getGenres()
    } else {
        genre
    }
    val thumbnailUrl = other.thumbnail_url ?: thumbnailUrl
    return this.copy(
        author = author,
        artist = artist,
        description = description,
        genre = genres,
        thumbnailUrl = thumbnailUrl,
        status = other.status.toLong(),
        updateStrategy = other.update_strategy,
        initialized = other.initialized && initialized,
    )
}

fun SAudiobook.toDomainAudiobook(sourceId: Long): Audiobook {
    return Audiobook.create().copy(
        url = url,
        title = title,
        artist = artist,
        author = author,
        description = description,
        genre = getGenres(),
        status = status.toLong(),
        thumbnailUrl = thumbnail_url,
        updateStrategy = update_strategy,
        initialized = initialized,
        source = sourceId,
    )
}

fun Audiobook.hasCustomCover(coverCache: AudiobookCoverCache = Injekt.get()): Boolean {
    return coverCache.getCustomCoverFile(id).exists()
}
