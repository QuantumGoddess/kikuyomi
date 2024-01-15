package eu.kanade.tachiyomi.data.download.audiobook

import android.content.Context
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.audiobooksource.AudiobookSource
import eu.kanade.tachiyomi.util.storage.DiskUtil
import logcat.LogPriority
import tachiyomi.core.i18n.stringResource
import tachiyomi.core.util.system.logcat
import tachiyomi.domain.entries.audiobook.model.Audiobook
import tachiyomi.domain.items.audiobookchapter.model.Chapter
import tachiyomi.domain.storage.service.StorageManager
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * This class is used to provide the directories where the downloads should be saved.
 * It uses the following path scheme: /<root downloads dir>/<source name>/<audiobook>/<chapter>
 *
 * @param context the application context.
 */
class AudiobookDownloadProvider(
    private val context: Context,
    private val storageManager: StorageManager = Injekt.get(),
) {

    val downloadsDir: UniFile?
        get() = storageManager.getDownloadsDirectory()

    /**
     * Returns the download directory for an audiobook. For internal use only.
     *
     * @param audiobookTitle the title of the audiobook to query.
     * @param source the source of the audiobook.
     */
    internal fun getAudiobookDir(audiobookTitle: String, source: AudiobookSource): UniFile {
        try {
            return downloadsDir!!
                .createDirectory(getSourceDirName(source))!!
                .createDirectory(getAudiobookDirName(audiobookTitle))!!
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e) { "Invalid download directory" }
            throw Exception(context.stringResource(MR.strings.invalid_location, downloadsDir ?: ""))
        }
    }

    /**
     * Returns the download directory for a source if it exists.
     *
     * @param source the source to query.
     */
    fun findSourceDir(source: AudiobookSource): UniFile? {
        return downloadsDir?.findFile(getSourceDirName(source), true)
    }

    /**
     * Returns the download directory for an audiobook if it exists.
     *
     * @param audiobookTitle the title of the audiobook to query.
     * @param source the source of the audiobook.
     */
    fun findAudiobookDir(audiobookTitle: String, source: AudiobookSource): UniFile? {
        val sourceDir = findSourceDir(source)
        return sourceDir?.findFile(getAudiobookDirName(audiobookTitle), true)
    }

    /**
     * Returns the download directory for an chapter if it exists.
     *
     * @param chapterName the name of the chapter to query.
     * @param chapterScanlator scanlator of the chapter to query
     * @param audiobookTitle the title of the audiobook to query.
     * @param source the source of the chapter.
     */
    fun findChapterDir(
        chapterName: String,
        chapterScanlator: String?,
        audiobookTitle: String,
        source: AudiobookSource,
    ): UniFile? {
        val audiobookDir = findAudiobookDir(audiobookTitle, source)
        return getValidChapterDirNames(chapterName, chapterScanlator).asSequence()
            .mapNotNull { audiobookDir?.findFile(it, true) }
            .firstOrNull()
    }

    /**
     * Returns a list of downloaded directories for the chapters that exist.
     *
     * @param chapters the chapters to query.
     * @param audiobook the audiobook of the chapter.
     * @param source the source of the chapter.
     */
    fun findChapterDirs(chapters: List<Chapter>, audiobook: Audiobook, source: AudiobookSource): Pair<UniFile?, List<UniFile>> {
        val audiobookDir = findAudiobookDir(audiobook.title, source) ?: return null to emptyList()
        return audiobookDir to chapters.mapNotNull { chapter ->
            getValidChapterDirNames(chapter.name, chapter.scanlator).asSequence()
                .mapNotNull { audiobookDir.findFile(it, true) }
                .firstOrNull()
        }
    }

    /**
     * Returns the download directory name for a source.
     *
     * @param source the source to query.
     */
    fun getSourceDirName(source: AudiobookSource): String {
        return DiskUtil.buildValidFilename(source.toString())
    }

    /**
     * Returns the download directory name for an audiobook.
     *
     * @param audiobookTitle the title of the audiobook to query.
     */
    fun getAudiobookDirName(audiobookTitle: String): String {
        return DiskUtil.buildValidFilename(audiobookTitle)
    }

    /**
     * Returns the chapter directory name for an chapter.
     *
     * @param chapterName the name of the chapter to query.
     * @param chapterScanlator scanlator of the chapter to query
     */
    fun getChapterDirName(chapterName: String, chapterScanlator: String?): String {
        val newChapterName = sanitizeChapterName(chapterName)
        return DiskUtil.buildValidFilename(
            when {
                chapterScanlator.isNullOrBlank().not() -> "${chapterScanlator}_$newChapterName"
                else -> newChapterName
            },
        )
    }

    /**
     * Return the new name for the chapter (in case it's empty or blank)
     *
     * @param chapterName the name of the chapter
     */
    private fun sanitizeChapterName(chapterName: String): String {
        return chapterName.ifBlank {
            "Chapter"
        }
    }

    /**
     * Returns the chapter directory name for an chapter.
     *
     * @param chapterName the name of the chapter to query.
     * @param chapterScanlator scanlator of the chapter to query
     */
    fun getOldChapterDirName(chapterName: String, chapterScanlator: String?): String {
        return DiskUtil.buildValidFilename(
            when {
                chapterScanlator != null -> "${chapterScanlator}_$chapterName"
                else -> chapterName
            },
        )
    }

    fun isChapterDirNameChanged(oldChapter: Chapter, newChapter: Chapter): Boolean {
        return oldChapter.name != newChapter.name ||
            oldChapter.scanlator?.takeIf { it.isNotBlank() } != newChapter.scanlator?.takeIf { it.isNotBlank() }
    }

    /**
     * Returns valid downloaded chapter directory names.
     *
     * @param chapterName the name of the chapter to query.
     * @param chapterScanlator scanlator of the chapter to query
     */
    fun getValidChapterDirNames(chapterName: String, chapterScanlator: String?): List<String> {
        val chapterDirName = getChapterDirName(chapterName, chapterScanlator)
        val oldChapterDirName = getOldChapterDirName(chapterName, chapterScanlator)
        return listOf(chapterDirName, oldChapterDirName)
    }
}
