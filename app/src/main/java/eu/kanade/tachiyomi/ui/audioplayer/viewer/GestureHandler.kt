package eu.kanade.tachiyomi.ui.audioplayer.viewer

import android.annotation.SuppressLint
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import eu.kanade.tachiyomi.ui.audioplayer.AudioplayerActivity
import eu.kanade.tachiyomi.ui.audioplayer.settings.AudioplayerPreferences
import eu.kanade.tachiyomi.ui.audioplayer.viewer.SeekState
import uy.kohesive.injekt.injectLazy
import kotlin.math.abs

class GestureHandler(
    private val activity: AudioplayerActivity,
    private val width: Float,
    private val height: Float,
) : GestureDetector.SimpleOnGestureListener(), View.OnTouchListener {
    private var scrollState = STATE_UP

    private val trigger = width.coerceAtMost(height) / 25

    private val preferences: AudioplayerPreferences by injectLazy()

    val interval = preferences.skipLengthPreference().get()

    override fun onDown(event: MotionEvent): Boolean {
        return true
    }

    private val playerControls = activity.playerControls

    private var scrollDiff: Float? = null

    override fun onScroll(
        e1: MotionEvent?,
        e2: MotionEvent,
        distanceX: Float,
        distanceY: Float,
    ): Boolean {
        if (e1 != null) {
            if (SeekState.mode == SeekState.LOCKED) {
                return false
            }
            if (e1.y < height * 0.05F || e1.y > height * 0.95F) return false
            val dx = e1.x - e2.x
            val dy = e1.y - e2.y
            when (scrollState) {

                STATE_VERTICAL_LEFT -> {
                    val diff = 1.5F * distanceY / height
                    if (preferences.gestureVolumeBrightness().get()) {
                        activity.verticalScrollLeft(
                            diff,
                        )
                    }
                }

                STATE_VERTICAL_RIGHT -> {
                    val diff = 1.5F * distanceY / height
                    if (preferences.gestureVolumeBrightness().get()) {
                        activity.verticalScrollRight(
                            diff,
                        )
                    }
                }
            }
        }
        return true
    }

    override fun onTouch(v: View?, event: MotionEvent?): Boolean {
        TODO("Not yet implemented")
    }
}

private const val STATE_UP = 0
private const val STATE_VERTICAL_LEFT = 1
private const val STATE_VERTICAL_RIGHT = 2
