package eu.kanade.tachiyomi.ui.audioplayer.viewer

import android.annotation.SuppressLint
import android.content.Context
import android.content.ContextWrapper
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.AudioplayerControlsBinding
import eu.kanade.tachiyomi.ui.audioplayer.AudioplayerActivity
import eu.kanade.tachiyomi.ui.audioplayer.viewer.components.Seekbar
import `is`.xyz.mpv.MPVLib
import `is`.xyz.mpv.Utils
import kotlinx.coroutines.flow.update
import logcat.LogcatLogger
import tachiyomi.core.i18n.stringResource
import tachiyomi.core.util.lang.launchNonCancellable
import tachiyomi.core.util.lang.withUIContext
import tachiyomi.core.util.system.logcat
import kotlin.math.abs

class AudioplayerControlsView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    LinearLayout(context, attrs) {

    internal val binding: AudioplayerControlsBinding =
        AudioplayerControlsBinding.inflate(LayoutInflater.from(context), this, false)

    private tailrec fun Context.getActivity(): AudioplayerActivity? = this as? AudioplayerActivity
        ?: (this as? ContextWrapper)?.baseContext?.getActivity()

    private val activity: AudioplayerActivity = context.getActivity()!!

    private val playerPreferences = activity.playerPreferences

    private val player get() = activity.player

    private val originalTint = binding.prevBtn.imageTintList

    val seekbar: Seekbar = Seekbar(
        view = binding.playbackSeekbar,
        onValueChange = ::onValueChange,
        onValueChangeFinished = ::onValueChangeFinished,
    )

    private fun updateCoverImage() {
        activity.lifecycleScope.launchNonCancellable {
            activity.viewModel.mutableState.update {
                it.copy(isLoadingChapter = true)
            }
            withUIContext {
                try{
                    binding.cover.setImageURI(Uri.parse(activity.viewModel.currentAudiobook?.thumbnailUrl))
                }catch(e : Exception){
                    LogcatLogger.logcat { e.message!! }
                }
            }
        }
    }

    private fun onValueChange(value: Float, wasSeeking: Boolean) {
        if (!wasSeeking) {
            SeekState.mode = SeekState.SEEKBAR
            activity.initSeek()
        }

        MPVLib.command(arrayOf("seek", value.toInt().toString(), "absolute+keyframes"))

        val duration = player.duration ?: 0
        if (duration == 0 || activity.initialSeek < 0) {
            return
        }

        val difference = value.toInt() - activity.initialSeek

        showSeekText(value.toInt(), difference)
    }

    private fun onValueChangeFinished(value: Float) {
        if (SeekState.mode == SeekState.SEEKBAR) {
            if (playerPreferences.audioplayerSmoothSeek().get()) {
                player.timePos = value.toInt()
            } else {
                MPVLib.command(
                    arrayOf("seek", value.toInt().toString(), "absolute+keyframes"),
                )
            }
            SeekState.mode = SeekState.NONE
            animationHandler.removeCallbacks(hideUiForSeekRunnable)
            animationHandler.postDelayed(hideUiForSeekRunnable, 500L)
        } else {
            MPVLib.command(arrayOf("seek", value.toInt().toString(), "absolute+keyframes"))
        }
    }

    init {
        addView(binding.root)
    }

