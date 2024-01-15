package eu.kanade.tachiyomi.ui.browse.audiobook.extension

import android.app.Application
import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import dev.icerock.moko.resources.StringResource
import eu.kanade.domain.extension.audiobook.interactor.GetAudiobookExtensionsByType
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.components.SEARCH_DEBOUNCE_MILLIS
import eu.kanade.tachiyomi.audiobooksource.online.AudiobookHttpSource
import eu.kanade.tachiyomi.extension.InstallStep
import eu.kanade.tachiyomi.extension.audiobook.AudiobookExtensionManager
import eu.kanade.tachiyomi.extension.audiobook.model.AudiobookExtension
import eu.kanade.tachiyomi.util.system.LocaleHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import tachiyomi.core.util.lang.launchIO
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.time.Duration.Companion.seconds

class AudiobookExtensionsScreenModel(
    preferences: SourcePreferences = Injekt.get(),
    private val extensionManager: AudiobookExtensionManager = Injekt.get(),
    private val getExtensions: GetAudiobookExtensionsByType = Injekt.get(),
) : StateScreenModel<AudiobookExtensionsScreenModel.State>(State()) {

    private var _currentDownloads = MutableStateFlow<Map<String, InstallStep>>(hashMapOf())

    init {
        val context = Injekt.get<Application>()
        val extensionMapper: (Map<String, InstallStep>) -> ((AudiobookExtension) -> AudiobookExtensionUiModel.Item) = { map ->
            {
                AudiobookExtensionUiModel.Item(it, map[it.pkgName] ?: InstallStep.Idle)
            }
        }
        val queryFilter: (String) -> ((AudiobookExtension) -> Boolean) = { query ->
            filter@{ extension ->
                if (query.isEmpty()) return@filter true
                query.split(",").any { _input ->
                    val input = _input.trim()
                    if (input.isEmpty()) return@any false
                    when (extension) {
                        is AudiobookExtension.Available -> {
                            extension.sources.any {
                                it.name.contains(input, ignoreCase = true) ||
                                    it.baseUrl.contains(input, ignoreCase = true) ||
                                    it.id == input.toLongOrNull()
                            } || extension.name.contains(input, ignoreCase = true)
                        }
                        is AudiobookExtension.Installed -> {
                            extension.sources.any {
                                it.name.contains(input, ignoreCase = true) ||
                                    it.id == input.toLongOrNull() ||
                                    if (it is AudiobookHttpSource) {
                                        it.baseUrl.contains(
                                            input,
                                            ignoreCase = true,
                                        )
                                    } else {
                                        false
                                    }
                            } || extension.name.contains(input, ignoreCase = true)
                        }
                        is AudiobookExtension.Untrusted -> extension.name.contains(
                            input,
                            ignoreCase = true,
                        )
                    }
                }
            }
        }

        screenModelScope.launchIO {
            combine(
                state.map { it.searchQuery }.distinctUntilChanged().debounce(SEARCH_DEBOUNCE_MILLIS),
                _currentDownloads,
                getExtensions.subscribe(),
            ) { query, downloads, (_updates, _installed, _available, _untrusted) ->
                val searchQuery = query ?: ""

                val itemsGroups: ItemGroups = mutableMapOf()

                val updates = _updates.filter(queryFilter(searchQuery)).map(
                    extensionMapper(downloads),
                )
                if (updates.isNotEmpty()) {
                    itemsGroups[AudiobookExtensionUiModel.Header.Resource(MR.strings.ext_updates_pending)] = updates
                }

                val installed = _installed.filter(queryFilter(searchQuery)).map(
                    extensionMapper(downloads),
                )
                val untrusted = _untrusted.filter(queryFilter(searchQuery)).map(
                    extensionMapper(downloads),
                )
                if (installed.isNotEmpty() || untrusted.isNotEmpty()) {
                    itemsGroups[AudiobookExtensionUiModel.Header.Resource(MR.strings.ext_installed)] = installed + untrusted
                }

                val languagesWithExtensions = _available
                    .filter(queryFilter(searchQuery))
                    .groupBy { it.lang }
                    .toSortedMap(LocaleHelper.comparator)
                    .map { (lang, exts) ->
                        AudiobookExtensionUiModel.Header.Text(
                            LocaleHelper.getSourceDisplayName(lang, context),
                        ) to exts.map(extensionMapper(downloads))
                    }

                if (languagesWithExtensions.isNotEmpty()) {
                    itemsGroups.putAll(languagesWithExtensions)
                }

                itemsGroups
            }
                .collectLatest {
                    mutableState.update { state ->
                        state.copy(
                            isLoading = false,
                            items = it,
                        )
                    }
                }
        }
        screenModelScope.launchIO { findAvailableExtensions() }

        preferences.audiobookExtensionUpdatesCount().changes()
            .onEach { mutableState.update { state -> state.copy(updates = it) } }
            .launchIn(screenModelScope)
    }

    fun search(query: String?) {
        mutableState.update {
            it.copy(searchQuery = query)
        }
    }

    fun updateAllExtensions() {
        screenModelScope.launchIO {
            state.value.items.values.flatten()
                .map { it.extension }
                .filterIsInstance<AudiobookExtension.Installed>()
                .filter { it.hasUpdate }
                .forEach(::updateExtension)
        }
    }

    fun installExtension(extension: AudiobookExtension.Available) {
        screenModelScope.launchIO {
            extensionManager.installExtension(extension).collectToInstallUpdate(extension)
        }
    }

    fun updateExtension(extension: AudiobookExtension.Installed) {
        screenModelScope.launchIO {
            extensionManager.updateExtension(extension).collectToInstallUpdate(extension)
        }
    }

    fun cancelInstallUpdateExtension(extension: AudiobookExtension) {
        extensionManager.cancelInstallUpdateExtension(extension)
    }

    private fun addDownloadState(extension: AudiobookExtension, installStep: InstallStep) {
        _currentDownloads.update { it + Pair(extension.pkgName, installStep) }
    }

    private fun removeDownloadState(extension: AudiobookExtension) {
        _currentDownloads.update { it - extension.pkgName }
    }

    private suspend fun Flow<InstallStep>.collectToInstallUpdate(extension: AudiobookExtension) =
        this
            .onEach { installStep -> addDownloadState(extension, installStep) }
            .onCompletion { removeDownloadState(extension) }
            .collect()

    fun uninstallExtension(extension: AudiobookExtension) {
        extensionManager.uninstallExtension(extension)
    }

    fun findAvailableExtensions() {
        screenModelScope.launchIO {
            mutableState.update { it.copy(isRefreshing = true) }
            extensionManager.findAvailableExtensions()

            // Fake slower refresh so it doesn't seem like it's not doing anything
            delay(1.seconds)

            mutableState.update { it.copy(isRefreshing = false) }
        }
    }

    fun trustSignature(signatureHash: String) {
        extensionManager.trustSignature(signatureHash)
    }

    @Immutable
    data class State(
        val isLoading: Boolean = true,
        val isRefreshing: Boolean = false,
        val items: ItemGroups = mutableMapOf(),
        val updates: Int = 0,
        val searchQuery: String? = null,
    ) {
        val isEmpty = items.isEmpty()
    }
}

typealias ItemGroups = MutableMap<AudiobookExtensionUiModel.Header, List<AudiobookExtensionUiModel.Item>>

object AudiobookExtensionUiModel {
    sealed interface Header {
        data class Resource(val textRes: StringResource) : Header
        data class Text(val text: String) : Header
    }
    data class Item(
        val extension: AudiobookExtension,
        val installStep: InstallStep,
    )
}
