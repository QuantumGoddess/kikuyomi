package tachiyomi.domain.entries.audiobook.interactor

import tachiyomi.domain.entries.audiobook.model.Audiobook
import tachiyomi.domain.entries.audiobook.repository.AudiobookRepository

class NetworkToLocalAudiobook(
    private val audiobookRepository: AudiobookRepository,
) {

    suspend fun await(audiobook: Audiobook): Audiobook {
        val localAudiobook = getAudiobook(audiobook.url, audiobook.source)
        return when {
            localAudiobook == null -> {
                val id = insertAudiobook(audiobook)
                audiobook.copy(id = id!!)
            }
            !localAudiobook.favorite -> {
                // if the audiobook isn't a favorite, set its display title from source
                // if it later becomes a favorite, updated title will go to db
                localAudiobook.copy(title = audiobook.title)
            }
            else -> {
                localAudiobook
            }
        }
    }

    private suspend fun getAudiobook(url: String, sourceId: Long): Audiobook? {
        return audiobookRepository.getAudiobookByUrlAndSourceId(url, sourceId)
    }

    private suspend fun insertAudiobook(audiobook: Audiobook): Long? {
        return audiobookRepository.insertAudiobook(audiobook)
    }
}
