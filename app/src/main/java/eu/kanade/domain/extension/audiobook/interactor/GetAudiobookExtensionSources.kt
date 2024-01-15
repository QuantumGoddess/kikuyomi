package eu.kanade.domain.extension.audiobook.interactor

import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.audiobooksource.AudiobookSource
import eu.kanade.tachiyomi.extension.audiobook.model.AudiobookExtension
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class GetAudiobookExtensionSources(
    private val preferences: SourcePreferences,
) {

    fun subscribe(extension: AudiobookExtension.Installed): Flow<List<AudiobookExtensionSourceItem>> {
        val isMultiSource = extension.sources.size > 1
        val isMultiLangSingleSource =
            isMultiSource && extension.sources.map { it.name }.distinct().size == 1

        return preferences.disabledAudiobookSources().changes().map { disabledSources ->
            fun AudiobookSource.isEnabled() = id.toString() !in disabledSources

            extension.sources
                .map { source ->
                    AudiobookExtensionSourceItem(
                        source = source,
                        enabled = source.isEnabled(),
                        labelAsName = isMultiSource && isMultiLangSingleSource.not(),
                    )
                }
        }
    }
}

data class AudiobookExtensionSourceItem(
    val source: AudiobookSource,
    val enabled: Boolean,
    val labelAsName: Boolean,
)
