package eu.kanade.tachiyomi.data.database.models.audiobook

class AudiobookTrackImpl : AudiobookTrack {

    override var id: Long? = null

    override var audiobook_id: Long = 0

    override var sync_id: Int = 0

    override var media_id: Long = 0

    override var library_id: Long? = null

    override lateinit var title: String

    override var last_chapter_read: Float = 0F

    override var total_chapters: Int = 0

    override var score: Float = 0f

    override var status: Int = 0

    override var started_listening_date: Long = 0

    override var finished_listening_date: Long = 0

    override var tracking_url: String = ""
}
