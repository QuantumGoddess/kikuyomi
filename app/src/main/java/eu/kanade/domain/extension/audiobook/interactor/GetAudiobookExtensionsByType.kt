package eu.kanade.domain.extension.audiobook.interactor

import eu.kanade.domain.extension.audiobook.model.AudiobookExtensions
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.audiobook.AudiobookExtensionManager
import eu.kanade.tachiyomi.extension.audiobook.model.AudiobookExtension
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class GetAudiobookExtensionsByType(
    private val preferences: SourcePreferences,
    private val extensionManager: AudiobookExtensionManager,
) {

    fun subscribe(): Flow<AudiobookExtensions> {
        val showNsfwSources = preferences.showNsfwSource().get()

        return combine(
            preferences.enabledLanguages().changes(),
            extensionManager.installedExtensionsFlow,
            extensionManager.untrustedExtensionsFlow,
            extensionManager.availableExtensionsFlow,
        ) { _activeLanguages, _installed, _untrusted, _available ->
            val (updates, installed) = _installed
                .filter { (showNsfwSources || it.isNsfw.not()) }
                .sortedWith(
                    compareBy<AudiobookExtension.Installed> { it.isObsolete.not() }
                        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name },
                )
                .partition { it.hasUpdate }

            val untrusted = _untrusted
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

            val available = _available
                .filter { extension ->
                    _installed.none { it.pkgName == extension.pkgName } &&
                        _untrusted.none { it.pkgName == extension.pkgName } &&
                        (showNsfwSources || extension.isNsfw.not())
                }
                .flatMap { ext ->
                    if (ext.sources.isEmpty()) {
                        return@flatMap if (ext.lang in _activeLanguages) listOf(ext) else emptyList()
                    }
                    ext.sources.filter { it.lang in _activeLanguages }
                        .map {
                            ext.copy(
                                name = it.name,
                                lang = it.lang,
                                pkgName = "${ext.pkgName}-${it.id}",
                                sources = listOf(it),
                            )
                        }
                }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

            AudiobookExtensions(updates, installed, available, untrusted)
        }
    }
}
