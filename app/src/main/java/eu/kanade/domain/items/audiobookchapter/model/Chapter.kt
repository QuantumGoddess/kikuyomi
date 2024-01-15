package eu.kanade.domain.items.audiobookchapter.model

import eu.kanade.tachiyomi.audiobooksource.model.SChapter
import eu.kanade.tachiyomi.data.database.models.audiobook.ChapterImpl
import tachiyomi.domain.items.audiobookchapter.model.Chapter
import eu.kanade.tachiyomi.data.database.models.audiobook.Chapter as DbChapter

// TODO: Remove when all deps are migrated
fun Chapter.toSChapter(): SChapter {
    return SChapter.create().also {
        it.url = url
        it.name = name
        it.date_upload = dateUpload
        it.chapter_number = chapterNumber.toFloat()
        it.scanlator = scanlator
    }
}

fun Chapter.copyFromSChapter(sChapter: SChapter): Chapter {
    return this.copy(
        name = sChapter.name,
        url = sChapter.url,
        dateUpload = sChapter.date_upload,
        chapterNumber = sChapter.chapter_number.toDouble(),
        scanlator = sChapter.scanlator?.ifBlank { null },
    )
}

fun Chapter.toDbChapter(): DbChapter = ChapterImpl().also {
    it.id = id
    it.audiobook_id = audiobookId
    it.url = url
    it.name = name
    it.scanlator = scanlator
    it.read = read
    it.bookmark = bookmark
    it.last_second_read = lastSecondRead
    it.total_seconds = totalSeconds
    it.date_fetch = dateFetch
    it.date_upload = dateUpload
    it.chapter_number = chapterNumber.toFloat()
    it.source_order = sourceOrder.toInt()
}
