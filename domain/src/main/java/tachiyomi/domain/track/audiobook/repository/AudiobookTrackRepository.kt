package tachiyomi.domain.track.audiobook.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.track.audiobook.model.AudiobookTrack

interface AudiobookTrackRepository {

    suspend fun getTrackByAudiobookId(id: Long): AudiobookTrack?

    suspend fun getTracksByAudiobookId(audiobookId: Long): List<AudiobookTrack>

    fun getAudiobookTracksAsFlow(): Flow<List<AudiobookTrack>>

    fun getTracksByAudiobookIdAsFlow(audiobookId: Long): Flow<List<AudiobookTrack>>

    suspend fun deleteAudiobook(audiobookId: Long, syncId: Long)

    suspend fun insertAudiobook(track: AudiobookTrack)

    suspend fun insertAllAudiobook(tracks: List<AudiobookTrack>)
}
