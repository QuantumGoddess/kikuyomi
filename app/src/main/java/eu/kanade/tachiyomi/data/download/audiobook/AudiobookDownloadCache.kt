package eu.kanade.tachiyomi.data.download.audiobook

import android.content.Context
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.audiobooksource.AudiobookSource
import eu.kanade.tachiyomi.extension.audiobook.AudiobookExtensionManager
import eu.kanade.tachiyomi.util.size
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import logcat.LogPriority
import tachiyomi.core.storage.extension
import tachiyomi.core.storage.nameWithoutExtension
import tachiyomi.core.util.lang.launchIO
import tachiyomi.core.util.lang.launchNonCancellable
import tachiyomi.core.util.system.logcat
import tachiyomi.domain.entries.audiobook.model.Audiobook
import tachiyomi.domain.items.audiobookchapter.model.Chapter
import tachiyomi.domain.source.audiobook.service.AudiobookSourceManager
import tachiyomi.domain.storage.service.StoragePreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

/**
 * Cache where we dump the downloads directory from the filesystem. This class is needed because
 * directory checking is expensive and it slows downs the app. The cache is invalidated by the time
 * defined in [renewInterval] as we don't have any control over the filesystem and the user can
 * delete the folders at any time without the app noticing.
 */
class AudiobookDownloadCache(
    private val context: Context,
    private val provider: AudiobookDownloadProvider = Injekt.get(),
    private val sourceManager: AudiobookSourceManager = Injekt.get(),
    private val extensionManager: AudiobookExtensionManager = Injekt.get(),
    storagePreferences: StoragePreferences = Injekt.get(),
) {

    private val scope = CoroutineScope(Dispatchers.IO)

    private val _changes: Channel<Unit> = Channel(Channel.UNLIMITED)
    val changes = _changes.receiveAsFlow()
        .onStart { emit(Unit) }
        .shareIn(scope, SharingStarted.Eagerly, 1)

    /**
     * The interval after which this cache should be invalidated. 1 hour shouldn't cause major
     * issues, as the cache is only used for UI feedback.
     */
    private val renewInterval = 1.hours.inWholeMilliseconds

    /**
     * The last time the cache was refreshed.
     */
    private var lastRenew = 0L
    private var renewalJob: Job? = null

    private val _isInitializing = MutableStateFlow(false)
    val isInitializing = _isInitializing
        .debounce(1000L) // Don't notify if it finishes quickly enough
        .stateIn(scope, SharingStarted.WhileSubscribed(), false)

    private val diskCacheFile: File
        get() = File(context.cacheDir, "dl_index_cache_v2")

    private val rootDownloadsDirLock = Mutex()
    private var rootDownloadsDir = RootDirectory(provider.downloadsDir)

    init {
        // Attempt to read cache file
        scope.launch {
            rootDownloadsDirLock.withLock {
                try {
                    val diskCache = diskCacheFile.inputStream().use {
                        ProtoBuf.decodeFromByteArray<RootDirectory>(it.readBytes())
                    }
                    rootDownloadsDir = diskCache
                    lastRenew = System.currentTimeMillis()
                } catch (e: Throwable) {
                    diskCacheFile.delete()
                }
            }
        }

        storagePreferences.baseStorageDirectory().changes()
            .drop(1)
            .onEach {
                rootDownloadsDir = RootDirectory(provider.downloadsDir)
                invalidateCache()
            }
            .launchIn(scope)
    }

    /**
     * Returns true if the chapter is downloaded.
     *
     * @param chapterName the name of the chapter to query.
     * @param chapterScanlator scanlator of the chapter to query
     * @param audiobookTitle the title of the audiobook to query.
     * @param sourceId the id of the source of the chapter.
     */
    fun isChapterDownloaded(
        chapterName: String,
        chapterScanlator: String?,
        audiobookTitle: String,
        sourceId: Long,
        skipCache: Boolean,
    ): Boolean {
        if (skipCache) {
            val source = sourceManager.getOrStub(sourceId)
            return provider.findChapterDir(chapterName, chapterScanlator, audiobookTitle, source) != null
        }

        renewCache()

        val sourceDir = rootDownloadsDir.sourceDirs[sourceId]
        if (sourceDir != null) {
            val audiobookDir = sourceDir.audiobookDirs[provider.getAudiobookDirName(audiobookTitle)]
            if (audiobookDir != null) {
                return provider.getValidChapterDirNames(
                    chapterName,
                    chapterScanlator,
                ).any { it in audiobookDir.chapterDirs }
            }
        }
        return false
    }

    /**
     * Returns the amount of downloaded chapters.
     */
    fun getTotalDownloadCount(): Int {
        renewCache()

        return rootDownloadsDir.sourceDirs.values.sumOf { sourceDir ->
            sourceDir.audiobookDirs.values.sumOf { audiobookDir ->
                audiobookDir.chapterDirs.size
            }
        }
    }

    /**
     * Returns the amount of downloaded chapters for an audiobook.
     *
     * @param audiobook the audiobook to check.
     */
    fun getDownloadCount(audiobook: Audiobook): Int {
        renewCache()

        val sourceDir = rootDownloadsDir.sourceDirs[audiobook.source]
        if (sourceDir != null) {
            val audiobookDir = sourceDir.audiobookDirs[provider.getAudiobookDirName(audiobook.title)]
            if (audiobookDir != null) {
                return audiobookDir.chapterDirs.size
            }
        }
        return 0
    }

    /**
     * Returns the total size of downloaded chapters.
     */
    fun getTotalDownloadSize(): Long {
        renewCache()

        return rootDownloadsDir.sourceDirs.values.sumOf { sourceDir ->
            sourceDir.dir?.size() ?: 0L
        }
    }

    /**
     * Returns the total size of downloaded chapters for an audiobook.
     *
     * @param audiobook the audiobook to check.
     */
    fun getDownloadSize(audiobook: Audiobook): Long {
        renewCache()

        return rootDownloadsDir.sourceDirs[audiobook.source]?.audiobookDirs?.get(
            provider.getAudiobookDirName(
                audiobook.title,
            ),
        )?.dir?.size() ?: 0
    }

    /**
     * Adds an chapter that has just been download to this cache.
     *
     * @param chapterDirName the downloaded chapter's directory name.
     * @param audiobookUniFile the directory of the audiobook.
     * @param audiobook the audiobook of the chapter.
     */
    @Synchronized
    fun addChapter(chapterDirName: String, audiobookUniFile: UniFile, audiobook: Audiobook) {
        // Retrieve the cached source directory or cache a new one
        var sourceDir = rootDownloadsDir.sourceDirs[audiobook.source]
        if (sourceDir == null) {
            val source = sourceManager.get(audiobook.source) ?: return
            val sourceUniFile = provider.findSourceDir(source) ?: return
            sourceDir = SourceDirectory(sourceUniFile)
            rootDownloadsDir.sourceDirs += audiobook.source to sourceDir
        }

        // Retrieve the cached audiobook directory or cache a new one
        val audiobookDirName = provider.getAudiobookDirName(audiobook.title)
        var audiobookDir = sourceDir.audiobookDirs[audiobookDirName]
        if (audiobookDir == null) {
            audiobookDir = AudiobookDirectory(audiobookUniFile)
            sourceDir.audiobookDirs += audiobookDirName to audiobookDir
        }

        // Save the chapter directory
        audiobookDir.chapterDirs += chapterDirName

        notifyChanges()
    }

    /**
     * Removes an chapter that has been deleted from this cache.
     *
     * @param chapter the chapter to remove.
     * @param audiobook the audiobook of the chapter.
     */
    @Synchronized
    fun removeChapter(chapter: Chapter, audiobook: Audiobook) {
        val sourceDir = rootDownloadsDir.sourceDirs[audiobook.source] ?: return
        val audiobookDir = sourceDir.audiobookDirs[provider.getAudiobookDirName(audiobook.title)] ?: return
        provider.getValidChapterDirNames(chapter.name, chapter.scanlator).forEach {
            if (it in audiobookDir.chapterDirs) {
                audiobookDir.chapterDirs -= it
            }
        }
    }

    /**
     * Removes a list of chapters that have been deleted from this cache.
     *
     * @param chapters the list of chapter to remove.
     * @param audiobook the audiobook of the chapter.
     */
    @Synchronized
    fun removeChapters(chapters: List<Chapter>, audiobook: Audiobook) {
        val sourceDir = rootDownloadsDir.sourceDirs[audiobook.source] ?: return
        val audiobookDir = sourceDir.audiobookDirs[provider.getAudiobookDirName(audiobook.title)] ?: return
        chapters.forEach { chapter ->
            provider.getValidChapterDirNames(chapter.name, chapter.scanlator).forEach {
                if (it in audiobookDir.chapterDirs) {
                    audiobookDir.chapterDirs -= it
                }
            }
        }
        notifyChanges()
    }

    /**
     * Removes an audiobook that has been deleted from this cache.
     *
     * @param audiobook the audiobook to remove.
     */
    @Synchronized
    fun removeAudiobook(audiobook: Audiobook) {
        val sourceDir = rootDownloadsDir.sourceDirs[audiobook.source] ?: return
        val audiobookDirName = provider.getAudiobookDirName(audiobook.title)
        if (sourceDir.audiobookDirs.containsKey(audiobookDirName)) {
            sourceDir.audiobookDirs -= audiobookDirName
        }

        notifyChanges()
    }

    fun removeSource(source: AudiobookSource) {
        rootDownloadsDir.sourceDirs -= source.id

        notifyChanges()
    }

    fun invalidateCache() {
        lastRenew = 0L
        renewalJob?.cancel()
    }

    /**
     * Renews the downloads cache.
     */
    private fun renewCache() {
        // Avoid renewing cache if in the process nor too often
        if (lastRenew + renewInterval >= System.currentTimeMillis() || renewalJob?.isActive == true) {
            return
        }

        renewalJob = scope.launchIO {
            if (lastRenew == 0L) {
                _isInitializing.emit(true)
            }

            var sources = getSources()

            // Try to wait until extensions and sources have loaded
            withTimeoutOrNull(30.seconds) {
                while (!extensionManager.isInitialized) {
                    delay(2.seconds)
                }

                while (sources.isEmpty()) {
                    delay(2.seconds)
                    sources = getSources()
                }
            }

            val sourceMap = sources.associate {
                provider.getSourceDirName(it).lowercase() to it.id
            }

            val sourceDirs = rootDownloadsDir.dir?.listFiles().orEmpty()
                .filter { it.isDirectory && !it.name.isNullOrBlank() }
                .mapNotNull { dir ->
                    val sourceId = sourceMap[dir.name!!.lowercase()]
                    sourceId?.let { it to SourceDirectory(dir) }
                }
                .toMap()
                .let { ConcurrentHashMap(it) }

            rootDownloadsDir.sourceDirs = sourceDirs

            sourceDirs.values
                .map { sourceDir ->
                    async {
                        val audiobookDirs = sourceDir.dir?.listFiles().orEmpty()
                            .filter { it.isDirectory && !it.name.isNullOrBlank() }
                            .associate { it.name!! to AudiobookDirectory(it) }

                        sourceDir.audiobookDirs = ConcurrentHashMap(audiobookDirs)

                        audiobookDirs.values.forEach { audiobookDir ->
                            val chapterDirs = audiobookDir.dir?.listFiles().orEmpty()
                                .mapNotNull {
                                    when {
                                        // Ignore incomplete downloads
                                        it.name?.endsWith(AudiobookDownloader.TMP_DIR_SUFFIX) == true -> null
                                        // Folder of audios
                                        it.isDirectory -> it.name
                                        // MP4 files
                                        it.isFile && it.extension == "mp4" -> it.nameWithoutExtension
                                        // MKV files
                                        it.isFile && it.extension == "mkv" -> it.nameWithoutExtension
                                        // Anything else is irrelevant
                                        else -> null
                                    }
                                }
                                .toMutableSet()

                            audiobookDir.chapterDirs = chapterDirs
                        }
                    }
                }
                .awaitAll()

            _isInitializing.emit(false)
        }.also {
            it.invokeOnCompletion(onCancelling = true) { exception ->
                if (exception != null && exception !is CancellationException) {
                    logcat(LogPriority.ERROR, exception) { "Failed to create download cache" }
                }

                lastRenew = System.currentTimeMillis()
                notifyChanges()
            }
        }

        // Mainly to notify the indexing notifier UI
        notifyChanges()
    }

    private fun getSources(): List<AudiobookSource> {
        return sourceManager.getOnlineSources() + sourceManager.getStubSources()
    }

    private fun notifyChanges() {
        scope.launchNonCancellable {
            _changes.send(Unit)
        }
    }
}

/**
 * Class to store the files under the root downloads directory.
 */
private class RootDirectory(
    val dir: UniFile?,
    var sourceDirs: ConcurrentHashMap<Long, SourceDirectory> = ConcurrentHashMap(),
)

/**
 * Class to store the files under a source directory.
 */
private class SourceDirectory(
    val dir: UniFile?,
    var audiobookDirs: ConcurrentHashMap<String, AudiobookDirectory> = ConcurrentHashMap(),
)

/**
 * Class to store the files under a manga directory.
 */
private class AudiobookDirectory(
    val dir: UniFile?,
    var chapterDirs: MutableSet<String> = mutableSetOf(),
)
