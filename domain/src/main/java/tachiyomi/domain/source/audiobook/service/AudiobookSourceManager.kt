package tachiyomi.domain.source.audiobook.service

import eu.kanade.tachiyomi.audiobooksource.AudiobookCatalogueSource
import eu.kanade.tachiyomi.audiobooksource.AudiobookSource
import eu.kanade.tachiyomi.audiobooksource.online.AudiobookHttpSource
import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.source.audiobook.model.StubAudiobookSource

interface AudiobookSourceManager {

    val catalogueSources: Flow<List<AudiobookCatalogueSource>>

    fun get(sourceKey: Long): AudiobookSource?

    fun getOrStub(sourceKey: Long): AudiobookSource

    fun getOnlineSources(): List<AudiobookHttpSource>

    fun getCatalogueSources(): List<AudiobookCatalogueSource>

    fun getStubSources(): List<StubAudiobookSource>
}