    @Suppress("DEPRECATION")
    override fun onViewAdded(child: View?) {
        binding.backArrowBtn.setOnClickListener { activity.onBackPressed() }

        // Long click controls
        binding.cycleSpeedBtn.setOnLongClickListener {
            activity.viewModel.showSpeedPicker()
            true
        }

        binding.prevBtn.setOnClickListener { switchEpisode(previous = true) }
        binding.playBtn.setOnClickListener { playPause() }
        binding.nextBtn.setOnClickListener { switchEpisode(previous = false) }

        binding.rewindBtn.setOnClickListener { activity.doubleTapSeek(-10, isDoubleTap = false) }
        binding.forwardBtn.setOnClickListener { activity.doubleTapSeek(10, isDoubleTap = false) }


//        binding.pipBtn.setOnClickListener { activity.pip.start() }

//        binding.pipBtn.isVisible = !playerPreferences.pipOnExit().get() && activity.pip.supportedAndEnabled

        binding.playbackPositionBtn.setOnClickListener {
            if (player.timePos != null && player.duration != null) {
                playerPreferences.invertedDurationTxt().set(false)
                playerPreferences.invertedPlaybackTxt().set(
                    !playerPreferences.invertedPlaybackTxt().get(),
                )
                updatePlaybackPos(player.timePos!!)
                updatePlaybackDuration(player.duration!!)
            }
        }

        binding.playbackDurationBtn.setOnClickListener {
            if (player.timePos != null && player.duration != null) {
                playerPreferences.invertedPlaybackTxt().set(false)
                playerPreferences.invertedDurationTxt().set(
                    !playerPreferences.invertedDurationTxt().get(),
                )
                updatePlaybackPos(player.timePos!!)
                updatePlaybackDuration(player.duration!!)
            }
        }

        binding.settingsBtn.setOnClickListener { activity.viewModel.showPlayerSettings() }

        binding.streamsBtn.setOnClickListener { activity.viewModel.showStreamsCatalog() }

        binding.titleMainTxt.setOnClickListener { activity.viewModel.showChapterList() }

        binding.titleSecondaryTxt.setOnClickListener { activity.viewModel.showChapterList() }

        updateCoverImage()
    }

    private fun switchEpisode(previous: Boolean) {
        return activity.changeChapter(activity.viewModel.getAdjacentChapterId(previous = previous))
    }

    internal suspend fun updateEpisodeText() {
        val viewModel = activity.viewModel
        withUIContext {
            binding.titleMainTxt.text = viewModel.currentAudiobook?.title
            binding.titleSecondaryTxt.text = viewModel.currentChapter?.name
        }
    }

    internal suspend fun updatePlaylistButtons() {
        val viewModel = activity.viewModel
        val plCount = viewModel.currentPlaylist.size
        val plPos = viewModel.getCurrentChapterIndex()

        val grey = ContextCompat.getColor(context, R.color.tint_disabled)
        val white = originalTint
        withUIContext {
            with(binding.prevBtn) {
                this.imageTintList = if (plPos == 0) ColorStateList.valueOf( grey ) else white
                this.isClickable = plPos != 0
            }
            with(binding.nextBtn) {
                this.imageTintList =
                    if (plPos == plCount - 1) ColorStateList.valueOf( grey ) else white
                this.isClickable = plPos != plCount - 1
            }
        }
    }

    internal suspend fun updateSpeedButton() {
        withUIContext {
            binding.cycleSpeedBtn.text = context.getString(R.string.ui_speed, player.playbackSpeed)
            player.playbackSpeed?.let { playerPreferences.audioplayerSpeed().set(it.toFloat()) }
        }
    }

    private var showControls = false
    private var wasPausedBeforeSeeking = false

    private val nonSeekViewRunnable = Runnable {
        binding.topControlsGroup.visibility = View.VISIBLE
        binding.chapterControlsGroup.visibility = View.VISIBLE
    }

    private val hideUiForSeekRunnable = Runnable {
        SeekState.mode = SeekState.NONE
        player.paused = wasPausedBeforeSeeking
        if (showControls) {
            AnimationUtils.loadAnimation(context, R.anim.player_fade_in).also { fadeAnimation ->
                binding.topControlsGroup.startAnimation(fadeAnimation)
                binding.topControlsGroup.visibility = View.VISIBLE

                binding.chapterControlsGroup.startAnimation(fadeAnimation)
                binding.chapterControlsGroup.visibility = View.VISIBLE
            }
            showControls = false
        } else {
            showControls = true

            animationHandler.removeCallbacks(nonSeekViewRunnable)
            animationHandler.postDelayed(
                nonSeekViewRunnable,
                600L + resources.getInteger(R.integer.player_animation_duration).toLong(),
            )
        }
    }

