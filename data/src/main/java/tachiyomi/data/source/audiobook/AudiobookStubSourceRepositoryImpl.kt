package tachiyomi.data.source.audiobook

import kotlinx.coroutines.flow.Flow
import tachiyomi.data.handlers.audiobook.AudiobookDatabaseHandler
import tachiyomi.domain.source.audiobook.model.StubAudiobookSource
import tachiyomi.domain.source.audiobook.repository.AudiobookStubSourceRepository

class AudiobookStubSourceRepositoryImpl(
    private val handler: AudiobookDatabaseHandler,
) : AudiobookStubSourceRepository {

    override fun subscribeAllAudiobook(): Flow<List<StubAudiobookSource>> {
        return handler.subscribeToList { audiobooksourcesQueries.findAll(::mapStubSource) }
    }

    override suspend fun getStubAudiobookSource(id: Long): StubAudiobookSource? {
        return handler.awaitOneOrNull {
            audiobooksourcesQueries.findOne(
                id,
                ::mapStubSource,
            )
        }
    }

    override suspend fun upsertStubAudiobookSource(id: Long, lang: String, name: String) {
        handler.await { audiobooksourcesQueries.upsert(id, lang, name) }
    }

    private fun mapStubSource(
        id: Long,
        lang: String,
        name: String,
    ): StubAudiobookSource = StubAudiobookSource(id = id, lang = lang, name = name)
}
