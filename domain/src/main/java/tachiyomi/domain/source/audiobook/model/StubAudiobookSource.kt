package tachiyomi.domain.source.audiobook.model

import eu.kanade.tachiyomi.audiobooksource.AudiobookSource
import eu.kanade.tachiyomi.audiobooksource.model.SAudiobook
import eu.kanade.tachiyomi.audiobooksource.model.SChapter
import eu.kanade.tachiyomi.audiobooksource.model.Audio

@Suppress("OverridingDeprecatedMember")
class StubAudiobookSource(
    override val id: Long,
    override val lang: String,
    override val name: String,
) : AudiobookSource {

    private val isInvalid: Boolean = name.isBlank() || lang.isBlank()

    override suspend fun getAudiobookDetails(audiobook: SAudiobook): SAudiobook =
        throw AudiobookSourceNotInstalledException()

    override suspend fun getChapterList(audiobook: SAudiobook): List<SChapter> =
        throw AudiobookSourceNotInstalledException()

    override suspend fun getAudioList(chapter: SChapter): List<Audio> =
        throw AudiobookSourceNotInstalledException()

    override fun toString(): String =
        if (isInvalid.not()) "$name (${lang.uppercase()})" else id.toString()

    companion object {
        fun from(source: AudiobookSource): StubAudiobookSource {
            return StubAudiobookSource(id = source.id, lang = source.lang, name = source.name)
        }
    }
}
class AudiobookSourceNotInstalledException : Exception()
