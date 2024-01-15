package eu.kanade.domain.source.audiobook.interactor

import eu.kanade.domain.source.interactor.SetMigrateSorting
import eu.kanade.domain.source.service.SourcePreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import tachiyomi.core.util.lang.compareToWithCollator
import tachiyomi.domain.source.audiobook.model.AudiobookSource
import tachiyomi.domain.source.audiobook.repository.AudiobookSourceRepository
import tachiyomi.source.local.entries.audiobook.LocalAudiobookSource
import java.util.Collections

class GetAudiobookSourcesWithFavoriteCount(
    private val repository: AudiobookSourceRepository,
    private val preferences: SourcePreferences,
) {

    fun subscribe(): Flow<List<Pair<AudiobookSource, Long>>> {
        return combine(
            preferences.migrationSortingDirection().changes(),
            preferences.migrationSortingMode().changes(),
            repository.getAudiobookSourcesWithFavoriteCount(),
        ) { direction, mode, list ->
            list
                .filterNot { it.first.id == LocalAudiobookSource.ID }
                .sortedWith(sortFn(direction, mode))
        }
    }

    private fun sortFn(
        direction: SetMigrateSorting.Direction,
        sorting: SetMigrateSorting.Mode,
    ): java.util.Comparator<Pair<AudiobookSource, Long>> {
        val sortFn: (Pair<AudiobookSource, Long>, Pair<AudiobookSource, Long>) -> Int = { a, b ->
            when (sorting) {
                SetMigrateSorting.Mode.ALPHABETICAL -> {
                    when {
                        a.first.isStub && b.first.isStub.not() -> -1
                        b.first.isStub && a.first.isStub.not() -> 1
                        else -> a.first.name.lowercase().compareToWithCollator(b.first.name.lowercase())
                    }
                }
                SetMigrateSorting.Mode.TOTAL -> {
                    when {
                        a.first.isStub && b.first.isStub.not() -> -1
                        b.first.isStub && a.first.isStub.not() -> 1
                        else -> a.second.compareTo(b.second)
                    }
                }
            }
        }

        return when (direction) {
            SetMigrateSorting.Direction.ASCENDING -> Comparator(sortFn)
            SetMigrateSorting.Direction.DESCENDING -> Collections.reverseOrder(sortFn)
        }
    }
}
