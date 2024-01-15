package tachiyomi.domain.entries.audiobook.interactor

import tachiyomi.domain.entries.audiobook.model.Audiobook
import tachiyomi.domain.entries.audiobook.model.AudiobookUpdate
import tachiyomi.domain.items.audiobookchapter.interactor.GetChaptersByAudiobookId
import tachiyomi.domain.items.audiobookchapter.model.Chapter
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.absoluteValue

class AudiobookFetchInterval(
    private val getChaptersByAudiobookId: GetChaptersByAudiobookId,
) {

    suspend fun toAudiobookUpdateOrNull(
        audiobook: Audiobook,
        dateTime: ZonedDateTime,
        window: Pair<Long, Long>,
    ): AudiobookUpdate? {
        val currentWindow = if (window.first == 0L && window.second == 0L) {
            getWindow(ZonedDateTime.now())
        } else {
            window
        }
        val chapters = getChaptersByAudiobookId.await(audiobook.id)
        val interval = audiobook.fetchInterval.takeIf { it < 0 } ?: calculateInterval(
            chapters,
            dateTime.zone,
        )
        val nextUpdate = calculateNextUpdate(audiobook, interval, dateTime, currentWindow)

        return if (audiobook.nextUpdate == nextUpdate && audiobook.fetchInterval == interval) {
            null
        } else {
            AudiobookUpdate(id = audiobook.id, nextUpdate = nextUpdate, fetchInterval = interval)
        }
    }

    fun getWindow(dateTime: ZonedDateTime): Pair<Long, Long> {
        val today = dateTime.toLocalDate().atStartOfDay(dateTime.zone)
        val lowerBound = today.minusDays(GRACE_PERIOD)
        val upperBound = today.plusDays(GRACE_PERIOD)
        return Pair(lowerBound.toEpochSecond() * 1000, upperBound.toEpochSecond() * 1000 - 1)
    }

    internal fun calculateInterval(chapters: List<Chapter>, zone: ZoneId): Int {
        val uploadDates = chapters.asSequence()
            .filter { it.dateUpload > 0L }
            .sortedByDescending { it.dateUpload }
            .map {
                ZonedDateTime.ofInstant(Instant.ofEpochMilli(it.dateUpload), zone)
                    .toLocalDate()
                    .atStartOfDay()
            }
            .distinct()
            .take(10)
            .toList()

        val fetchDates = chapters.asSequence()
            .sortedByDescending { it.dateFetch }
            .map {
                ZonedDateTime.ofInstant(Instant.ofEpochMilli(it.dateFetch), zone)
                    .toLocalDate()
                    .atStartOfDay()
            }
            .distinct()
            .take(10)
            .toList()

        val interval = when {
            // Enough upload date from source
            uploadDates.size >= 3 -> {
                val uploadDelta = uploadDates.last().until(uploadDates.first(), ChronoUnit.DAYS)
                val uploadPeriod = uploadDates.indexOf(uploadDates.last())
                uploadDelta.floorDiv(uploadPeriod).toInt()
            }
            // Enough fetch date from client
            fetchDates.size >= 3 -> {
                val fetchDelta = fetchDates.last().until(fetchDates.first(), ChronoUnit.DAYS)
                val uploadPeriod = fetchDates.indexOf(fetchDates.last())
                fetchDelta.floorDiv(uploadPeriod).toInt()
            }
            // Default to 7 days
            else -> 7
        }

        return interval.coerceIn(1, MAX_INTERVAL)
    }

    private fun calculateNextUpdate(
        audiobook: Audiobook,
        interval: Int,
        dateTime: ZonedDateTime,
        window: Pair<Long, Long>,
    ): Long {
        return if (
            audiobook.nextUpdate !in window.first.rangeTo(window.second + 1) ||
            audiobook.fetchInterval == 0
        ) {
            val latestDate = ZonedDateTime.ofInstant(
                Instant.ofEpochMilli(audiobook.lastUpdate),
                dateTime.zone,
            )
                .toLocalDate()
                .atStartOfDay()
            val timeSinceLatest = ChronoUnit.DAYS.between(latestDate, dateTime).toInt()
            val cycle = timeSinceLatest.floorDiv(
                interval.absoluteValue.takeIf { interval < 0 }
                    ?: doubleInterval(interval, timeSinceLatest, doubleWhenOver = 10),
            )
            latestDate.plusDays((cycle + 1) * interval.toLong()).toEpochSecond(dateTime.offset) * 1000
        } else {
            audiobook.nextUpdate
        }
    }

    private fun doubleInterval(delta: Int, timeSinceLatest: Int, doubleWhenOver: Int): Int {
        if (delta >= MAX_INTERVAL) return MAX_INTERVAL

        // double delta again if missed more than 9 check in new delta
        val cycle = timeSinceLatest.floorDiv(delta) + 1
        return if (cycle > doubleWhenOver) {
            doubleInterval(delta * 2, timeSinceLatest, doubleWhenOver)
        } else {
            delta
        }
    }

    companion object {
        const val MAX_INTERVAL = 28

        private const val GRACE_PERIOD = 1L
    }
}
