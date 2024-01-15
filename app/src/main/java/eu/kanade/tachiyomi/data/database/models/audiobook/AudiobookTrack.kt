package eu.kanade.tachiyomi.data.database.models.audiobook

import java.io.Serializable

interface AudiobookTrack : Serializable {

    var id: Long?

    var audiobook_id: Long

    var sync_id: Int

    var media_id: Long

    var library_id: Long?

    var title: String

    var last_chapter_read: Float

    var total_chapters: Int

    var score: Float

    var status: Int

    var started_listening_date: Long

    var finished_listening_date: Long

    var tracking_url: String

    fun copyPersonalFrom(other: AudiobookTrack) {
        last_chapter_read = other.last_chapter_read
        score = other.score
        status = other.status
        started_listening_date = other.started_listening_date
        finished_listening_date = other.finished_listening_date
    }

    companion object {
        fun create(serviceId: Long): AudiobookTrack = AudiobookTrackImpl().apply {
            sync_id = serviceId.toInt()
        }
    }
}
