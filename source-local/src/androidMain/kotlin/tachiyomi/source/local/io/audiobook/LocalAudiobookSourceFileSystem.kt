package tachiyomi.source.local.io.audiobook

import com.hippo.unifile.UniFile
import tachiyomi.domain.storage.service.StorageManager

actual class LocalAudiobookSourceFileSystem(
    private val storageManager: StorageManager,
) {

    actual fun getBaseDirectory(): UniFile? {
        return storageManager.getLocalAudiobookSourceDirectory()
    }

    actual fun getFilesInBaseDirectory(): List<UniFile> {
        return getBaseDirectory()?.listFiles().orEmpty().toList()
    }

    actual fun getAudiobookDirectory(name: String): UniFile? {
        return getBaseDirectory()
            ?.findFile(name, true)
            ?.takeIf { it.isDirectory }
    }

    actual fun getFilesInAudiobookDirectory(name: String): List<UniFile> {
        return getBaseDirectory()
            ?.findFile(name, true)
            ?.takeIf { it.isDirectory }
            ?.listFiles().orEmpty().toList()
    }
}
