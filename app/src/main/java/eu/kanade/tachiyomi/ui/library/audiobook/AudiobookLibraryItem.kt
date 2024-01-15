package eu.kanade.tachiyomi.ui.library.audiobook

import eu.kanade.tachiyomi.source.audiobook.getNameForAudiobookInfo
import tachiyomi.domain.library.audiobook.LibraryAudiobook
import tachiyomi.domain.source.audiobook.service.AudiobookSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

data class AudiobookLibraryItem(
    val libraryAudiobook: LibraryAudiobook,
    var downloadCount: Long = -1,
    var unreadCount: Long = -1,
    var isLocal: Boolean = false,
    var sourceLanguage: String = "",
    private val sourceManager: AudiobookSourceManager = Injekt.get(),
) {
    /**
     * Checks if a query matches the audiobook
     *
     * @param constraint the query to check.
     * @return true if the audiobook matches the query, false otherwise.
     */
    fun matches(constraint: String): Boolean {
        val sourceName by lazy { sourceManager.getOrStub(libraryAudiobook.audiobook.source).getNameForAudiobookInfo() }
        return libraryAudiobook.audiobook.title.contains(constraint, true) ||
            (libraryAudiobook.audiobook.author?.contains(constraint, true) ?: false) ||
            (libraryAudiobook.audiobook.artist?.contains(constraint, true) ?: false) ||
            (libraryAudiobook.audiobook.description?.contains(constraint, true) ?: false) ||
            constraint.split(",").map { it.trim() }.all { subconstraint ->
                checkNegatableConstraint(subconstraint) {
                    sourceName.contains(it, true) ||
                        (libraryAudiobook.audiobook.genre?.any { genre -> genre.equals(it, true) } ?: false)
                }
            }
    }

    /**
     * Checks a predicate on a negatable constraint. If the constraint starts with a minus character,
     * the minus is stripped and the result of the predicate is inverted.
     *
     * @param constraint the argument to the predicate. Inverts the predicate if it starts with '-'.
     * @param predicate the check to be run against the constraint.
     * @return !predicate(x) if constraint = "-x", otherwise predicate(constraint)
     */
    private fun checkNegatableConstraint(
        constraint: String,
        predicate: (String) -> Boolean,
    ): Boolean {
        return if (constraint.startsWith("-")) {
            !predicate(constraint.substringAfter("-").trimStart())
        } else {
            predicate(constraint)
        }
    }
}
