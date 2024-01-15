package eu.kanade.domain.entries.audiobook.interactor

import tachiyomi.domain.entries.audiobook.model.Audiobook
import tachiyomi.domain.entries.audiobook.model.AudiobookUpdate
import tachiyomi.domain.entries.audiobook.repository.AudiobookRepository
import kotlin.math.pow

class SetAudiobookViewerFlags(
    private val audiobookRepository: AudiobookRepository,
) {

    suspend fun awaitSetSkipIntroLength(id: Long, flag: Long) {
        val audiobook = audiobookRepository.getAudiobookById(id)
        audiobookRepository.updateAudiobook(
            AudiobookUpdate(
                id = id,
                viewerFlags = audiobook.viewerFlags.setFlag(flag, Audiobook.AUDIOBOOK_INTRO_MASK),
            ),
        )
    }

    suspend fun awaitSetNextChapterAiring(id: Long, flags: Pair<Int, Long>) {
        awaitSetNextChapterToAir(id, flags.first.toLong().addHexZeros(zeros = 2))
        awaitSetNextChapterAiringAt(id, flags.second.addHexZeros(zeros = 6))
    }

    private suspend fun awaitSetNextChapterToAir(id: Long, flag: Long) {
        val audiobook = audiobookRepository.getAudiobookById(id)
        audiobookRepository.updateAudiobook(
            AudiobookUpdate(
                id = id,
                viewerFlags = audiobook.viewerFlags.setFlag(flag, Audiobook.AUDIOBOOK_AIRING_CHAPTER_MASK),
            ),
        )
    }

    private suspend fun awaitSetNextChapterAiringAt(id: Long, flag: Long) {
        val audiobook = audiobookRepository.getAudiobookById(id)
        audiobookRepository.updateAudiobook(
            AudiobookUpdate(
                id = id,
                viewerFlags = audiobook.viewerFlags.setFlag(flag, Audiobook.AUDIOBOOK_AIRING_TIME_MASK),
            ),
        )
    }

    private fun Long.setFlag(flag: Long, mask: Long): Long {
        return this and mask.inv() or (flag and mask)
    }

    private fun Long.addHexZeros(zeros: Int): Long {
        val hex = 16.0
        return this.times(hex.pow(zeros)).toLong()
    }
}
