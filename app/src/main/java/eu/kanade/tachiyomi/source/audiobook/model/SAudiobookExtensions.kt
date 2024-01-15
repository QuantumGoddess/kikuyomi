package eu.kanade.tachiyomi.source.audiobook.model

import dataaudiobook.Audiobooks
import tachiyomi.domain.entries.audiobook.model.Audiobook

fun Audiobook.copyFrom(other: Audiobooks): Audiobook {
    var audiobook = this
    other.author?.let { audiobook = audiobook.copy(author = it) }
    other.artist?.let { audiobook = audiobook.copy(artist = it) }
    other.description?.let { audiobook = audiobook.copy(description = it) }
    other.genre?.let { audiobook = audiobook.copy(genre = it) }
    other.thumbnail_url?.let { audiobook = audiobook.copy(thumbnailUrl = it) }
    audiobook = audiobook.copy(status = other.status)
    if (!initialized) {
        audiobook = audiobook.copy(initialized = other.initialized)
    }
    return audiobook
}
