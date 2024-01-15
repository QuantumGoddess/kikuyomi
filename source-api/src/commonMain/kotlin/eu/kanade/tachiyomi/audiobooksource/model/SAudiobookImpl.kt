package eu.kanade.tachiyomi.audiobooksource.model

class SAudiobookImpl : SAudiobook {

    override lateinit var url: String

    override lateinit var title: String

    override var artist: String? = null

    override var author: String? = null

    override var description: String? = null

    override var genre: String? = null

    override var status: Int = 0

    override var thumbnail_url: String? = null

    override var initialized: Boolean = false

    override var update_strategy: AudiobookUpdateStrategy = AudiobookUpdateStrategy.ALWAYS_UPDATE
}
