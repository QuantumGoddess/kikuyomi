package eu.kanade.tachiyomi.ui.audioplayer

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.ImageDecoder
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewAnimationUtils
import android.view.WindowManager
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.GestureDetectorCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.audiobooksource.model.Audio
import eu.kanade.tachiyomi.audiobooksource.model.Track
import eu.kanade.tachiyomi.audiobooksource.online.AudiobookHttpSource
import eu.kanade.tachiyomi.data.notification.NotificationReceiver
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.databinding.AudioplayerActivityBinding
import eu.kanade.tachiyomi.ui.audioplayer.settings.AudioplayerPreferences
import eu.kanade.tachiyomi.ui.audioplayer.settings.AudioplayerSettingsScreenModel
import eu.kanade.tachiyomi.ui.audioplayer.settings.dialogs.ChapterListDialog
import eu.kanade.tachiyomi.ui.audioplayer.settings.dialogs.SpeedPickerDialog
import eu.kanade.tachiyomi.ui.audioplayer.settings.sheets.AudioChaptersSheet
import eu.kanade.tachiyomi.ui.audioplayer.settings.sheets.AudioplayerSettingsSheet
import eu.kanade.tachiyomi.ui.audioplayer.settings.sheets.StreamsCatalogSheet
import eu.kanade.tachiyomi.ui.audioplayer.viewer.ACTION_MEDIA_CONTROL
import eu.kanade.tachiyomi.ui.audioplayer.viewer.AspectState
import eu.kanade.tachiyomi.ui.audioplayer.viewer.EXTRA_CONTROL_TYPE
import eu.kanade.tachiyomi.ui.audioplayer.viewer.GestureHandler
import eu.kanade.tachiyomi.ui.audioplayer.viewer.PIP_NEXT
import eu.kanade.tachiyomi.ui.audioplayer.viewer.PIP_PAUSE
import eu.kanade.tachiyomi.ui.audioplayer.viewer.PIP_PLAY
import eu.kanade.tachiyomi.ui.audioplayer.viewer.PIP_PREVIOUS
import eu.kanade.tachiyomi.ui.audioplayer.viewer.PIP_SKIP
import eu.kanade.tachiyomi.ui.audioplayer.viewer.PictureInPictureHandler
import eu.kanade.tachiyomi.ui.audioplayer.viewer.PipState
import eu.kanade.tachiyomi.ui.audioplayer.viewer.SeekState
import eu.kanade.tachiyomi.ui.audioplayer.viewer.SetAsCover
import eu.kanade.tachiyomi.ui.base.activity.BaseActivity
import eu.kanade.tachiyomi.util.SkipType
import eu.kanade.tachiyomi.util.Stamp
import eu.kanade.tachiyomi.util.system.LocaleHelper
import eu.kanade.tachiyomi.util.system.powerManager
import eu.kanade.tachiyomi.util.system.toShareIntent
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.view.setComposeContent
import `is`.xyz.mpv.MPVActivity
import `is`.xyz.mpv.MPVLib
import `is`.xyz.mpv.Utils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import logcat.LogPriority
import logcat.LogcatLogger
import tachiyomi.core.i18n.stringResource
import tachiyomi.core.util.lang.launchIO
import tachiyomi.core.util.lang.launchNonCancellable
import tachiyomi.core.util.lang.launchUI
import tachiyomi.core.util.lang.withIOContext
import tachiyomi.core.util.lang.withUIContext
import tachiyomi.core.util.system.logcat
import tachiyomi.domain.storage.service.StorageManager
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt
import `is`.xyz.mpv.MPVView.Chapter as VideoChapter

class AudioplayerActivity : BaseActivity() {

    internal val viewModel by viewModels<PlayerViewModel>()

    internal val playerPreferences: AudioplayerPreferences = Injekt.get()

    private val stopServiceHandler = Handler(Looper.getMainLooper())
    private val eventUiHandler = Handler(Looper.getMainLooper())

    private var audiobookId: Long? = -1
    private var episodeId: Long? = -1

    companion object {
        fun newIntent(context: Context, audiobookId: Long?, episodeId: Long?): Intent {
            return Intent(context, AudioplayerActivity::class.java).apply {
                putExtra("audiobookId", audiobookId)
                putExtra("episodeId", episodeId)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        logcat { intent.action.toString() }
        if (isFinishing) {
            Log.w("mpv", "Activity is finishing...");
            return;
        }
        audiobookId = intent.extras!!.getLong("audiobookId", -1)
        episodeId = intent.extras!!.getLong("episodeId", -1)
        if (audiobookId == -1L || episodeId == -1L) {
            logcat {"whoops, something went wrong"}
            finish()
            return
        }
        viewModel.saveCurrentChapterWatchingProgress()
        lifecycleScope.launchNonCancellable {
            viewModel.mutableState.update {
                it.copy(isLoadingChapter = true)
            }

            val initResult = viewModel.init(audiobookId!!, episodeId!!)
            if (!initResult.second.getOrDefault(false)) {
                val exception = initResult.second.exceptionOrNull() ?: IllegalStateException(
                    "Unknown error",
                )
                withUIContext {
                    setInitialEpisodeError(exception)
                }
            }
            lifecycleScope.launch {
                setVideoList(
                    qualityIndex = initResult.first.audioIndex,
                    videos = initResult.first.audioList,
                    position = initResult.first.position,
                )
            }
        }
        super.onNewIntent(intent)

        val filepath = intent?.let { parsePathFromIntent(it) }
        if (filepath == null) {
            return
        }
        if (!activityIsForeground && didResumeBackgroundPlayback) {
            MPVLib.command(arrayOf("loadfile", filepath, "append"))
            showToast(getString(R.string.notice_file_appended))
            moveTaskToBack(true)
        } else {
            MPVLib.command(arrayOf("loadfile", filepath))
        }
    }



    private fun resolveUri(data: Uri): String? {
        val filepath = when (data.scheme) {
            "file" -> data.path
            "content" -> openContentFd(data)
            "http", "https", "rtmp", "rtmps", "rtp", "rtsp", "mms", "mmst", "mmsh", "tcp", "udp", "lavf"
            -> data.toString()
            else -> null
        }

        if (filepath == null)
            Log.e("mpv", "unknown scheme: ${data.scheme}")
        return filepath
    }

    private fun parsePathFromIntent(intent: Intent): String? {
        val filepath: String?
        filepath = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data?.let { resolveUri(it) }
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)?.let {
                val uri = Uri.parse(it.trim())
                if (uri.isHierarchical && !uri.isRelative) resolveUri(uri) else null
            }
            else -> intent.getStringExtra("filepath")
        }
        return filepath
    }

    internal val pip = PictureInPictureHandler(this, playerPreferences.enablePip().get())

    private val playerObserver = PlayerObserver(this)

    private var mReceiver: BroadcastReceiver? = null

    lateinit var binding: AudioplayerActivityBinding
    private lateinit var toast: Toast

    private val psc = Utils.PlaybackStateCache()
    private lateinit var mediaSession: MediaSessionCompat

    private val playbackStateBuilder = PlaybackStateCompat.Builder()

    private lateinit var headsetReceiver: BroadcastReceiver

    private var activityIsForeground = true
    private var didResumeBackgroundPlayback = false

    internal val player get() = binding.player

    internal val playerControls get() = binding.playerControls

    private var audioManager: AudioManager? = null
    private var fineVolume = 0F
    private var maxVolume = 0

    private var brightness = 0F

    internal var deviceWidth = 0
    internal var deviceHeight = 0

