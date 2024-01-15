package tachiyomi.source.local.io.audiobook

import com.hippo.unifile.UniFile

expect class LocalAudiobookSourceFileSystem {

    fun getBaseDirectory(): UniFile?

    fun getFilesInBaseDirectory(): List<UniFile>

    fun getAudiobookDirectory(name: String): UniFile?

    fun getFilesInAudiobookDirectory(name: String): List<UniFile>
}
