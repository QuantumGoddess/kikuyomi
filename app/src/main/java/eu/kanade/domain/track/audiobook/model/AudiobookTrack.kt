package eu.kanade.domain.track.audiobook.model

import tachiyomi.domain.track.audiobook.model.AudiobookTrack
import eu.kanade.tachiyomi.data.database.models.audiobook.AudiobookTrack as DbAudiobookTrack

fun AudiobookTrack.copyPersonalFrom(other: AudiobookTrack): AudiobookTrack {
    return this.copy(
        lastChapterRead = other.lastChapterRead,
        score = other.score,
        status = other.status,
        startDate = other.startDate,
        finishDate = other.finishDate,
    )
}

fun AudiobookTrack.toDbTrack(): DbAudiobookTrack = DbAudiobookTrack.create(syncId).also {
    it.id = id
    it.audiobook_id = audiobookId
    it.media_id = remoteId
    it.library_id = libraryId
    it.title = title
    it.last_chapter_read = lastChapterRead.toFloat()
    it.total_chapters = totalChapters.toInt()
    it.status = status.toInt()
    it.score = score.toFloat()
    it.tracking_url = remoteUrl
    it.started_listening_date = startDate
    it.finished_listening_date = finishDate
}

fun DbAudiobookTrack.toDomainTrack(idRequired: Boolean = true): AudiobookTrack? {
    val trackId = id ?: if (idRequired.not()) -1 else return null
    return AudiobookTrack(
        id = trackId,
        audiobookId = audiobook_id,
        syncId = sync_id.toLong(),
        remoteId = media_id,
        libraryId = library_id,
        title = title,
        lastChapterRead = last_chapter_read.toDouble(),
        totalChapters = total_chapters.toLong(),
        status = status.toLong(),
        score = score.toDouble(),
        remoteUrl = tracking_url,
        startDate = started_listening_date,
        finishDate = finished_listening_date,
    )
}
