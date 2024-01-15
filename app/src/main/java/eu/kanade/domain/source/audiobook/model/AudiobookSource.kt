package eu.kanade.domain.source.audiobook.model

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import eu.kanade.tachiyomi.extension.audiobook.AudiobookExtensionManager
import tachiyomi.domain.source.audiobook.model.AudiobookSource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

val AudiobookSource.icon: ImageBitmap?
    get() {
        return Injekt.get<AudiobookExtensionManager>().getAppIconForSource(id)
            ?.toBitmap()
            ?.asImageBitmap()
    }
