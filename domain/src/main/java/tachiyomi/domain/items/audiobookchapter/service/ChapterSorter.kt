package tachiyomi.domain.items.audiobookchapter.service

import tachiyomi.core.util.lang.compareToWithCollator
import tachiyomi.domain.entries.audiobook.model.Audiobook
import tachiyomi.domain.items.audiobookchapter.model.Chapter

fun getChapterSort(audiobook: Audiobook, sortDescending: Boolean = audiobook.sortDescending()): (
    Chapter,
    Chapter,
) -> Int {
    return when (audiobook.sorting) {
        Audiobook.CHAPTER_SORTING_SOURCE -> when (sortDescending) {
            true -> { e1, e2 -> e1.sourceOrder.compareTo(e2.sourceOrder) }
            false -> { e1, e2 -> e2.sourceOrder.compareTo(e1.sourceOrder) }
        }
        Audiobook.CHAPTER_SORTING_NUMBER -> when (sortDescending) {
            true -> { e1, e2 -> e2.chapterNumber.compareTo(e1.chapterNumber) }
            false -> { e1, e2 -> e1.chapterNumber.compareTo(e2.chapterNumber) }
        }
        Audiobook.CHAPTER_SORTING_UPLOAD_DATE -> when (sortDescending) {
            true -> { e1, e2 -> e2.dateUpload.compareTo(e1.dateUpload) }
            false -> { e1, e2 -> e1.dateUpload.compareTo(e2.dateUpload) }
        }
        Audiobook.CHAPTER_SORTING_ALPHABET -> when (sortDescending) {
            true -> { e1, e2 -> e2.name.compareToWithCollator(e1.name) }
            false -> { e1, e2 -> e1.name.compareToWithCollator(e2.name) }
        }
        else -> throw NotImplementedError("Invalid chapter sorting method: ${audiobook.sorting}")
    }
}
