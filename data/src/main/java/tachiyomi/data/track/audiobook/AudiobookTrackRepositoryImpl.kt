package tachiyomi.data.track.audiobook

import kotlinx.coroutines.flow.Flow
import tachiyomi.data.handlers.audiobook.AudiobookDatabaseHandler
import tachiyomi.domain.track.audiobook.model.AudiobookTrack
import tachiyomi.domain.track.audiobook.repository.AudiobookTrackRepository

class AudiobookTrackRepositoryImpl(
    private val handler: AudiobookDatabaseHandler,
) : AudiobookTrackRepository {

    override suspend fun getTrackByAudiobookId(id: Long): AudiobookTrack? {
        return handler.awaitOneOrNull { audiobook_syncQueries.getTrackByAudiobookId(id, ::mapTrack) }
    }

    override suspend fun getTracksByAudiobookId(audiobookId: Long): List<AudiobookTrack> {
        return handler.awaitList {
            audiobook_syncQueries.getTracksByAudiobookId(audiobookId, ::mapTrack)
        }
    }

    override fun getAudiobookTracksAsFlow(): Flow<List<AudiobookTrack>> {
        return handler.subscribeToList {
            audiobook_syncQueries.getAudiobookTracks(::mapTrack)
        }
    }

    override fun getTracksByAudiobookIdAsFlow(audiobookId: Long): Flow<List<AudiobookTrack>> {
        return handler.subscribeToList {
            audiobook_syncQueries.getTracksByAudiobookId(audiobookId, ::mapTrack)
        }
    }

    override suspend fun deleteAudiobook(audiobookId: Long, syncId: Long) {
        handler.await {
            audiobook_syncQueries.delete(
                audiobookId = audiobookId,
                syncId = syncId,
            )
        }
    }

    override suspend fun insertAudiobook(track: AudiobookTrack) {
        insertValues(track)
    }

    override suspend fun insertAllAudiobook(tracks: List<AudiobookTrack>) {
        insertValues(*tracks.toTypedArray())
    }

    private suspend fun insertValues(vararg tracks: AudiobookTrack) {
        handler.await(inTransaction = true) {
            tracks.forEach { audiobookTrack ->
                audiobook_syncQueries.insert(
                    audiobookId = audiobookTrack.audiobookId,
                    syncId = audiobookTrack.syncId,
                    remoteId = audiobookTrack.remoteId,
                    libraryId = audiobookTrack.libraryId,
                    title = audiobookTrack.title,
                    lastChapterRead = audiobookTrack.lastChapterRead,
                    totalChapters = audiobookTrack.totalChapters,
                    status = audiobookTrack.status,
                    score = audiobookTrack.score,
                    remoteUrl = audiobookTrack.remoteUrl,
                    startDate = audiobookTrack.startDate,
                    finishDate = audiobookTrack.finishDate,
                )
            }
        }
    }

    private fun mapTrack(
        id: Long,
        audiobookId: Long,
        syncId: Long,
        remoteId: Long,
        libraryId: Long?,
        title: String,
        lastChapterRead: Double,
        totalChapters: Long,
        status: Long,
        score: Double,
        remoteUrl: String,
        startDate: Long,
        finishDate: Long,
    ): AudiobookTrack = AudiobookTrack(
        id = id,
        audiobookId = audiobookId,
        syncId = syncId,
        remoteId = remoteId,
        libraryId = libraryId,
        title = title,
        lastChapterRead = lastChapterRead,
        totalChapters = totalChapters,
        status = status,
        score = score,
        remoteUrl = remoteUrl,
        startDate = startDate,
        finishDate = finishDate,
    )
}
