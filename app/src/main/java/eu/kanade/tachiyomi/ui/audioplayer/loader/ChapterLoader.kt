package eu.kanade.tachiyomi.ui.audioplayer.loader

import eu.kanade.domain.items.audiobookchapter.model.toSChapter
import eu.kanade.tachiyomi.audiobooksource.AudiobookSource
import eu.kanade.tachiyomi.audiobooksource.model.Audio
import eu.kanade.tachiyomi.audiobooksource.online.AudiobookHttpSource
import eu.kanade.tachiyomi.data.download.audiobook.AudiobookDownloadManager
import tachiyomi.domain.entries.audiobook.model.Audiobook
import tachiyomi.domain.items.audiobookchapter.model.Chapter
import tachiyomi.source.local.entries.audiobook.LocalAudiobookSource
import tachiyomi.source.local.io.audiobook.LocalAudiobookSourceFileSystem
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Loader used to retrieve the audio links for a given chapter.
 */
class ChapterLoader {

    companion object {

        private var errorMessage = ""

        /**
         * Returns an observable list of audios of an [chapter] based on the type of [source] used.
         *
         * @param chapter the chapter being parsed.
         * @param audiobook the audiobook of the chapter.
         * @param source the source of the audiobook.
         */
        suspend fun getLinks(chapter: Chapter, audiobook: Audiobook, source: AudiobookSource): List<Audio> {
            val isDownloaded = isDownload(chapter, audiobook)
            return when {
                isDownloaded -> isDownload(chapter, audiobook, source)
                source is AudiobookHttpSource -> isHttp(chapter, source)
                source is LocalAudiobookSource -> isLocal(chapter)
                else -> error("source not supported")
            }
        }

        /**
         * Returns true if the given [chapter] is downloaded.
         *
         * @param chapter the chapter being parsed.
         * @param audiobook the audiobook of the chapter.
         */
        fun isDownload(chapter: Chapter, audiobook: Audiobook): Boolean {
            val downloadManager: AudiobookDownloadManager = Injekt.get()
            return downloadManager.isChapterDownloaded(
                chapter.name,
                chapter.scanlator,
                audiobook.title,
                audiobook.source,
                skipCache = true,
            )
        }

        /**
         * Returns an list of audios when the [chapter] is online.
         *
         * @param chapter the chapter being parsed.
         * @param source the online source of the chapter.
         */
        private suspend fun isHttp(chapter: Chapter, source: AudiobookHttpSource): List<Audio> {
            val audios = source.getAudioList(chapter.toSChapter())

            audios.filter { it.audioUrl.isNullOrEmpty() }.forEach { audio ->
                audio.status = Audio.State.LOAD_AUDIO

                try {
                    audio.audioUrl = source.getAudioUrl(audio)
                } catch (e: Throwable) {
                    audio.status = Audio.State.ERROR
                }
            }

            return audios
        }

        /**
         * Returns an observable list of audios when the [chapter] is downloaded.
         *
         * @param chapter the chapter being parsed.
         * @param audiobook the audiobook of the chapter.
         * @param source the source of the audiobook.
         */
        private fun isDownload(
            chapter: Chapter,
            audiobook: Audiobook,
            source: AudiobookSource,
        ): List<Audio> {
            val downloadManager: AudiobookDownloadManager = Injekt.get()
            return try {
                val audio = downloadManager.buildAudio(source, audiobook, chapter)
                listOf(audio)
            } catch (e: Throwable) {
                emptyList()
            }
        }

        /**
         * Returns an list of audios when the [chapter] is from local source.
         *
         * @param chapter the chapter being parsed.
         */
        private fun isLocal(
            chapter: Chapter,
        ): List<Audio> {
            return try {
                val (audiobookDirName, chapterName) = chapter.url.split('/', limit = 2)
                val fileSystem: LocalAudiobookSourceFileSystem = Injekt.get()
                val audioFile = fileSystem.getBaseDirectory()
                    ?.findFile(audiobookDirName, true)
                    ?.findFile(chapterName, true)
                val audioUri = audioFile!!.uri

                val audio = Audio(
                    audioUri.toString(),
                    "Local source: ${chapter.url}",
                    audioUri.toString(),
                    audioUri,
                )
                listOf(audio)
            } catch (e: Exception) {
                errorMessage = e.message ?: "Error getting links"
                emptyList()
            }
        }
    }
}