    internal fun hideUiForSeek() {
        animationHandler.removeCallbacks(hideUiForSeekRunnable)

        if (!(
                binding.topControlsGroup.visibility == View.INVISIBLE &&
                    binding.chapterControlsGroup.visibility == INVISIBLE
                )
        ) {
            wasPausedBeforeSeeking = player.paused!!
            showControls = binding.unlockedView.isVisible
            binding.topControlsGroup.visibility = View.INVISIBLE
            binding.chapterControlsGroup.visibility = View.INVISIBLE
            player.paused = true
            animationHandler.removeCallbacks(volumeViewRunnable)
            animationHandler.removeCallbacks(brightnessViewRunnable)
            animationHandler.removeCallbacks(seekTextRunnable)
            binding.volumeView.visibility = View.GONE
            binding.brightnessView.visibility = View.GONE
            activity.binding.seekView.visibility = View.GONE
            binding.seekBarGroup.visibility = View.VISIBLE
            binding.unlockedView.visibility = View.VISIBLE
            SeekState.mode = SeekState.SCROLL
        }

        val delay = if (SeekState.mode == SeekState.DOUBLE_TAP) 1000L else 500L

        animationHandler.postDelayed(hideUiForSeekRunnable, delay)
    }

    private val animationHandler = Handler(Looper.getMainLooper())

    @SuppressLint("SetTextI18n")
    internal fun updatePlaybackPos(position: Int) {
        val duration = player.duration
        val invertedPlayback = playerPreferences.invertedPlaybackTxt().get()
        val invertedDuration = playerPreferences.invertedDurationTxt().get()

        if (duration != null) {
            if (invertedPlayback) {
                binding.playbackPositionBtn.text = "-${Utils.prettyTime(duration - position)}"
            } else if (invertedDuration) {
                binding.playbackPositionBtn.text = Utils.prettyTime(position)
                binding.playbackDurationBtn.text = "-${Utils.prettyTime(duration - position)}"
            } else {
                binding.playbackPositionBtn.text = Utils.prettyTime(position)
            }
            activity.viewModel.onSecondReached(position, duration)
        }
        seekbar.updateSeekbar(value = position.toFloat())
    }

    @SuppressLint("SetTextI18n")
    internal fun updatePlaybackDuration(duration: Int) {
        if (!playerPreferences.invertedDurationTxt().get() && player.duration != null) {
            binding.playbackDurationBtn.text = Utils.prettyTime(duration)
        }

        seekbar.updateSeekbar(duration = duration.toFloat())
    }

    internal fun updateBufferPosition(bufferPosition: Int) {
        seekbar.updateSeekbar(readAheadValue = bufferPosition.toFloat())
    }

    internal fun showAndFadeControls() {
        val itemView = binding.unlockedView
        if (!itemView.isVisible) fadeInControls()
        itemView.visibility = View.VISIBLE
    }



    private fun fadeInControls() {
        AnimationUtils.loadAnimation(context, R.anim.player_fade_in).also { fadeAnimation ->
            val itemView = binding.unlockedView
            itemView.startAnimation(fadeAnimation)
            itemView.visibility = View.VISIBLE
        }

        binding.seekBarGroup.startAnimation(
            AnimationUtils.loadAnimation(context, R.anim.player_enter_bottom),
        )
        binding.topControlsGroup.startAnimation(
            AnimationUtils.loadAnimation(context, R.anim.player_enter_top),
        )
        binding.chapterControlsGroup.startAnimation(
            AnimationUtils.loadAnimation(context, R.anim.player_fade_in),
        )
    }

    internal fun playPause() {
        player.cyclePause()
        when {
            binding.unlockedView.isVisible -> showAndFadeControls()
        }
    }

    private fun cycleViewMode() {
        AspectState.mode = when (AspectState.mode) {
            AspectState.FIT -> AspectState.CROP
            AspectState.CROP -> AspectState.STRETCH
            else -> AspectState.FIT
        }
        setViewMode(showText = true)
    }

