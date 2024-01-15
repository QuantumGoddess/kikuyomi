package eu.kanade.tachiyomi.source.audiobook

import android.graphics.drawable.Drawable
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.audiobooksource.AudiobookSource
import eu.kanade.tachiyomi.extension.audiobook.AudiobookExtensionManager
import tachiyomi.domain.source.audiobook.model.StubAudiobookSource
import tachiyomi.source.local.entries.audiobook.isLocal
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

fun AudiobookSource.icon(): Drawable? = Injekt.get<AudiobookExtensionManager>().getAppIconForSource(this.id)

fun AudiobookSource.getPreferenceKey(): String = "source_$id"

fun AudiobookSource.toStubSource(): StubAudiobookSource = StubAudiobookSource(id = id, lang = lang, name = name)

fun AudiobookSource.getNameForAudiobookInfo(): String {
    val preferences = Injekt.get<SourcePreferences>()
    val enabledLanguages = preferences.enabledLanguages().get()
        .filterNot { it in listOf("all", "other") }
    val hasOneActiveLanguages = enabledLanguages.size == 1
    val isInEnabledLanguages = lang in enabledLanguages
    return when {
        // For edge cases where user disables a source they got manga of in their library.
        hasOneActiveLanguages && !isInEnabledLanguages -> toString()
        // Hide the language tag when only one language is used.
        hasOneActiveLanguages && isInEnabledLanguages -> name
        else -> toString()
    }
}

fun AudiobookSource.isLocalOrStub(): Boolean = isLocal() || this is StubAudiobookSource
