package tachiyomi.source.local.image.audiobook

import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.audiobooksource.model.SAudiobook
import java.io.InputStream

expect class LocalAudiobookCoverManager {

    fun find(audiobookUrl: String): UniFile?

    fun update(audiobook: SAudiobook, inputStream: InputStream): UniFile?
}
