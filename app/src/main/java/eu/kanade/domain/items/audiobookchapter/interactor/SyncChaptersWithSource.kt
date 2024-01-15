package eu.kanade.domain.items.audiobookchapter.interactor

import eu.kanade.domain.entries.audiobook.interactor.UpdateAudiobook
import eu.kanade.domain.entries.audiobook.model.toSAudiobook
import eu.kanade.domain.items.audiobookchapter.model.copyFromSChapter
import eu.kanade.domain.items.audiobookchapter.model.toSChapter
import eu.kanade.tachiyomi.audiobooksource.AudiobookSource
import eu.kanade.tachiyomi.audiobooksource.model.SChapter
import eu.kanade.tachiyomi.audiobooksource.online.AudiobookHttpSource
import eu.kanade.tachiyomi.data.download.audiobook.AudiobookDownloadManager
import eu.kanade.tachiyomi.data.download.audiobook.AudiobookDownloadProvider
import tachiyomi.data.items.chapter.ChapterSanitizer
import tachiyomi.domain.entries.audiobook.model.Audiobook
import tachiyomi.domain.items.audiobookchapter.interactor.GetChaptersByAudiobookId
import tachiyomi.domain.items.audiobookchapter.interactor.ShouldUpdateDbChapter
import tachiyomi.domain.items.audiobookchapter.interactor.UpdateChapter
import tachiyomi.domain.items.audiobookchapter.model.Chapter
import tachiyomi.domain.items.audiobookchapter.model.NoChaptersException
import tachiyomi.domain.items.audiobookchapter.model.toChapterUpdate
import tachiyomi.domain.items.audiobookchapter.repository.ChapterRepository
import tachiyomi.domain.items.audiobookchapter.service.ChapterRecognition
import tachiyomi.source.local.entries.audiobook.isLocal
import java.lang.Long.max
import java.time.Instant
import java.time.ZonedDateTime
import java.util.TreeSet

