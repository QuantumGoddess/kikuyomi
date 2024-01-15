package eu.kanade.tachiyomi.data.coil

import coil.key.Keyer
import coil.request.Options
import eu.kanade.domain.entries.audiobook.model.hasCustomCover
import eu.kanade.tachiyomi.data.cache.AudiobookCoverCache
import tachiyomi.domain.entries.audiobook.model.AudiobookCover
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import tachiyomi.domain.entries.audiobook.model.Audiobook as DomainAudiobook

class AudiobookKeyer : Keyer<DomainAudiobook> {
    override fun key(data: DomainAudiobook, options: Options): String {
        return if (data.hasCustomCover()) {
            "audiobook;${data.id};${data.coverLastModified}"
        } else {
            "audiobook;${data.thumbnailUrl};${data.coverLastModified}"
        }
    }
}

class AudiobookCoverKeyer(
    private val coverCache: AudiobookCoverCache = Injekt.get(),
) : Keyer<AudiobookCover> {
    override fun key(data: AudiobookCover, options: Options): String {
        return if (coverCache.getCustomCoverFile(data.audiobookId).exists()) {
            "audiobook;${data.audiobookId};${data.lastModified}"
        } else {
            "audiobook;${data.url};${data.lastModified}"
        }
    }
}
