package tachiyomi.domain.storage.service

import android.content.Context
import androidx.core.net.toUri
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.util.storage.DiskUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class StorageManager(
    private val context: Context,
    storagePreferences: StoragePreferences,
) {

    private val scope = CoroutineScope(Dispatchers.IO)

    private var baseDir: UniFile? = storagePreferences.baseStorageDirectory().get().let(::getBaseDir)

    init {
        storagePreferences.baseStorageDirectory().changes()
            .onEach { baseDir = getBaseDir(it) }
            .launchIn(scope)
    }

    private fun getBaseDir(path: String): UniFile? {
        val file = UniFile.fromUri(context, path.toUri())

        return file.takeIf { it?.exists() == true }?.also { parent ->
            parent.createDirectory(AUTOMATIC_BACKUPS_PATH)
            parent.createDirectory(LOCAL_SOURCE_PATH)
            parent.createDirectory(LOCAL_ANIMESOURCE_PATH)
            parent.createDirectory(LOCAL_AUDIOBOOKSOURCE_PATH)
            parent.createDirectory(FONTS_PATH)
            parent.createDirectory(DOWNLOADS_PATH).also {
                DiskUtil.createNoMediaFile(it, context)
            }
        }
    }

    fun getAutomaticBackupsDirectory(): UniFile? {
        return baseDir?.createDirectory(AUTOMATIC_BACKUPS_PATH)
    }

    fun getDownloadsDirectory(): UniFile? {
        return baseDir?.createDirectory(DOWNLOADS_PATH)
    }

    fun getLocalMangaSourceDirectory(): UniFile? {
        return baseDir?.createDirectory(LOCAL_SOURCE_PATH)
    }

    fun getLocalAnimeSourceDirectory(): UniFile? {
        return baseDir?.createDirectory(LOCAL_ANIMESOURCE_PATH)
    }

    fun getLocalAudiobookSourceDirectory(): UniFile? {
        return baseDir?.createDirectory(LOCAL_AUDIOBOOKSOURCE_PATH)
    }

    fun getFontsDirectory(): UniFile? {
        return baseDir?.createDirectory(FONTS_PATH)
    }
}

private const val AUTOMATIC_BACKUPS_PATH = "autobackup"
private const val DOWNLOADS_PATH = "downloads"
private const val LOCAL_SOURCE_PATH = "local"
private const val LOCAL_ANIMESOURCE_PATH = "localanime"
private const val LOCAL_AUDIOBOOKSOURCE_PATH = "localaudiobooks"
private const val FONTS_PATH = "fonts"