class SyncChaptersWithSource(
    private val downloadManager: AudiobookDownloadManager,
    private val downloadProvider: AudiobookDownloadProvider,
    private val chapterRepository: ChapterRepository,
    private val shouldUpdateDbChapter: ShouldUpdateDbChapter,
    private val updateAudiobook: UpdateAudiobook,
    private val updateChapter: UpdateChapter,
    private val getChaptersByAudiobookId: GetChaptersByAudiobookId,
) {

    /**
     * Method to synchronize db chapters with source ones
     *
     * @param rawSourceChapters the chapters from the source.
     * @param audiobook the audiobook the chapters belong to.
     * @param source the source the audiobook belongs to.
     * @return Newly added chapters
     */
    suspend fun await(
        rawSourceChapters: List<SChapter>,
        audiobook: Audiobook,
        source: AudiobookSource,
        manualFetch: Boolean = false,
        fetchWindow: Pair<Long, Long> = Pair(0, 0),
    ): List<Chapter> {
        if (rawSourceChapters.isEmpty() && !source.isLocal()) {
            throw NoChaptersException()
        }

        val now = ZonedDateTime.now()

        val sourceChapters = rawSourceChapters
            .distinctBy { it.url }
            .mapIndexed { i, sChapter ->
                Chapter.create()
                    .copyFromSChapter(sChapter)
                    .copy(name = with(ChapterSanitizer) { sChapter.name.sanitize(audiobook.title) })
                    .copy(audiobookId = audiobook.id, sourceOrder = i.toLong())
            }

        // Chapters from db.
        val dbChapters = getChaptersByAudiobookId.await(audiobook.id)

        // Chapters from the source not in db.
        val toAdd = mutableListOf<Chapter>()

        // Chapters whose metadata have changed.
        val toChange = mutableListOf<Chapter>()

        // Chapters from the db not in source.
        val toDelete = dbChapters.filterNot { dbChapter ->
            sourceChapters.any { sourceChapter ->
                dbChapter.url == sourceChapter.url
            }
        }

        val rightNow = Instant.now().toEpochMilli()

        // Used to not set upload date of older chapters
        // to a higher value than newer chapters
        var maxReadUploadDate = 0L

        val sAudiobook = audiobook.toSAudiobook()
        for (sourceChapter in sourceChapters) {
            var chapter = sourceChapter

            // Update metadata from source if necessary.
            if (source is AudiobookHttpSource) {
                val sChapter = chapter.toSChapter()
                source.prepareNewChapter(sChapter, sAudiobook)
                chapter = chapter.copyFromSChapter(sChapter)
            }

            // Recognize chapter number for the chapter.
            val chapterNumber = ChapterRecognition.parseChapterNumber(
                audiobook.title,
                chapter.name,
                chapter.chapterNumber,
            )
            chapter = chapter.copy(chapterNumber = chapterNumber)

            val dbChapter = dbChapters.find { it.url == chapter.url }

            if (dbChapter == null) {
                val toAddChapter = if (chapter.dateUpload == 0L) {
                    val altDateUpload = if (maxReadUploadDate == 0L) rightNow else maxReadUploadDate
                    chapter.copy(dateUpload = altDateUpload)
                } else {
                    maxReadUploadDate = max(maxReadUploadDate, sourceChapter.dateUpload)
                    chapter
                }
                toAdd.add(toAddChapter)
            } else {
                if (shouldUpdateDbChapter.await(dbChapter, chapter)) {
                    val shouldRenameChapter = downloadProvider.isChapterDirNameChanged(
                        dbChapter,
                        chapter,
                    ) &&
                        downloadManager.isChapterDownloaded(
                            dbChapter.name,
                            dbChapter.scanlator,
                            audiobook.title,
                            audiobook.source,
                        )

                    if (shouldRenameChapter) {
                        downloadManager.renameChapter(source, audiobook, dbChapter, chapter)
                    }
                    var toChangeChapter = dbChapter.copy(
                        name = chapter.name,
                        chapterNumber = chapter.chapterNumber,
                        scanlator = chapter.scanlator,
                        sourceOrder = chapter.sourceOrder,
                    )
                    if (chapter.dateUpload != 0L) {
                        toChangeChapter = toChangeChapter.copy(
                            dateUpload = sourceChapter.dateUpload,
                        )
                    }
                    toChange.add(toChangeChapter)
                }
            }
        }

        // Return if there's nothing to add, delete or change, avoiding unnecessary db transactions.
        if (toAdd.isEmpty() && toDelete.isEmpty() && toChange.isEmpty()) {
            if (manualFetch || audiobook.fetchInterval == 0 || audiobook.nextUpdate < fetchWindow.first) {
                updateAudiobook.awaitUpdateFetchInterval(
                    audiobook,
                    now,
                    fetchWindow,
                )
            }
            return emptyList()
        }

        val reAdded = mutableListOf<Chapter>()

        val deletedChapterNumbers = TreeSet<Double>()
        val deletedReadChapterNumbers = TreeSet<Double>()
        val deletedBookmarkedChapterNumbers = TreeSet<Double>()

        toDelete.forEach { chapter ->
            if (chapter.read) deletedReadChapterNumbers.add(chapter.chapterNumber)
            if (chapter.bookmark) deletedBookmarkedChapterNumbers.add(chapter.chapterNumber)
            deletedChapterNumbers.add(chapter.chapterNumber)
        }

        val deletedChapterNumberDateFetchMap = toDelete.sortedByDescending { it.dateFetch }
            .associate { it.chapterNumber to it.dateFetch }

        // Date fetch is set in such a way that the upper ones will have bigger value than the lower ones
        // Sources MUST return the chapters from most to less recent, which is common.
        var itemCount = toAdd.size
        var updatedToAdd = toAdd.map { toAddItem ->
            var chapter = toAddItem.copy(dateFetch = rightNow + itemCount--)

            if (chapter.isRecognizedNumber.not() || chapter.chapterNumber !in deletedChapterNumbers) return@map chapter

            chapter = chapter.copy(
                read = chapter.chapterNumber in deletedReadChapterNumbers,
                bookmark = chapter.chapterNumber in deletedBookmarkedChapterNumbers,
            )

            // Try to to use the fetch date of the original entry to not pollute 'Updates' tab
            deletedChapterNumberDateFetchMap[chapter.chapterNumber]?.let {
                chapter = chapter.copy(dateFetch = it)
            }

            reAdded.add(chapter)

            chapter
        }

        if (toDelete.isNotEmpty()) {
            val toDeleteIds = toDelete.map { it.id }
            chapterRepository.removeChaptersWithIds(toDeleteIds)
        }

        if (updatedToAdd.isNotEmpty()) {
            updatedToAdd = chapterRepository.addAllChapters(updatedToAdd)
        }

        if (toChange.isNotEmpty()) {
            val chapterUpdates = toChange.map { it.toChapterUpdate() }
            updateChapter.awaitAll(chapterUpdates)
        }
        updateAudiobook.awaitUpdateFetchInterval(audiobook, now, fetchWindow)

        // Set this audiobook as updated since chapters were changed
        // Note that last_update actually represents last time the chapter list changed at all
        updateAudiobook.awaitUpdateLastUpdate(audiobook.id)

        val reAddedUrls = reAdded.map { it.url }.toHashSet()

        return updatedToAdd.filterNot { it.url in reAddedUrls }
    }
}
