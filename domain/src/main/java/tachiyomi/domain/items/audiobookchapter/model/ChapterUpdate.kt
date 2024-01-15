package tachiyomi.domain.items.audiobookchapter.model

data class ChapterUpdate(
    val id: Long,
    val audiobookId: Long? = null,
    val read: Boolean? = null,
    val bookmark: Boolean? = null,
    val lastSecondRead: Long? = null,
    val totalSeconds: Long? = null,
    val dateFetch: Long? = null,
    val sourceOrder: Long? = null,
    val url: String? = null,
    val name: String? = null,
    val dateUpload: Long? = null,
    val chapterNumber: Double? = null,
    val scanlator: String? = null,
)

fun Chapter.toChapterUpdate(): ChapterUpdate {
    return ChapterUpdate(
        id,
        audiobookId,
        read,
        bookmark,
        lastSecondRead,
        totalSeconds,
        dateFetch,
        sourceOrder,
        url,
        name,
        dateUpload,
        chapterNumber,
        scanlator,
    )
}
