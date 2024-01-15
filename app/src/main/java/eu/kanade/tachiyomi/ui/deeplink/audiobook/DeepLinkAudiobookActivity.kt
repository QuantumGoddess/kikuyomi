package eu.kanade.tachiyomi.ui.deeplink.audiobook

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import eu.kanade.tachiyomi.ui.deeplink.DeepLinkScreenType
import eu.kanade.tachiyomi.ui.main.MainActivity

class DeepLinkAudiobookActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        intent.apply {
            flags = flags or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(MainActivity.INTENT_SEARCH_TYPE, DeepLinkScreenType.AUDIOBOOK.toString())
            setClass(applicationContext, MainActivity::class.java)
        }
        startActivity(intent)
        finish()
    }
}
