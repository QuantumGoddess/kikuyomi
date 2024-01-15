package tachiyomi.data.handlers.audiobook

import androidx.paging.PagingSource
import app.cash.sqldelight.ExecutableQuery
import app.cash.sqldelight.Query
import kikuyomi.data.AudiobookDatabase
import kotlinx.coroutines.flow.Flow

interface AudiobookDatabaseHandler {

    suspend fun <T> await(inTransaction: Boolean = false, block: suspend AudiobookDatabase.() -> T): T

    suspend fun <T : Any> awaitList(
        inTransaction: Boolean = false,
        block: suspend AudiobookDatabase.() -> Query<T>,
    ): List<T>

    suspend fun <T : Any> awaitOne(
        inTransaction: Boolean = false,
        block: suspend AudiobookDatabase.() -> Query<T>,
    ): T

    suspend fun <T : Any> awaitOneExecutable(
        inTransaction: Boolean = false,
        block: suspend AudiobookDatabase.() -> ExecutableQuery<T>,
    ): T

    suspend fun <T : Any> awaitOneOrNull(
        inTransaction: Boolean = false,
        block: suspend AudiobookDatabase.() -> Query<T>,
    ): T?

    suspend fun <T : Any> awaitOneOrNullExecutable(
        inTransaction: Boolean = false,
        block: suspend AudiobookDatabase.() -> ExecutableQuery<T>,
    ): T?

    fun <T : Any> subscribeToList(block: AudiobookDatabase.() -> Query<T>): Flow<List<T>>

    fun <T : Any> subscribeToOne(block: AudiobookDatabase.() -> Query<T>): Flow<T>

    fun <T : Any> subscribeToOneOrNull(block: AudiobookDatabase.() -> Query<T>): Flow<T?>

    fun <T : Any> subscribeToPagingSource(
        countQuery: AudiobookDatabase.() -> Query<Long>,
        queryProvider: AudiobookDatabase.(Long, Long) -> Query<T>,
    ): PagingSource<Long, T>
}
