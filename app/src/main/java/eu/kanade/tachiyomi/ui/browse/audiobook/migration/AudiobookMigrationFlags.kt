package eu.kanade.tachiyomi.ui.browse.audiobook.migration

import dev.icerock.moko.resources.StringResource
import eu.kanade.domain.entries.audiobook.model.hasCustomCover
import eu.kanade.tachiyomi.data.cache.AudiobookCoverCache
import eu.kanade.tachiyomi.data.download.audiobook.AudiobookDownloadCache
import tachiyomi.domain.entries.audiobook.model.Audiobook
import tachiyomi.i18n.MR
import uy.kohesive.injekt.injectLazy

data class AudiobookMigrationFlag(
    val flag: Int,
    val isDefaultSelected: Boolean,
    val titleId: StringResource,
) {
    companion object {
        fun create(flag: Int, defaultSelectionMap: Int, titleId: StringResource): AudiobookMigrationFlag {
            return AudiobookMigrationFlag(
                flag = flag,
                isDefaultSelected = defaultSelectionMap and flag != 0,
                titleId = titleId,
            )
        }
    }
}

object AudiobookMigrationFlags {

    private const val CHAPTERS = 0b00001
    private const val CATEGORIES = 0b00010
    private const val CUSTOM_COVER = 0b01000
    private const val DELETE_DOWNLOADED = 0b10000

    private val coverCache: AudiobookCoverCache by injectLazy()
    private val downloadCache: AudiobookDownloadCache by injectLazy()

    fun hasChapters(value: Int): Boolean {
        return value and CHAPTERS != 0
    }

    fun hasCategories(value: Int): Boolean {
        return value and CATEGORIES != 0
    }

    fun hasCustomCover(value: Int): Boolean {
        return value and CUSTOM_COVER != 0
    }

    fun hasDeleteDownloaded(value: Int): Boolean {
        return value and DELETE_DOWNLOADED != 0
    }

    /** Returns information about applicable flags with default selections. */
    fun getFlags(audiobook: Audiobook?, defaultSelectedBitMap: Int): List<AudiobookMigrationFlag> {
        val flags = mutableListOf<AudiobookMigrationFlag>()
        flags += AudiobookMigrationFlag.create(CHAPTERS, defaultSelectedBitMap, MR.strings.chapters)
        flags += AudiobookMigrationFlag.create(CATEGORIES, defaultSelectedBitMap, MR.strings.categories)

        if (audiobook != null) {
            if (audiobook.hasCustomCover(coverCache)) {
                flags += AudiobookMigrationFlag.create(
                    CUSTOM_COVER,
                    defaultSelectedBitMap,
                    MR.strings.custom_cover,
                )
            }
            if (downloadCache.getDownloadCount(audiobook) > 0) {
                flags += AudiobookMigrationFlag.create(
                    DELETE_DOWNLOADED,
                    defaultSelectedBitMap,
                    MR.strings.delete_downloaded,
                )
            }
        }
        return flags
    }

    /** Returns a bit map of selected flags. */
    fun getSelectedFlagsBitMap(
        selectedFlags: List<Boolean>,
        flags: List<AudiobookMigrationFlag>,
    ): Int {
        return selectedFlags
            .zip(flags)
            .filter { (isSelected, _) -> isSelected }
            .map { (_, flag) -> flag.flag }
            .reduceOrNull { acc, mask -> acc or mask } ?: 0
    }
}
