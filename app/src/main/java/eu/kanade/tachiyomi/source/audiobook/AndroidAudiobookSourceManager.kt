package eu.kanade.tachiyomi.source.audiobook

import android.content.Context
import eu.kanade.tachiyomi.audiobooksource.AudiobookCatalogueSource
import eu.kanade.tachiyomi.audiobooksource.AudiobookSource
import eu.kanade.tachiyomi.audiobooksource.online.AudiobookHttpSource
import eu.kanade.tachiyomi.data.download.audiobook.AudiobookDownloadManager
import eu.kanade.tachiyomi.extension.audiobook.AudiobookExtensionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import tachiyomi.domain.source.audiobook.model.StubAudiobookSource
import tachiyomi.domain.source.audiobook.repository.AudiobookStubSourceRepository
import tachiyomi.domain.source.audiobook.service.AudiobookSourceManager
import tachiyomi.source.local.entries.audiobook.LocalAudiobookSource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.util.concurrent.ConcurrentHashMap

class AndroidAudiobookSourceManager(
    private val context: Context,
    private val extensionManager: AudiobookExtensionManager,
    private val sourceRepository: AudiobookStubSourceRepository,
) : AudiobookSourceManager {
    private val downloadManager: AudiobookDownloadManager by injectLazy()

    private val scope = CoroutineScope(Job() + Dispatchers.IO)

    private val sourcesMapFlow = MutableStateFlow(ConcurrentHashMap<Long, AudiobookSource>())

    private val stubSourcesMap = ConcurrentHashMap<Long, StubAudiobookSource>()

    override val catalogueSources: Flow<List<AudiobookCatalogueSource>> = sourcesMapFlow.map {
        it.values.filterIsInstance<AudiobookCatalogueSource>()
    }

    init {
        scope.launch {
            extensionManager.installedExtensionsFlow
                .collectLatest { extensions ->
                    val mutableMap = ConcurrentHashMap<Long, AudiobookSource>(
                        mapOf(
                            LocalAudiobookSource.ID to LocalAudiobookSource(
                                context,
                                Injekt.get(),
                                Injekt.get(),
                            ),
                        ),
                    )
                    extensions.forEach { extension ->
                        extension.sources.forEach {
                            mutableMap[it.id] = it
                            registerStubSource(StubAudiobookSource.from(it))
                        }
                    }
                    sourcesMapFlow.value = mutableMap
                }
        }

        scope.launch {
            sourceRepository.subscribeAllAudiobook()
                .collectLatest { sources ->
                    val mutableMap = stubSourcesMap.toMutableMap()
                    sources.forEach {
                        mutableMap[it.id] = it
                    }
                }
        }
    }

    override fun get(sourceKey: Long): AudiobookSource? {
        return sourcesMapFlow.value[sourceKey]
    }

    override fun getOrStub(sourceKey: Long): AudiobookSource {
        return sourcesMapFlow.value[sourceKey] ?: stubSourcesMap.getOrPut(sourceKey) {
            runBlocking { createStubSource(sourceKey) }
        }
    }

    override fun getOnlineSources() = sourcesMapFlow.value.values.filterIsInstance<AudiobookHttpSource>()

    override fun getCatalogueSources() = sourcesMapFlow.value.values.filterIsInstance<AudiobookCatalogueSource>()

    override fun getStubSources(): List<StubAudiobookSource> {
        val onlineSourceIds = getOnlineSources().map { it.id }
        return stubSourcesMap.values.filterNot { it.id in onlineSourceIds }
    }

    private fun registerStubSource(source: StubAudiobookSource) {
        scope.launch {
            val dbSource = sourceRepository.getStubAudiobookSource(source.id)
            if (dbSource == source) return@launch
            sourceRepository.upsertStubAudiobookSource(source.id, source.lang, source.name)
            if (dbSource != null) {
                downloadManager.renameSource(dbSource, source)
            }
        }
    }

    private suspend fun createStubSource(id: Long): StubAudiobookSource {
        sourceRepository.getStubAudiobookSource(id)?.let {
            return it
        }
        extensionManager.getSourceData(id)?.let {
            registerStubSource(it)
            return it
        }
        return StubAudiobookSource(id = id, lang = "", name = "")
    }
}
