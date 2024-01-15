package tachiyomi.source.local.entries.audiobook

import android.content.Context
import android.media.MediaMetadataRetriever
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.audiobooksource.AudiobookCatalogueSource
import eu.kanade.tachiyomi.audiobooksource.AudiobookSource
import eu.kanade.tachiyomi.audiobooksource.UnmeteredSource
import eu.kanade.tachiyomi.audiobooksource.model.AudiobookFilterList
import eu.kanade.tachiyomi.audiobooksource.model.AudiobooksPage
import eu.kanade.tachiyomi.audiobooksource.model.SAudiobook
import eu.kanade.tachiyomi.audiobooksource.model.SChapter
import eu.kanade.tachiyomi.util.lang.compareToCaseInsensitiveNaturalOrder
import eu.kanade.tachiyomi.util.storage.toFFmpegString
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import logcat.LogPriority
import logcat.LogcatLogger
import rx.Observable
import tachiyomi.core.i18n.stringResource
import tachiyomi.core.metadata.tachiyomi.AudiobookDetails
import tachiyomi.core.metadata.tachiyomi.ChapterDetails
import tachiyomi.core.storage.extension
import tachiyomi.core.storage.nameWithoutExtension
import tachiyomi.core.util.lang.withIOContext
import tachiyomi.core.util.system.logcat
import tachiyomi.domain.entries.audiobook.model.Audiobook
import tachiyomi.domain.items.audiobookchapter.service.ChapterRecognition
import tachiyomi.i18n.MR
import tachiyomi.source.local.filter.audiobook.AudiobookOrderBy
import tachiyomi.source.local.image.audiobook.LocalAudiobookCoverManager
import tachiyomi.source.local.io.ArchiveAudiobook
import tachiyomi.source.local.io.audiobook.LocalAudiobookSourceFileSystem
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs

