package eu.kanade.tachiyomi.data.database.models.audiobook

import eu.kanade.tachiyomi.audiobooksource.model.SChapter
import java.io.Serializable
import tachiyomi.domain.items.audiobookchapter.model.Chapter as DomainChapter

interface Chapter : SChapter, Serializable {

    var id: Long?

    var audiobook_id: Long?

    var read: Boolean

    var bookmark: Boolean

    var last_second_read: Long

    var total_seconds: Long

    var date_fetch: Long

    var source_order: Int

    var last_modified: Long
}

fun Chapter.toDomainChapter(): DomainChapter? {
    if (id == null || audiobook_id == null) return null
    return DomainChapter(
        id = id!!,
        audiobookId = audiobook_id!!,
        read = read,
        bookmark = bookmark,
        lastSecondRead = last_second_read,
        totalSeconds = total_seconds,
        dateFetch = date_fetch,
        sourceOrder = source_order.toLong(),
        url = url,
        name = name,
        dateUpload = date_upload,
        chapterNumber = chapter_number.toDouble(),
        scanlator = scanlator,
        lastModifiedAt = last_modified,
    )
}
