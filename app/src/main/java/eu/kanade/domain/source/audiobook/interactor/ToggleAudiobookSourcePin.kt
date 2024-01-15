package eu.kanade.domain.source.audiobook.interactor

import eu.kanade.domain.source.service.SourcePreferences
import tachiyomi.core.preference.getAndSet
import tachiyomi.domain.source.audiobook.model.AudiobookSource

class ToggleAudiobookSourcePin(
    private val preferences: SourcePreferences,
) {

    fun await(source: AudiobookSource) {
        val isPinned = source.id.toString() in preferences.pinnedAudiobookSources().get()
        preferences.pinnedAudiobookSources().getAndSet { pinned ->
            if (isPinned) pinned.minus("${source.id}") else pinned.plus("${source.id}")
        }
    }
}