actual class LocalAudiobookSource(
    private val context: Context,
    private val fileSystem: LocalAudiobookSourceFileSystem,
    private val coverManager: LocalAudiobookCoverManager,
) : AudiobookCatalogueSource, UnmeteredSource {

    private val json: Json by injectLazy()

    private val POPULAR_FILTERS = AudiobookFilterList(AudiobookOrderBy.Popular(context))
    private val LATEST_FILTERS = AudiobookFilterList(AudiobookOrderBy.Latest(context))

    override val name = context.stringResource(MR.strings.local_audiobook_source)

    override val id: Long = ID

    override val lang = "other"

    override fun toString() = name

    override val supportsLatest = true

    // Browse related
    override suspend fun getPopularAudiobook(page: Int) = getSearchAudiobook(page, "", POPULAR_FILTERS)

    override suspend fun getLatestUpdates(page: Int) = getSearchAudiobook(page, "", LATEST_FILTERS)

    override suspend fun getSearchAudiobook(
        page: Int,
        query: String,
        filters: AudiobookFilterList,
    ): AudiobooksPage = withIOContext {
        val lastModifiedLimit = if (filters === LATEST_FILTERS) {
            System.currentTimeMillis() - LATEST_THRESHOLD
        } else {
            0L
        }

        var audiobookDirs = fileSystem.getFilesInBaseDirectory()
            // Filter out files that are hidden and is not a folder
            .filter { it.isDirectory && !it.name.orEmpty().startsWith('.') }
            .distinctBy { it.name }
            .filter {
                if (lastModifiedLimit == 0L && query.isBlank()) {
                    true
                } else if (lastModifiedLimit == 0L) {
                    it.name.orEmpty().contains(query, ignoreCase = true)
                } else {
                    it.lastModified() >= lastModifiedLimit
                }
            }

        filters.forEach { filter ->
            when (filter) {
                is AudiobookOrderBy.Popular -> {
                    audiobookDirs = if (filter.state!!.ascending) {
                        audiobookDirs.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name.orEmpty() })
                    } else {
                        audiobookDirs.sortedWith(
                            compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.name.orEmpty() },
                        )
                    }
                }
                is AudiobookOrderBy.Latest -> {
                    audiobookDirs = if (filter.state!!.ascending) {
                        audiobookDirs.sortedBy(UniFile::lastModified)
                    } else {
                        audiobookDirs.sortedByDescending(UniFile::lastModified)
                    }
                }
                else -> {
                    /* Do nothing */
                }
            }
        }

        // Transform audiobookDirs to list of SAudiobook
        val audiobooks = audiobookDirs
            .map { audiobookDir ->
                async {
                    SAudiobook.create().apply {
                        title = audiobookDir.name.orEmpty()
                        url = audiobookDir.name.orEmpty()
                        getChapterList(this)
                        // Try to find the cover
                        coverManager.find(audiobookDir.name.orEmpty())?.let {
                            thumbnail_url = it.uri.toString()
                        }
                    }
                }
            }
            .awaitAll()

        AudiobooksPage(audiobooks.toList(), false)
    }

    // Old fetch functions

    // TODO: Should be replaced when Audiobook Extensions get to 1.15

    @Deprecated("Use the non-RxJava API instead", replaceWith = ReplaceWith("getPopularAudiobook"))
    override fun fetchPopularAudiobook(page: Int) = fetchSearchAudiobook(page, "", POPULAR_FILTERS)

    @Deprecated("Use the non-RxJava API instead", replaceWith = ReplaceWith("getLatestUpdates"))
    override fun fetchLatestUpdates(page: Int) = fetchSearchAudiobook(page, "", LATEST_FILTERS)

    @Deprecated("Use the non-RxJava API instead", replaceWith = ReplaceWith("getSearchAudiobook"))
    override fun fetchSearchAudiobook(page: Int, query: String, filters: AudiobookFilterList): Observable<AudiobooksPage> {
        return runBlocking {
            Observable.just(getSearchAudiobook(page, query, filters))
        }
    }

    // Audiobook details related
    override suspend fun getAudiobookDetails(audiobook: SAudiobook): SAudiobook = withIOContext {
        coverManager.find(audiobook.url)?.let {
            audiobook.thumbnail_url = it.uri.toString()
        }

        val audiobookDirFiles = fileSystem.getFilesInAudiobookDirectory(audiobook.url)

        audiobookDirFiles
            .firstOrNull { it.extension == "json" && it.nameWithoutExtension == "details" }
            ?.let { file ->
                json.decodeFromStream<AudiobookDetails>(file.openInputStream()).run {
                    title?.let { audiobook.title = it }
                    author?.let { audiobook.author = it }
                    artist?.let { audiobook.artist = it }
                    description?.let { audiobook.description = it }
                    genre?.let { audiobook.genre = it.joinToString() }
                    status?.let { audiobook.status = it }
                }
            }

        return@withIOContext audiobook
    }

    // Chapters
    override suspend fun getChapterList(audiobook: SAudiobook): List<SChapter> = withIOContext {
        val chaptersData = fileSystem.getFilesInAudiobookDirectory(audiobook.url)
            .firstOrNull {
                it.extension == "json" && it.nameWithoutExtension == "chapters"
            }?.let { file ->
                runCatching {
                    json.decodeFromStream<List<ChapterDetails>>(file.openInputStream())
                }.getOrNull()
            }

        val chapters = fileSystem.getFilesInAudiobookDirectory(audiobook.url)
            // Only keep supported formats
            .filter { ArchiveAudiobook.isSupported(it) }
            .map { chapterFile ->
                SChapter.create().apply {
                    url = "${audiobook.url}/${chapterFile.name}"
                    name = chapterFile.nameWithoutExtension.orEmpty()
                    date_upload = chapterFile.lastModified()

                    val chapterNumber = ChapterRecognition.parseChapterNumber(
                        audiobook.title,
                        this.name,
                        this.chapter_number.toDouble(),
                    ).toFloat()
                    chapter_number = chapterNumber

                    // Overwrite data from chapters.json file
                    chaptersData?.also { dataList ->
                        dataList.firstOrNull { it.chapter_number.equalsTo(chapterNumber) }?.also { data ->
                            data.name?.also { name = it }
                            data.date_upload?.also { date_upload = parseDate(it) }
                            scanlator = data.scanlator
                        }
                    }
                }
            }
            .sortedWith { e1, e2 ->
                val e = e2.chapter_number.compareTo(e1.chapter_number)
                if (e == 0) e2.name.compareToCaseInsensitiveNaturalOrder(e1.name) else e
            }

        // Generate the cover from the first chapter found if not available
        try {
            chapters.lastOrNull()?.let { chapter ->
                updateDetailsFromAudio(chapter, audiobook)
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) { "Couldn't extract thumbnail from audio: $e" }
        }


        chapters
    }

    private fun parseDate(isoDate: String): Long {
        return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).parse(isoDate)?.time ?: 0L
    }

    private fun Float.equalsTo(other: Float): Boolean {
        return abs(this - other) < 0.0001
    }

    // Filters
    override fun getFilterList() = AudiobookFilterList(AudiobookOrderBy.Popular(context))

    // Unused stuff
    override suspend fun getAudioList(chapter: SChapter) = throw UnsupportedOperationException(
        "Unused",
    )

    private fun updateDetailsFromAudio(chapter: SChapter, audiobook: SAudiobook) {
        val tempFile = File.createTempFile(
            "tmp_",
            audiobook.title + DEFAULT_COVER_NAME,
        )
        val outFile = tempFile.path
        val chapterName = chapter.url.split('/', limit = 2).last()
        val audiobookDir = fileSystem.getAudiobookDirectory(audiobook.url)!!
        val chapterFile = audiobookDir.findFile(chapterName)!!
        val chapterFilename = { chapterFile.toFFmpegString(context) }
        var ffProbe = com.arthenica.ffmpegkit.FFprobeKit.execute(
            "-v error -show_entries format=duration -of default=noprint_wrappers=1:nokey=1 \"${chapterFilename()}\"",
        )
        val duration = ffProbe.allLogsAsString.trim().toFloat()
        val second = duration.toInt() / 2

        com.arthenica.ffmpegkit.FFmpegKit.execute(
            "-i \"${chapterFilename()}\" -frames:v 1 -update true \"$outFile\" -y",
        )

        if (tempFile.length() > 0L) {
            coverManager.update(audiobook, tempFile.inputStream())
        }

        ffProbe = com.arthenica.ffmpegkit.FFprobeKit.execute(
            "\"${chapterFilename()}\" -show_entries format_tags=album -of compact=p=0:nk=1 -v 0",
        )
        ffProbe.allLogsAsString.trim().takeIf { it.isNotEmpty() } ?.let { audiobook.title = it }

        ffProbe = com.arthenica.ffmpegkit.FFprobeKit.execute(
            "\"${chapterFilename()}\" -show_entries format_tags=artist -of compact=p=0:nk=1 -v 0",
        )
        ffProbe.allLogsAsString.trim().takeIf { it.isNotEmpty() } ?.let { audiobook.author = it }

        ffProbe = com.arthenica.ffmpegkit.FFprobeKit.execute(
            "\"${chapterFilename()}\" -show_entries format_tags=genre -of compact=p=0:nk=1 -v 0",
        )
        ffProbe.allLogsAsString.trim().takeIf { it.isNotEmpty() } ?.let { audiobook.genre = it }

    }

    companion object {
        const val ID = 0L
        const val HELP_URL = "https://kikuyomi.org/help/guides/local-audiobook/"

        private const val DEFAULT_COVER_NAME = "cover.jpg"
        private val LATEST_THRESHOLD = TimeUnit.MILLISECONDS.convert(7, TimeUnit.DAYS)
    }
}

fun Audiobook.isLocal(): Boolean = source == LocalAudiobookSource.ID

fun AudiobookSource.isLocal(): Boolean = id == LocalAudiobookSource.ID