    internal fun setViewMode(showText: Boolean) {
        var aspect = "-1"
        var pan = "1.0"
        when (AspectState.mode) {
            AspectState.CROP -> {
                pan = "1.0"
            }
            AspectState.FIT -> {
                pan = "0.0"
            }
            AspectState.STRETCH -> {
                aspect = "${activity.deviceWidth}/${activity.deviceHeight}"
                pan = "0.0"
            }
            AspectState.CUSTOM -> {
                aspect = MPVLib.getPropertyString("video-aspect-override")
            }
        }

        mpvUpdateAspect(aspect = aspect, pan = pan)
        playerPreferences.audioplayerViewMode().set(AspectState.mode.index)
    }

    private fun mpvUpdateAspect(aspect: String, pan: String) {
        MPVLib.setPropertyString("video-aspect-override", aspect)
        MPVLib.setPropertyString("panscan", pan)
    }

    // Fade out seek text
    private val seekTextRunnable = Runnable {
        activity.binding.seekView.visibility = View.GONE
    }

    // Slide out Volume Bar
    private val volumeViewRunnable = Runnable {
        AnimationUtils.loadAnimation(context, R.anim.player_exit_left).also { slideAnimation ->
            if (SeekState.mode != SeekState.SCROLL) {
                binding.volumeView.startAnimation(
                    slideAnimation,
                )
            }
            binding.volumeView.visibility = View.GONE
        }
    }

    // Slide out Brightness Bar
    private val brightnessViewRunnable = Runnable {
        AnimationUtils.loadAnimation(context, R.anim.player_exit_right).also { slideAnimation ->
            if (SeekState.mode != SeekState.SCROLL) {
                binding.brightnessView.startAnimation(
                    slideAnimation,
                )
            }
            binding.brightnessView.visibility = View.GONE
        }
    }

    private fun showGestureView(type: String) {
        val callback: Runnable
        val itemView: LinearLayout
        val delay: Long
        when (type) {
            "seek" -> {
                callback = seekTextRunnable
                itemView = activity.binding.seekView
                delay = 0L
            }
            "volume" -> {
                callback = volumeViewRunnable
                itemView = binding.volumeView
                delay = 750L
                if (!itemView.isVisible) {
                    itemView.startAnimation(
                        AnimationUtils.loadAnimation(context, R.anim.player_enter_left),
                    )
                }
            }
            "brightness" -> {
                callback = brightnessViewRunnable
                itemView = binding.brightnessView
                delay = 750L
                if (!itemView.isVisible) {
                    itemView.startAnimation(
                        AnimationUtils.loadAnimation(context, R.anim.player_enter_right),
                    )
                }
            }
            else -> return
        }

        animationHandler.removeCallbacks(callback)
        itemView.visibility = View.VISIBLE
        animationHandler.postDelayed(callback, delay)
    }

    internal fun showSeekText(position: Int, difference: Int) {
        hideUiForSeek()
        updatePlaybackPos(position)

        val diffText = Utils.prettyTime(difference, true)
        activity.binding.seekText.text = activity.getString(
            R.string.ui_seek_distance,
            Utils.prettyTime(position),
            diffText,
        )
        showGestureView("seek")
    }

    internal fun showVolumeBar(showBar: Boolean, volume: Int) {
        binding.volumeText.text = volume.toString()
        binding.volumeBar.progress = volume
        if (volume == 0) {
            binding.volumeImg.setImageResource(R.drawable.ic_volume_off_24dp)
        } else {
            binding.volumeImg.setImageResource(R.drawable.ic_volume_on_20dp)
        }
        if (showBar) showGestureView("volume")
    }

    internal fun showBrightnessBar(showBar: Boolean, brightness: Int) {
        binding.brightnessText.text = brightness.toString()
        binding.brightnessBar.progress = abs(brightness)
        if (brightness >= 0) {
            binding.brightnessImg.setImageResource(R.drawable.ic_brightness_positive_20dp)
            binding.brightnessBar.max = 100
            binding.brightnessBar.secondaryProgress = 100
        } else {
            binding.brightnessImg.setImageResource(R.drawable.ic_brightness_negative_20dp)
            binding.brightnessBar.max = 75
            binding.brightnessBar.secondaryProgress = 75
        }
        if (showBar) showGestureView("brightness")
    }
}
