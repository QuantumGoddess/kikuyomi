package tachiyomi.source.local.image.audiobook

import android.content.Context
import android.media.MediaMetadataRetriever
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.audiobooksource.model.SAudiobook
import eu.kanade.tachiyomi.util.storage.DiskUtil
import tachiyomi.core.storage.extension
import tachiyomi.core.storage.nameWithoutExtension
import tachiyomi.core.util.system.ImageUtil
import tachiyomi.source.local.io.audiobook.LocalAudiobookSourceFileSystem
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream


private const val DEFAULT_COVER_NAME = "cover.jpg"

actual class LocalAudiobookCoverManager(
    private val context: Context,
    private val fileSystem: LocalAudiobookSourceFileSystem,
) {

    actual fun find(audiobookUrl: String): UniFile? {
        return fileSystem.getFilesInAudiobookDirectory(audiobookUrl)
            // Get all file whose names start with 'cover'
            .filter { it.isFile && it.nameWithoutExtension.equals("cover", ignoreCase = true) }
            // Get the first actual image
            .firstOrNull { ImageUtil.isImage(it.name) { it.openInputStream() } }
    }

    actual fun update(audiobook: SAudiobook, inputStream: InputStream): UniFile? {
        val directory = fileSystem.getAudiobookDirectory(audiobook.url)
        if (directory == null) {
            inputStream.close()
            return null
        }

        val targetFile = find(audiobook.url) ?: directory.createFile(DEFAULT_COVER_NAME)!!

        inputStream.use { input ->
            targetFile.openOutputStream().use { output ->
                input.copyTo(output)
            }
        }

        DiskUtil.createNoMediaFile(directory, context)

        audiobook.thumbnail_url = targetFile.uri.toString()
        return targetFile
    }


}
