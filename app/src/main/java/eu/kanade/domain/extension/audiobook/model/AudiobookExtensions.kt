package eu.kanade.domain.extension.audiobook.model

import eu.kanade.tachiyomi.extension.audiobook.model.AudiobookExtension

data class AudiobookExtensions(
    val updates: List<AudiobookExtension.Installed>,
    val installed: List<AudiobookExtension.Installed>,
    val available: List<AudiobookExtension.Available>,
    val untrusted: List<AudiobookExtension.Untrusted>,
)
