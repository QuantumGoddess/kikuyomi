package eu.kanade.tachiyomi.extension.audiobook.model

sealed interface AudiobookLoadResult {
    data class Success(val extension: AudiobookExtension.Installed) : AudiobookLoadResult
    data class Untrusted(val extension: AudiobookExtension.Untrusted) : AudiobookLoadResult
    data object Error : AudiobookLoadResult
}