    private var audioFocusRestore: () -> Unit = {}

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { type ->
        when (type) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            -> {
                // loss can occur in addition to ducking, so remember the old callback
                val oldRestore = audioFocusRestore
                val wasPlayerPaused = player.paused ?: false
                player.paused = true
                audioFocusRestore = {
                    oldRestore()
                    if (!wasPlayerPaused) player.paused = false
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                MPVLib.command(arrayOf("multiply", "volume", 0.5F.toString()))
                audioFocusRestore = {
                    MPVLib.command(arrayOf("multiply", "volume", 2F.toString()))
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                audioFocusRestore()
                audioFocusRestore = {}
            }
        }
    }

    private var hasAudioFocus = false

    private val audioFocusRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setOnAudioFocusChangeListener(audioFocusChangeListener)
            .build()
    } else {
        null
    }

    @Suppress("DEPRECATION")
    private fun requestAudioFocus() {
        if (hasAudioFocus) return
        hasAudioFocus = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioManager!!.requestAudioFocus(audioFocusRequest!!)
        } else {
            audioManager!!.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN,
            )
        }
    }

    private fun updateMediaSession() {
        synchronized (psc) {
            mediaSession?.let { psc.write(it) }
        }
    }

    @Suppress("DEPRECATION")
    private fun abandonAudioFocus() {
        if (!hasAudioFocus) return
        hasAudioFocus = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioManager!!.abandonAudioFocusRequest(audioFocusRequest!!)
        } else {
            audioManager!!.abandonAudioFocus(audioFocusChangeListener)
        }
    }

    private fun setAudioFocus(paused: Boolean) {
        if (paused) {
            abandonAudioFocus()
        } else {
            requestAudioFocus()
        }
    }

    internal var initialSeek = -1

    private val animationHandler = Handler(Looper.getMainLooper())

    private val streams: PlayerViewModel.AudioStreams
        get() = viewModel.state.value.audioStreams

    private var currentVideoList: List<Audio>? = null
        set(list) {
            field = list
            streams.quality.tracks = field?.map { Track("", it.quality) }?.toTypedArray() ?: emptyArray()
        }

    private var playerIsDestroyed = true

    private var hadPreviousSubs = false

    private var hadPreviousAudio = false

    private var videoChapters: List<VideoChapter> = emptyList()
        set(value) {
            field = value
            runOnUiThread {
                playerControls.seekbar.updateSeekbar(chapters = value)
            }
        }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        return super.dispatchTouchEvent(ev)
    }

    // Create Player -- Start --

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        registerSecureActivity(this)
        overridePendingTransition(R.anim.shared_axis_x_push_enter, R.anim.shared_axis_x_push_exit)
        Utils.copyAssets(this)
        super.onCreate(savedInstanceState)

        AudioplayerBackgroundService.createNotificationChannel(this)

        initMessageToast()
        setupPlayerControls()
        setupMediaSession()
        setupPlayerMPV()
        setupPlayerAudio()
        setupPlayerSubtitles()
        setupPlayerBrightness()
        loadDeviceDimensions()

        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            toast(throwable.message)
            logcat(LogPriority.ERROR, throwable)
            finish()
        }

        viewModel.eventFlow
            .onEach { event ->
                when (event) {
                    is PlayerViewModel.Event.SavedImage -> {
                        onSaveImageResult(event.result)
                    }
                    is PlayerViewModel.Event.ShareImage -> {
                        onShareImageResult(event.uri, event.seconds)
                    }
                    is PlayerViewModel.Event.SetCoverResult -> {
                        onSetAsCoverResult(event.result)
                    }
                    is PlayerViewModel.Event.SetAudiobookSkipIntro -> {
                        refreshUi()
                    }
                }
            }
            .launchIn(lifecycleScope)

        onNewIntent(this.intent)

        binding.dialogRoot.setComposeContent {
            val state by viewModel.state.collectAsState()

            when (state.dialog) {
                is PlayerViewModel.Dialog.ChapterList -> {
                    if (state.audiobook != null) {
                        ChapterListDialog(
                            displayMode = state.audiobook!!.displayMode,
                            chapterList = viewModel.currentPlaylist,
                            currentChapterIndex = viewModel.getCurrentChapterIndex(),
                            dateRelativeTime = viewModel.relativeTime,
                            dateFormat = viewModel.dateFormat,
                            onBookmarkClicked = viewModel::bookmarkChapter,
                            onChapterClicked = this::changeChapter,
                            onDismissRequest = pauseForDialogSheet(),
                        )
                    }
                }

                is PlayerViewModel.Dialog.SpeedPicker -> {
                    fun updateSpeed(speed: Float) {
                        playerPreferences.audioplayerSpeed().set(speed)
                        MPVLib.setPropertyDouble("speed", speed.toDouble())
                    }
                    SpeedPickerDialog(
                        currentSpeed = MPVLib.getPropertyDouble("speed"),
                        onSpeedChanged = ::updateSpeed,
                        onDismissRequest = pauseForDialogSheet(),
                    )
                }



                null -> {}
            }
        }

        binding.sheetRoot.setComposeContent {
            val state by viewModel.state.collectAsState()

            when (state.sheet) {


                is PlayerViewModel.Sheet.PlayerSettings -> {
                    AudioplayerSettingsSheet(
                        screenModel = AudioplayerSettingsScreenModel(viewModel.playerPreferences),
                        onDismissRequest = pauseForDialogSheet(),
                    )
                }

                is PlayerViewModel.Sheet.AudioChapters -> {
                    fun setChapter(videoChapter: VideoChapter, text: String) {
                        val seekDifference = videoChapter.time.roundToInt() - (player.timePos ?: 0)
                        doubleTapSeek(
                            time = seekDifference,
                            isDoubleTap = false,
                            videoChapterText = text,
                        )
                    }
                    AudioChaptersSheet(
                        timePosition = player.timePos ?: 0,
                        audioChapters = videoChapters,
                        onAudioChapterSelected = ::setChapter,
                        onDismissRequest = pauseForDialogSheet(),
                    )
                }

                is PlayerViewModel.Sheet.StreamsCatalog -> {
                    val qualityTracks = streams.quality.tracks.takeUnless { it.isEmpty() }
                    val subtitleTracks = streams.subtitle.tracks.takeUnless { it.isEmpty() }
                    val audioTracks = streams.audio.tracks.takeUnless { it.isEmpty() }

                    if (qualityTracks != null && subtitleTracks != null && audioTracks != null) {
                        fun onQualitySelected(qualityIndex: Int) {
                            if (playerIsDestroyed) return
                            if (streams.quality.index == qualityIndex) return
                            showLoadingIndicator(true)
                            viewModel.qualityIndex = qualityIndex
                            logcat(LogPriority.INFO) { "Changing quality" }
                            setVideoList(qualityIndex, currentVideoList)
                        }

                        fun onSubtitleSelected(index: Int) {
                            if (streams.subtitle.index == index ||
                                streams.subtitle.index > subtitleTracks.lastIndex
                            ) {
                                return
                            }
                            streams.subtitle.index = index
                            if (index == 0) {
                                player.sid = -1
                                return
                            }
                            val tracks = player.tracks.getValue("sub")
                            val selectedLoadedTrack = tracks.firstOrNull {
                                it.name == subtitleTracks[index].url ||
                                    it.mpvId.toString() == subtitleTracks[index].url
                            }
                            selectedLoadedTrack?.let { player.sid = it.mpvId }
                                ?: MPVLib.command(
                                    arrayOf(
                                        "sub-add",
                                        subtitleTracks[index].url,
                                        "select",
                                        subtitleTracks[index].url,
                                    ),
                                )
                        }

                        fun onAudioSelected(index: Int) {
                            if (streams.audio.index == index || streams.audio.index > audioTracks.lastIndex) return
                            streams.audio.index = index
                            if (index == 0) {
                                player.aid = -1
                                return
                            }
                            val tracks = player.tracks.getValue("audio")
                            val selectedLoadedTrack = tracks.firstOrNull {
                                it.name == audioTracks[index].url ||
                                    it.mpvId.toString() == audioTracks[index].url
                            }
                            selectedLoadedTrack?.let { player.aid = it.mpvId }
                                ?: MPVLib.command(
                                    arrayOf(
                                        "audio-add",
                                        audioTracks[index].url,
                                        "select",
                                        audioTracks[index].url,
                                    ),
                                )
                        }

                        StreamsCatalogSheet(
                            isEpisodeOnline = viewModel.isChapterOnline(),
                            videoStreams = viewModel.state.collectAsState().value.audioStreams,
                            openContentFd = ::openContentFd,
                            onQualitySelected = ::onQualitySelected,
                            onSubtitleSelected = ::onSubtitleSelected,
                            onAudioSelected = ::onAudioSelected,
                            onDismissRequest = pauseForDialogSheet(),
                        )
                    }
                }

                null -> {}
            }
        }

        playerIsDestroyed = false

        registerReceiver(
            headsetReceiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
        )
    }

    private fun setupPlayerControls() {
        binding = AudioplayerActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.statusBarColor = 70000000
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            window.navigationBarColor = 70000000
        }

        refreshUi()
        playerControls.showAndFadeControls()

    }

    private fun setupPlayerMPV() {
        val mpvConfFile = File("${applicationContext.filesDir.path}/mpv.conf")
        playerPreferences.mpvConf().get().let { mpvConfFile.writeText(it) }
        val mpvInputFile = File("${applicationContext.filesDir.path}/input.conf")
        playerPreferences.mpvInput().get().let { mpvInputFile.writeText(it) }

        val logLevel = if (viewModel.networkPreferences.verboseLogging().get()) "info" else "warn"
        player.initialize(applicationContext.filesDir.path, logLevel)

        val speedProperty = MPVLib.getPropertyDouble("speed")
        val currentSpeed = if (speedProperty == 1.0) playerPreferences.audioplayerSpeed().get().toDouble() else speedProperty
        MPVLib.setPropertyDouble("speed", currentSpeed)

        MPVLib.observeProperty("chapter-list", MPVLib.mpvFormat.MPV_FORMAT_NONE)
        MPVLib.setOptionString("keep-open", "always")
        MPVLib.setOptionString("ytdl", "no")

        MPVLib.setOptionString("hwdec", playerPreferences.hwDec().get())
        when (playerPreferences.deband().get()) {
            1 -> MPVLib.setOptionString("vf", "gradfun=radius=12")
            2 -> MPVLib.setOptionString("deband", "yes")
            3 -> MPVLib.setOptionString("vf", "format=yuv420p")
        }

        val currentPlayerStatisticsPage = playerPreferences.audioplayerStatisticsPage().get()
        if (currentPlayerStatisticsPage != 0) {
            MPVLib.command(arrayOf("script-binding", "stats/display-stats-toggle"))
            MPVLib.command(
                arrayOf("script-binding", "stats/display-page-$currentPlayerStatisticsPage"),
            )
        }

        MPVLib.setOptionString("input-default-bindings", "yes")

        MPVLib.addLogObserver(playerObserver)
        player.addObserver(playerObserver)
    }

    private fun setupPlayerAudio() {
        with(playerPreferences) {
            audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

            val useDeviceVolume = audioplayerVolumeValue().get() == -1.0F || !rememberAudioplayerVolume().get()
            fineVolume = if (useDeviceVolume) {
                audioManager!!.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
            } else {
                audioplayerVolumeValue().get()
            }

            if (rememberAudioDelay().get()) {
                MPVLib.setPropertyDouble("audio-delay", (audioDelay().get() / 1000.0))
            }
        }

        verticalScrollRight(0F)

        maxVolume = audioManager!!.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        playerControls.binding.volumeBar.max = maxVolume
        playerControls.binding.volumeBar.secondaryProgress = maxVolume

        volumeControlStream = AudioManager.STREAM_MUSIC
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun setupPlayerSubtitles() {
        with(playerPreferences) {
            val overrideType = if (overrideSubsASS().get()) "force" else "no"
            MPVLib.setPropertyString("sub-ass-override", overrideType)

            if (rememberSubtitlesDelay().get()) {
                MPVLib.setPropertyDouble("sub-delay", subtitlesDelay().get() / 1000.0)
            }

            copyFontsDirectory()

            if (playerPreferences.subtitleFont().get().trim() != "") {
                MPVLib.setPropertyString("sub-font", playerPreferences.subtitleFont().get())
            } else {
                MPVLib.setPropertyString("sub-font", "Sans Serif")
            }

            MPVLib.setPropertyString("sub-bold", if (boldSubtitles().get()) "yes" else "no")
            MPVLib.setPropertyString("sub-italic", if (italicSubtitles().get()) "yes" else "no")
            MPVLib.setPropertyInt("sub-font-size", subtitleFontSize().get())
            MPVLib.setPropertyString("sub-color", textColorSubtitles().get().toHexString())
            MPVLib.setPropertyString("sub-border-color", borderColorSubtitles().get().toHexString())
            MPVLib.setPropertyString(
                "sub-back-color",
                backgroundColorSubtitles().get().toHexString(),
            )
        }
    }

    private fun copyFontsDirectory() {
        // TODO: I think this is a bad hack.
        //  We need to find a way to let MPV directly access our fonts directory.
        CoroutineScope(Dispatchers.IO).launchIO {
            val storageManager: StorageManager = Injekt.get()
            storageManager.getFontsDirectory()?.listFiles()?.forEach { font ->
                val outFile = UniFile.fromFile(applicationContext.filesDir)?.createFile(font.name)
                outFile?.let {
                    font.openInputStream().copyTo(it.openOutputStream())
                }
            }
            MPVLib.setPropertyString(
                "sub-fonts-dir",
                applicationContext.filesDir.path,
            )
            logcat { "FINISHED FONTS" }
        }
    }

    private fun setupPlayerBrightness() {
        val useDeviceBrightness =
            playerPreferences.audioplayerBrightnessValue().get() == -1.0F ||
                !playerPreferences.rememberAudioplayerBrightness().get()

        brightness = if (useDeviceBrightness) {
            Utils.getScreenBrightness(this) ?: 0.5F
        } else {
            playerPreferences.audioplayerBrightnessValue().get()
        }
        verticalScrollLeft(0F)
    }

    @Suppress("DEPRECATION")
    private fun setupMediaSession() {
        mediaSession = MediaSessionCompat(this, "Kikuyomi_Player_Session").apply {
            // Enable callbacks from MediaButtons and TransportControls
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS,
            )

            // Do not let MediaButtons restart the player when the app is not visible
            setMediaButtonReceiver(null)

            setPlaybackState(
                with(playbackStateBuilder) {
                    setState(
                        PlaybackStateCompat.STATE_NONE,
                        0L,
                        0.0F,
                    )
                    build()
                },
            )

            // Implement methods that handle callbacks from a media controller
            setCallback(
                object : MediaSessionCompat.Callback() {
                    override fun onPlay() {
                        pauseByIntents(false)
                    }

                    override fun onPause() {
                        pauseByIntents(true)
                    }

                    override fun onSkipToPrevious() {
                        if (playerPreferences.mediaChapterSeek().get()) {
                            if (player.loadChapters().isNotEmpty()) {
                                doubleTapSeek(
                                    -1,
                                    videoChapterText = stringResource(MR.strings.go_to_previous_chapter),
                                    chapterSeek = "-1",
                                )
                            }
                        } else {
                            changeChapter(viewModel.getAdjacentChapterId(previous = true))
                        }
                    }

                    override fun onSkipToNext() {
                        if (playerPreferences.mediaChapterSeek().get()) {
                            if (player.loadChapters().isNotEmpty()) {
                                doubleTapSeek(
                                    1,
                                    videoChapterText = stringResource(MR.strings.go_to_next_chapter),
                                    chapterSeek = "1",
                                )
                            } else {
                                doubleTapSeek(viewModel.getAudiobookSkipIntroLength())
                            }
                        } else {
                            changeChapter(viewModel.getAdjacentChapterId(previous = false))
                        }
                    }
                },
            )
        }

        MediaControllerCompat(this, mediaSession).also { mediaController ->
            MediaControllerCompat.setMediaController(this, mediaController)
        }

        headsetReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                    pauseByIntents(true)
                }
            }
        }
    }

    private fun pauseByIntents(pause: Boolean) {
        player.paused = pause
        updatePlaybackState(pause = pause)
    }

    private fun updatePlaybackState(cachePause: Boolean = false, pause: Boolean = false) {
        val state = when {
            player.timePos?.let { it < 0 } ?: true ||
                player.duration?.let { it <= 0 } ?: true -> PlaybackStateCompat.STATE_CONNECTING
            cachePause -> PlaybackStateCompat.STATE_BUFFERING
            pause or (player.paused == true) -> PlaybackStateCompat.STATE_PAUSED
            else -> PlaybackStateCompat.STATE_PLAYING
        }
        var actions = PlaybackStateCompat.ACTION_PLAY or
            PlaybackStateCompat.ACTION_PLAY_PAUSE or
            PlaybackStateCompat.ACTION_PAUSE
        if (viewModel.currentPlaylist.size > 1) {
            actions = actions or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT
        }

        mediaSession.setPlaybackState(
            with(playbackStateBuilder) {
                setState(
                    state,
                    player.timePos?.toLong() ?: 0L,
                    player.playbackSpeed?.toFloat() ?: 1.0f,
                )
                setActions(actions)
                build()
            },
        )
    }

    @Suppress("DEPRECATION")
    private fun loadDeviceDimensions() {
        this@AudioplayerActivity.requestedOrientation = playerPreferences.defaultAudioplayerOrientationType().get()
        val dm = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(dm)
        deviceWidth = dm.widthPixels
        deviceHeight = dm.heightPixels

        val aspectProperty = MPVLib.getPropertyString("video-aspect-override").toDouble()
        AspectState.mode = if (aspectProperty != -1.0 && aspectProperty != (deviceWidth / deviceHeight).toDouble()) {
            AspectState.CUSTOM
        } else {
            AspectState.get(playerPreferences.audioplayerViewMode().get())
        }

        playerControls.setViewMode(showText = false)
    }

    private fun pauseForDialogSheet(fadeControls: Boolean = false): () -> Unit {
        val wasPlayerPaused = player.paused ?: true // default to not changing state
        player.paused = true
        return {
            if (!wasPlayerPaused) {
                player.paused = false
            } else {
                playerControls.showAndFadeControls()
            }
            viewModel.closeDialogSheet()
            refreshUi()
        }
    }

    // Create Player -- End --

    // Override PlayerActivity lifecycle -- Start --

    override fun onSaveInstanceState(outState: Bundle) {
        if (!isChangingConfigurations) {
            viewModel.onSaveInstanceStateNonConfigurationChange()
        }
        super.onSaveInstanceState(outState)
    }

    private fun shouldBackground(): Boolean {
        if (isFinishing) // about to exit?
            return false
        return true
    }

    override fun onResume() {
        if (activityIsForeground) {
            super.onResume()
            return
        }

        activityIsForeground = true
        // stop background service with a delay
        stopServiceHandler.removeCallbacks(stopServiceRunnable)
        stopServiceHandler.postDelayed(stopServiceRunnable, 1000L)

        refreshUi()

        super.onResume()
        if (pip.supportedAndEnabled && PipState.mode == PipState.ON) {
            player.paused?.let { pip.update(!it) }
        }
    }

    private fun savePosition() {
        if (MPVLib.getPropertyBoolean("eof-reached") ?: true) {
            Log.d("mpv", "player indicates EOF, not saving watch-later config")
            return
        }
        MPVLib.command(arrayOf("write-watch-later-config"))
    }

    override fun onPause() {
        viewModel.saveCurrentChapterWatchingProgress()
        super.onPause()
        val fmt = MPVLib.getPropertyString("video-format")
        val shouldBackground = shouldBackground()
        if (shouldBackground && !fmt.isNullOrEmpty())
            AudioplayerBackgroundService.thumbnail = MPVLib.grabThumbnail(192)
        else
            AudioplayerBackgroundService.thumbnail = null

        // media session uses the same thumbnail
        updateMediaSession()

        eventUiHandler.removeCallbacksAndMessages(null)
        activityIsForeground = false
        if (isFinishing) {
            savePosition()
            // tell mpv to shut down so that any other property changes or such are ignored,
            // preventing useless busywork
            MPVLib.command(arrayOf("stop"))
        } else if (!shouldBackground) {
            player.paused = true
        }

        didResumeBackgroundPlayback = shouldBackground
        if (shouldBackground) {
            Log.v("mpv", "Resuming playback in background")
            stopServiceHandler.removeCallbacks(stopServiceRunnable)
            val serviceIntent = Intent(this, AudioplayerBackgroundService::class.java).apply {
                putExtra("audiobookId", audiobookId)
                putExtra("episodeId", episodeId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                startForegroundService(serviceIntent)
            else
                startService(serviceIntent)
        }
    }

    override fun onStop() {
        viewModel.saveCurrentChapterWatchingProgress()
        if (pip.supportedAndEnabled && PipState.mode == PipState.ON && powerManager.isInteractive) {
            finishAndRemoveTask()
        }

        super.onStop()
    }

    override fun finishAndRemoveTask() {
        viewModel.deletePendingChapters()
        super.finishAndRemoveTask()
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.shared_axis_x_pop_enter, R.anim.shared_axis_x_pop_exit)
    }

    override fun onDestroy() {
        activityIsForeground = false
        mediaSession.isActive = false
        mediaSession.release()
        unregisterReceiver(headsetReceiver)

        playerPreferences.audioplayerVolumeValue().set(fineVolume)
        playerPreferences.audioplayerBrightnessValue().set(brightness)
        abandonAudioFocus()
        stopServiceRunnable.run()
        MPVLib.removeLogObserver(playerObserver)
        if (!playerIsDestroyed) {
            player.removeObserver(playerObserver)
            player.destroy()
        }
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (pip.supportedAndEnabled) {
            if (player.paused == false && playerPreferences.pipOnExit().get()) {
                pip.start()
            } else {
                finishAndRemoveTask()
                super.onBackPressed()
            }
        } else {
            finishAndRemoveTask()
            super.onBackPressed()
        }
    }

    override fun onUserLeaveHint() {
        if (player.paused == false && playerPreferences.pipOnExit().get()) pip.start()
        super.onUserLeaveHint()
    }

    // Override PlayerActivity lifecycle -- End --

    /**
     * Sets up the gesture detector and connects it to the player
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun setupGestures() {
        val gestures = GestureHandler(this, deviceWidth.toFloat(), deviceHeight.toFloat())
        val mDetector = GestureDetectorCompat(this, gestures)
        player.setOnTouchListener { v, event ->
            gestures.onTouch(v, event)
            mDetector.onTouchEvent(event)
        }
    }

    /**
     * Switches to the episode based on [episodeId],
     * @param episodeId id of the episode to switch the player to
     * @param autoPlay whether the episode is switching due to auto play
     */
    internal fun changeChapter(episodeId: Long?, autoPlay: Boolean = false) {
        animationHandler.removeCallbacks(nextEpisodeRunnable)
        viewModel.closeDialogSheet()

        player.paused = true
        showLoadingIndicator(true)
        videoChapters = emptyList()
        aniskipStamps = emptyList()

        lifecycleScope.launch {
            viewModel.mutableState.update { it.copy(isLoadingChapter = true) }

            val pipEpisodeToasts = playerPreferences.pipEpisodeToasts().get()

            when (val switchMethod = viewModel.loadChapter(episodeId)) {
                null -> {
                    if (viewModel.currentAudiobook != null && !autoPlay) {
                        launchUI { toast(MR.strings.no_next_episode) }
                    }
                    showLoadingIndicator(false)
                }

                else -> {
                    if (switchMethod.first != null) {
                        when {
                            switchMethod.first!!.isEmpty() -> setInitialEpisodeError(
                                Exception("Video list is empty."),
                            )
                            else -> setVideoList(qualityIndex = 0, switchMethod.first!!)
                        }
                    } else {
                        logcat(LogPriority.ERROR) { "Error getting links" }
                    }

                    if (PipState.mode == PipState.ON && pipEpisodeToasts) {
                        launchUI { toast(switchMethod.second) }
                    }
                }
            }
        }
    }

    internal fun showLoadingIndicator(visible: Boolean) {
        viewModel.viewModelScope.launchUI {
            binding.loadingIndicator.isVisible = visible
            playerControls.binding.playBtn.isVisible = !visible
        }
    }

    private fun isSeeking(seeking: Boolean) {
        val position = player.timePos ?: return
        val cachePosition = MPVLib.getPropertyInt("demuxer-cache-time") ?: -1
        showLoadingIndicator(position >= cachePosition && seeking)
    }

    @Suppress("UNUSED_PARAMETER")
    @SuppressLint("SourceLockedOrientationActivity")
    fun rotatePlayer(view: View) {
        if (this.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
            this.requestedOrientation = playerPreferences.defaultAudioplayerOrientationLandscape().get()
        } else {
            this.requestedOrientation = playerPreferences.defaultAudioplayerOrientationPortrait().get()
        }
    }

    private val doubleTapPlayPauseRunnable = Runnable {
        AnimationUtils.loadAnimation(this, R.anim.player_fade_out).also { fadeAnimation ->
            binding.playPauseView.startAnimation(fadeAnimation)
            binding.playPauseView.visibility = View.GONE
        }
    }

    private val stopServiceRunnable = Runnable {
        val intent = Intent(this, AudioplayerBackgroundService::class.java)
        applicationContext.stopService(intent)
    }

    internal fun doubleTapPlayPause() {
        animationHandler.removeCallbacks(doubleTapPlayPauseRunnable)
        playerControls.playPause()

        if (!playerControls.binding.unlockedView.isVisible) {
            when {
                player.paused!! -> {
                    binding.playPauseView.setImageResource(R.drawable.ic_pause_64dp)
                }

                !player.paused!! -> {
                    binding.playPauseView.setImageResource(R.drawable.ic_play_arrow_64dp)
                }
            }

            AnimationUtils.loadAnimation(this, R.anim.player_fade_in).also { fadeAnimation ->
                binding.playPauseView.startAnimation(fadeAnimation)
                binding.playPauseView.visibility = View.VISIBLE
            }

            animationHandler.postDelayed(doubleTapPlayPauseRunnable, 500L)
        } else {
            binding.playPauseView.visibility = View.GONE
        }
    }

    private lateinit var doubleTapBg: ImageView

    private val doubleTapSeekRunnable = Runnable {
        SeekState.mode = SeekState.NONE
        binding.secondsView.visibility = View.GONE
        doubleTapBg.visibility = View.GONE
        binding.secondsView.seconds = 0
        binding.secondsView.stop()
    }

    fun doubleTapSeek(
        time: Int,
        event: MotionEvent? = null,
        isDoubleTap: Boolean = true,
        videoChapterText: String? = null,
        chapterSeek: String? = null,
    ) {
        if (SeekState.mode != SeekState.DOUBLE_TAP) {
            doubleTapBg = if (time < 0) binding.rewBg else binding.ffwdBg
        }

        val view = if (time < 0) binding.rewTap else binding.ffwdTap
        val width = if (time < 0) deviceWidth * 0.2F else deviceWidth * 0.8F
        val x = (event?.x?.toInt() ?: width.toInt()) - view.x.toInt()
        val y = (event?.y?.toInt() ?: (deviceHeight / 2)) - view.y.toInt()

        SeekState.mode = if (isDoubleTap) SeekState.DOUBLE_TAP else SeekState.NONE
        binding.secondsView.visibility = View.VISIBLE
        animationHandler.removeCallbacks(doubleTapSeekRunnable)
        animationHandler.postDelayed(doubleTapSeekRunnable, 750L)

        if (time < 0) {
            binding.secondsView.updateLayoutParams<ConstraintLayout.LayoutParams> {
                rightToRight = ConstraintLayout.LayoutParams.UNSET
                leftToLeft = ConstraintLayout.LayoutParams.PARENT_ID
            }
            binding.secondsView.isForward = false

            if (doubleTapBg != binding.rewBg) {
                doubleTapBg.visibility = View.GONE
                binding.secondsView.seconds = 0
                doubleTapBg = binding.rewBg
            }
            doubleTapBg.visibility = View.VISIBLE

            if (videoChapterText != null) {
                binding.secondsView.binding.doubleTapSeconds.text = videoChapterText
            } else {
                binding.secondsView.seconds -= time
            }
        } else {
            binding.secondsView.updateLayoutParams<ConstraintLayout.LayoutParams> {
                rightToRight = ConstraintLayout.LayoutParams.PARENT_ID
                leftToLeft = ConstraintLayout.LayoutParams.UNSET
            }
            binding.secondsView.isForward = true

            if (doubleTapBg != binding.ffwdBg) {
                doubleTapBg.visibility = View.GONE
                binding.secondsView.seconds = 0
                doubleTapBg = binding.ffwdBg
            }
            doubleTapBg.visibility = View.VISIBLE

            if (videoChapterText != null) {
                binding.secondsView.binding.doubleTapSeconds.text = videoChapterText
            } else {
                binding.secondsView.seconds += time
            }
        }
        if (videoChapterText == null || chapterSeek != null) {
            playerControls.hideUiForSeek()
        }
        binding.secondsView.start()
        ViewAnimationUtils.createCircularReveal(
            view,
            x,
            y,
            0f,
            kotlin.math.max(view.height, view.width).toFloat(),
        ).setDuration(500).start()

        ObjectAnimator.ofFloat(view, "alpha", 0f, 0.15f).setDuration(500).start()
        ObjectAnimator.ofFloat(view, "alpha", 0.15f, 0.15f, 0f).setDuration(1000).start()

        if (chapterSeek == null) {
            MPVLib.command(arrayOf("seek", time.toString(), "relative+exact"))
        } else {
            MPVLib.command(arrayOf("add", "chapter", chapterSeek))
        }
    }

    // Gesture Functions -- Start --

    internal fun initSeek() {
        initialSeek = player.timePos ?: -1
    }

    internal fun horizontalScroll(diff: Float, final: Boolean = false) {
        // disable seeking when timePos is not available
        val duration = player.duration ?: 0
        if (duration == 0 || initialSeek < 0) {
            return
        }
        val newPos = (initialSeek + diff.toInt()).coerceIn(0, duration)
        if (playerPreferences.audioplayerSmoothSeek().get() && final) {
            player.timePos = newPos
        } else {
            MPVLib.command(
                arrayOf("seek", newPos.toString(), "absolute+keyframes"),
            )
        }
        val newDiff = newPos - initialSeek

        playerControls.showSeekText(newPos, newDiff)
    }

    fun verticalScrollRight(diff: Float) {
        if (diff != 0F) {
            fineVolume = (fineVolume + (diff * maxVolume)).coerceIn(
                0F,
                maxVolume.toFloat(),
            )
        }
        val newVolume = fineVolume.toInt()
        audioManager!!.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)

        playerControls.showVolumeBar(diff != 0F, newVolume)
    }

    fun verticalScrollLeft(diff: Float) {
        if (diff != 0F) brightness = (brightness + diff).coerceIn(-0.75F, 1F)
        window.attributes = window.attributes.apply {
            // value of 0 and 0.01 is broken somehow
            screenBrightness = brightness.coerceAtLeast(0.02F)
        }

        if (brightness < 0) {
            binding.brightnessOverlay.visibility = View.VISIBLE
            val alpha = (abs(brightness) * 256).toInt()
            binding.brightnessOverlay.setBackgroundColor(Color.argb(alpha, 0, 0, 0))
        } else {
            binding.brightnessOverlay.visibility = View.GONE
        }
        val finalBrightness = (brightness * 100).roundToInt()

        playerControls.showBrightnessBar(diff != 0F, finalBrightness)
    }

    // Gesture Functions -- End --

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                verticalScrollRight(1 / maxVolume.toFloat())
                return true
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                verticalScrollRight(-1 / maxVolume.toFloat())
                return true
            }
            // Not entirely sure how to handle these KeyCodes yet, need to learn some more
            /**
            KeyEvent.KEYCODE_MEDIA_NEXT -> {
            switchEpisode(false)
            return true
            }

            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
            switchEpisode(true)
            return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY -> {
            player.paused = true
            doubleTapPlayPause()
            return true
            }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
            player.paused = false
            doubleTapPlayPause()
            return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
            doubleTapPlayPause()
            return true
            }
             */
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                doubleTapSeek(playerPreferences.skipLengthPreference().get())
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                doubleTapSeek(-playerPreferences.skipLengthPreference().get())
                return true
            }
            KeyEvent.KEYCODE_SPACE -> {
                doubleTapPlayPause()
                return true
            }
            // add other keycodes as needed
            else -> {
                if (player.onKey(event!!)) return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    // Removing this causes mpv to repeat the last repeated input
    // that's not specified in onKeyDown indefinitely for some reason
    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (player.onKey(event!!)) return true
        return super.onKeyUp(keyCode, event)
    }

    /**
     * Called from the presenter when a screenshot is ready to be shared. It shows Android's
     * default sharing tool.
     */
    private fun onShareImageResult(uri: Uri, seconds: String) {
        val audiobook = viewModel.currentAudiobook ?: return
        val episode = viewModel.currentChapter ?: return

        val intent = uri.toShareIntent(
            context = applicationContext,
            message = stringResource(MR.strings.share_screenshot_info, audiobook.title, episode.name, seconds),
        )
        startActivity(Intent.createChooser(intent, stringResource(MR.strings.action_share)))
    }

    /**
     * Called from the presenter when a screenshot is saved or fails. It shows a message
     * or logs the event depending on the [result].
     */
    private fun onSaveImageResult(result: PlayerViewModel.SaveImageResult) {
        when (result) {
            is PlayerViewModel.SaveImageResult.Success -> {
                toast(MR.strings.picture_saved)
            }
            is PlayerViewModel.SaveImageResult.Error -> {
                logcat(LogPriority.ERROR, result.error)
            }
        }
    }

    /**
     * Called from the presenter when a screenshot is set as cover or fails.
     * It shows a different message depending on the [result].
     */
    private fun onSetAsCoverResult(result: SetAsCover) {
        toast(
            when (result) {
                SetAsCover.Success -> MR.strings.cover_updated
                SetAsCover.AddToLibraryFirst -> MR.strings.notification_first_add_to_library
                SetAsCover.Error -> MR.strings.notification_cover_update_failed
            },
        )
    }

    @Suppress("UNUSED_PARAMETER")
    fun cycleSpeed(view: View) {
        player.cycleSpeed()
        refreshUi()
    }

    @Suppress("UNUSED_PARAMETER")
    fun skipIntro(view: View) {
        if (skipType != null) {
            // this stops the counter
            if (waitingAniSkip > 0 && netflixStyle) {
                waitingAniSkip = -1
                return
            }
            skipType.let {
                MPVLib.command(
                    arrayOf(
                        "seek",
                        "${aniSkipInterval!!.first{it.skipType == skipType}.interval.endTime}",
                        "absolute",
                    ),
                )
            }
        }
    }

    /**
     * Updates the player UI text and controls in a separate thread
     */
    internal fun refreshUi() {
        viewModel.viewModelScope.launchUI {
            setVisibilities()
            player.timePos?.let { playerControls.updatePlaybackPos(it) }
            player.duration?.let { playerControls.updatePlaybackDuration(it) }
            updatePlaybackStatus(player.paused ?: return@launchUI)
            playerControls.updateEpisodeText()
            playerControls.updatePlaylistButtons()
            playerControls.updateSpeedButton()
            withIOContext { player.loadTracks() }
        }
    }

    @Suppress("DEPRECATION")
    private fun setVisibilities() {
        val windowInsetsController by lazy { WindowInsetsControllerCompat(window, binding.root) }
        binding.root.systemUiVisibility =
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LOW_PROFILE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && playerPreferences.audioplayerFullscreen().get()) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    private fun updatePlaybackStatus(paused: Boolean) {
        if (pip.supportedAndEnabled && PipState.mode == PipState.ON) pip.update(!paused)
        val r = if (paused) R.drawable.ic_play_arrow_64dp else R.drawable.ic_pause_64dp
        playerControls.binding.playBtn.setImageResource(r)

        if (paused) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    @Suppress("DEPRECATION")
    @Deprecated("Deprecated in Java")
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        PipState.mode = if (isInPictureInPictureMode) PipState.ON else PipState.OFF
        super.onPictureInPictureModeChanged(isInPictureInPictureMode)

        if (PipState.mode == PipState.ON) {
            // On Android TV it is required to hide controller in this PIP change callback
            binding.loadingIndicator.indicatorSize = binding.loadingIndicator.indicatorSize / 2
            mReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent == null || ACTION_MEDIA_CONTROL != intent.action) {
                        return
                    }
                    when (intent.getIntExtra(EXTRA_CONTROL_TYPE, 0)) {
                        PIP_PLAY -> {
                            player.paused = false
                        }
                        PIP_PAUSE -> {
                            player.paused = true
                        }
                        PIP_PREVIOUS -> {
                            changeChapter(viewModel.getAdjacentChapterId(previous = true))
                        }
                        PIP_NEXT -> {
                            changeChapter(viewModel.getAdjacentChapterId(previous = false))
                        }
                        PIP_SKIP -> {
                            doubleTapSeek(time = 10)
                        }
                    }
                }
            }
            registerReceiver(mReceiver, IntentFilter(ACTION_MEDIA_CONTROL))
        } else {
            binding.loadingIndicator.indicatorSize = binding.loadingIndicator.indicatorSize * 2
            if (mReceiver != null) {
                unregisterReceiver(mReceiver)
                mReceiver = null
            }
        }
    }

    /**
     * Called from the presenter if the initial load couldn't load the videos of the episode. In
     * this case the activity is closed and a toast is shown to the user.
     */
    private fun setInitialEpisodeError(error: Throwable) {
        toast(error.message)
        logcat(LogPriority.ERROR, error)
        finish()
    }

    private fun setVideoList(
        qualityIndex: Int,
        videos: List<Audio>?,
        fromStart: Boolean = false,
        position: Long? = null,
    ) {
        logcat { "Loading audio file" }
        if (playerIsDestroyed) return
        currentVideoList = videos
        currentVideoList?.getOrNull(qualityIndex)?.let {
            streams.quality.index = qualityIndex
            setHttpOptions(it)
            if (viewModel.state.value.isLoadingChapter) {
                viewModel.currentChapter?.let { episode ->
                    val preservePos = playerPreferences.preserveWatchingPosition().get()
                    val resumePosition = if (position != null) {
                        position
                    } else if ((episode.read && !preservePos) || fromStart) {
                        0L
                    } else {
                        episode.last_second_read
                    }
                    MPVLib.command(arrayOf("set", "start", "${resumePosition / 1000F}"))
                    playerControls.updatePlaybackDuration(resumePosition.toInt() / 1000)
                }
            } else {
                player.timePos?.let {
                    MPVLib.command(arrayOf("set", "start", "${player.timePos}"))
                }
            }
            streams.subtitle.tracks = arrayOf(Track("nothing", "None")) + it.subtitleTracks.toTypedArray()
            streams.audio.tracks = arrayOf(Track("nothing", "None")) + it.audioTracks.toTypedArray()
            if (!activityIsForeground && didResumeBackgroundPlayback) {
                MPVLib.command(arrayOf("loadfile", parseVideoUrl(it.audioUrl), "append"))
                showToast(getString(R.string.notice_file_appended))
                moveTaskToBack(true)
            } else {
                MPVLib.command(arrayOf("loadfile", parseVideoUrl(it.audioUrl)))
            }
            MPVLib.setPropertyString("vo", "null")
        }
        refreshUi()
    }

    private fun showToast(msg: String) {
        toast.setText(msg)
        toast.show()
    }

    @SuppressLint("ShowToast")
    private fun initMessageToast() {
        toast = Toast.makeText(this, "This totally shouldn't be seen", Toast.LENGTH_SHORT)
        toast.setGravity(Gravity.TOP or Gravity.CENTER_HORIZONTAL, 0, 0)
    }

    private fun parseVideoUrl(videoUrl: String?): String? {
        val uri = Uri.parse(videoUrl)
        return openContentFd(uri) ?: videoUrl
    }

    private fun openContentFd(uri: Uri): String? {
        if (uri.scheme != "content") return null
        val resolver = applicationContext.contentResolver
        logcat { "Resolving content URI: $uri" }
        val fd = try {
            val desc = resolver.openFileDescriptor(uri, "r")
            desc!!.detachFd()
        } catch (e: Exception) {
            logcat { "Failed to open content fd: $e" }
            return null
        }
        // Find out real file path and see if we can read it directly
        try {
            val path = File("/proc/self/fd/$fd").canonicalPath
            if (!path.startsWith("/proc") && File(path).canRead()) {
                logcat { "Found real file path: $path" }
                ParcelFileDescriptor.adoptFd(fd).close() // we don't need that anymore
                return path
            }
        } catch (_: Exception) {}
        // Else, pass the fd to mpv
        return "fdclose://$fd"
    }

    private fun setHttpOptions(video: Audio) {
        if (viewModel.isChapterOnline() != true) return
        val source = viewModel.currentSource as AudiobookHttpSource

        val headers = (video.headers ?: source.headers)
            .toMultimap()
            .mapValues { it.value.firstOrNull() ?: "" }
            .toMutableMap()

        val httpHeaderString = headers.map {
            it.key + ": " + it.value.replace(",", "\\,")
        }.joinToString(",")

        MPVLib.setOptionString("http-header-fields", httpHeaderString)

        // need to fix the cache
        // MPVLib.setOptionString("cache-on-disk", "yes")
        // val cacheDir = File(applicationContext.filesDir, "media").path
        // MPVLib.setOptionString("cache-dir", cacheDir)
    }

    private fun clearTracks() {
        val count = MPVLib.getPropertyInt("track-list/count")!!
        // Note that because events are async, properties might disappear at any moment
        // so use ?: continue instead of !!
        for (i in 0 until count) {
            val type = MPVLib.getPropertyString("track-list/$i/type") ?: continue
            if (!player.tracks.containsKey(type)) {
                continue
            }
            val mpvId = MPVLib.getPropertyInt("track-list/$i/id") ?: continue
            when (type) {
                "video" -> MPVLib.command(arrayOf("video-remove", "$mpvId"))
                "audio" -> MPVLib.command(arrayOf("audio-remove", "$mpvId"))
                "sub" -> MPVLib.command(arrayOf("sub-remove", "$mpvId"))
            }
        }
    }

    // TODO: exception java.util.ConcurrentModificationException:
    //  UPDATE: MAY HAVE BEEN FIXED
    // at java.lang.Object java.util.ArrayList$Itr.next() (ArrayList.java:860)
    // at void eu.kanade.tachiyomi.ui.player.PlayerActivity.fileLoaded() (PlayerActivity.kt:1874)
    // at void eu.kanade.tachiyomi.ui.player.PlayerActivity.event(int) (PlayerActivity.kt:1566)
    // at void is.xyz.mpv.MPVLib.event(int) (MPVLib.java:86)
    @SuppressLint("SourceLockedOrientationActivity")
    internal suspend fun fileLoaded() {
        val localLangName = LocaleHelper.getSimpleLocaleDisplayName()
        clearTracks()
        player.loadTracks()
        streams.subtitle.tracks += player.tracks.getOrElse("sub") { emptyList() }
            .drop(1).map { track ->
                Track(track.mpvId.toString(), track.name)
            }.toTypedArray()
        streams.audio.tracks += player.tracks.getOrElse("audio") { emptyList() }
            .drop(1).map { track ->
                Track(track.mpvId.toString(), track.name)
            }.toTypedArray()
        if (hadPreviousSubs) {
            streams.subtitle.tracks.getOrNull(streams.subtitle.index)?.let { sub ->
                MPVLib.command(arrayOf("sub-add", sub.url, "select", sub.url))
            }
        } else {
            currentVideoList?.getOrNull(streams.quality.index)
                ?.subtitleTracks?.let { tracks ->
                    val langIndex = tracks.indexOfFirst {
                        it.lang.contains(localLangName)
                    }
                    val requestedLanguage = if (langIndex == -1) 0 else langIndex
                    tracks.getOrNull(requestedLanguage)?.let { sub ->
                        hadPreviousSubs = true
                        streams.subtitle.index = requestedLanguage + 1
                        MPVLib.command(arrayOf("sub-add", sub.url, "select", sub.url))
                    }
                } ?: run {
                val mpvSub = player.tracks.getOrElse("sub") { emptyList() }
                    .firstOrNull { player.sid == it.mpvId }
                streams.subtitle.index = mpvSub?.let {
                    streams.subtitle.tracks.indexOfFirst { it.url == mpvSub.mpvId.toString() }
                }?.coerceAtLeast(0) ?: 0
            }
        }
        if (hadPreviousAudio) {
            streams.audio.tracks.getOrNull(streams.audio.index)?.let { audio ->
                MPVLib.command(arrayOf("audio-add", audio.url, "select", audio.url))
            }
        } else {
            currentVideoList?.getOrNull(streams.quality.index)
                ?.audioTracks?.let { tracks ->
                    val langIndex = tracks.indexOfFirst {
                        it.lang.contains(localLangName)
                    }
                    val requestedLanguage = if (langIndex == -1) 0 else langIndex
                    tracks.getOrNull(requestedLanguage)?.let { audio ->
                        hadPreviousAudio = true
                        streams.audio.index = requestedLanguage + 1
                        MPVLib.command(arrayOf("audio-add", audio.url, "select", audio.url))
                    }
                } ?: run {
                val mpvAudio = player.tracks.getOrElse("audio") { emptyList() }
                    .firstOrNull { player.aid == it.mpvId }
                streams.audio.index = mpvAudio?.let {
                    streams.audio.tracks.indexOfFirst { it.url == mpvAudio.mpvId.toString() }
                }?.coerceAtLeast(0) ?: 0
            }
        }

        viewModel.viewModelScope.launchUI {
            if (playerPreferences.adjustOrientationVideoDimensions().get()) {
                this@AudioplayerActivity.requestedOrientation =
                    playerPreferences.defaultAudioplayerOrientationPortrait().get()
            }

            viewModel.mutableState.update {
                it.copy(isLoadingChapter = false)
            }
        }
        // aniSkip stuff
        waitingAniSkip = playerPreferences.waitingTimeAniSkip().get()
        runBlocking {
            if (aniSkipEnable) {
                aniSkipInterval = viewModel.aniSkipResponse(player.duration)
                aniSkipInterval?.let {
                    aniskipStamps = it
                    updateChapters(it, player.duration)
                }
            }
        }
    }

    private var aniskipStamps: List<Stamp> = emptyList()

    private fun updateChapters(stamps: List<Stamp>? = null, duration: Int? = null) {
        val aniskipStamps = stamps ?: aniskipStamps
        val sortedAniskipStamps = aniskipStamps.sortedBy { it.interval.startTime }
        val aniskipChapters = sortedAniskipStamps.mapIndexed { i, it ->
            val startTime = if (i == 0 && it.interval.startTime < 1.0) {
                0.0
            } else {
                it.interval.startTime
            }
            val startChapter = VideoChapter(
                index = -2, // Index -2 is used to indicate that this is an AniSkip chapter
                title = it.skipType.getString(),
                time = startTime,
            )
            val nextStart = sortedAniskipStamps.getOrNull(i + 1)?.interval?.startTime
            val isNotLastChapter = abs(it.interval.endTime - (duration?.toDouble() ?: -2.0)) > 1.0
            val isNotAdjacent = nextStart == null || (abs(it.interval.endTime - nextStart) > 1.0)
            if (isNotLastChapter && isNotAdjacent) {
                val endChapter = VideoChapter(
                    index = -1,
                    title = null,
                    time = it.interval.endTime,
                )
                return@mapIndexed listOf(startChapter, endChapter)
            } else {
                listOf(startChapter)
            }
        }.flatten()
        val playerChapters = player.loadChapters().filter { playerChapter ->
            aniskipChapters.none { aniskipChapter ->
                abs(aniskipChapter.time - playerChapter.time) < 1.0 && aniskipChapter.index == -2
            }
        }.sortedBy { it.time }.mapIndexed { i, it ->
            if (i == 0 && it.time < 1.0) {
                VideoChapter(
                    it.index,
                    it.title,
                    0.0,
                )
            } else {
                it
            }
        }
        val filteredAniskipChapters = aniskipChapters.filter { aniskipChapter ->
            playerChapters.none { playerChapter ->
                abs(aniskipChapter.time - playerChapter.time) < 1.0 && aniskipChapter.index != -2
            }
        }
        val startChapter = if ((playerChapters + filteredAniskipChapters).isNotEmpty() &&
            playerChapters.none { it.time == 0.0 } &&
            filteredAniskipChapters.none { it.time == 0.0 }
        ) {
            listOf(
                VideoChapter(
                    index = -1,
                    title = null,
                    time = 0.0,
                ),
            )
        } else {
            emptyList()
        }
        val combinedChapters = (startChapter + playerChapters + filteredAniskipChapters).sortedBy { it.time }
        videoChapters = combinedChapters
    }

    private val aniSkipEnable = playerPreferences.aniSkipEnabled().get()
    private val netflixStyle = playerPreferences.enableNetflixStyleAniSkip().get()

    private var aniSkipInterval: List<Stamp>? = null
    private var waitingAniSkip = playerPreferences.waitingTimeAniSkip().get()

    private var skipType: SkipType? = null

    private suspend fun aniSkipStuff(position: Long) {
        if (!aniSkipEnable) return
        // if it doesn't find any interval it will show the +85 button
        if (aniSkipInterval == null) return

        val autoSkipAniSkip = playerPreferences.autoSkipAniSkip().get()

        skipType =
            aniSkipInterval
                ?.firstOrNull {
                    it.interval.startTime <= position &&
                        it.interval.endTime > position
                }?.skipType
        skipType?.let { skipType ->
            if (netflixStyle) {
                // show a toast with the seconds before the skip
                if (waitingAniSkip == playerPreferences.waitingTimeAniSkip().get()) {
                    toast(
                        "AniSkip: ${stringResource(MR.strings.player_aniskip_dontskip_toast,waitingAniSkip)}",
                    )
                }
                waitingAniSkip--
            } else if (autoSkipAniSkip) {
                skipType.let {
                    MPVLib.command(
                        arrayOf(
                            "seek",
                            "${aniSkipInterval!!.first{it.skipType == skipType}.interval.endTime}",
                            "absolute",
                        ),
                    )
                }
            } else {

            }
        } ?: run {
            refreshUi()
        }
    }

    // mpv events

    internal fun eventPropertyUi(property: String, value: Long) {
        when (property) {
            "demuxer-cache-time" -> playerControls.updateBufferPosition(value.toInt())
            "time-pos" -> {
                playerControls.updatePlaybackPos(value.toInt())
                viewModel.viewModelScope.launchUI { aniSkipStuff(value) }
                updatePlaybackState()
            }
            "duration" -> {
                playerControls.updatePlaybackDuration(value.toInt())
                mediaSession.isActive = true
                updatePlaybackState()
            }
        }
    }

    internal fun eventPropertyUi(property: String, value: Boolean) {
        if (!activityIsForeground) return
        when (property) {
            "seeking" -> isSeeking(value)
            "paused-for-cache" -> {
                showLoadingIndicator(value)
                updatePlaybackState(cachePause = true)
            }
            "pause" -> {
                if (!isFinishing) {
                    setAudioFocus(value)
                    updatePlaybackStatus(value)
                    updatePlaybackState(pause = true)
                }
            }
            "eof-reached" -> endFile(value)
        }
    }

    internal fun eventPropertyUi(property: String) {
        if (!activityIsForeground) return
        when (property) {
            "chapter-list" -> updateChapters()
        }
    }

    private val nextEpisodeRunnable = Runnable {
        changeChapter(
            viewModel.getAdjacentChapterId(previous = false),
            autoPlay = true,
        )
    }

    private fun endFile(eofReached: Boolean) {
        animationHandler.removeCallbacks(nextEpisodeRunnable)
        if (eofReached && playerPreferences.autoplayEnabled().get()) {
            animationHandler.postDelayed(nextEpisodeRunnable, 1000L)
        }
    }
}
