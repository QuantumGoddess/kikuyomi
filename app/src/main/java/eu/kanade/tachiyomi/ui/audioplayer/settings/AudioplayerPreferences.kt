package eu.kanade.tachiyomi.ui.audioplayer.settings

import eu.kanade.tachiyomi.ui.audioplayer.viewer.AspectState
import eu.kanade.tachiyomi.ui.audioplayer.viewer.HwDecState
import tachiyomi.core.preference.PreferenceStore

class AudioplayerPreferences(
    private val preferenceStore: PreferenceStore,
) {
    fun preserveWatchingPosition() = preferenceStore.getBoolean(
        "pref_preserve_watching_position",
        false,
    )

    fun enablePip() = preferenceStore.getBoolean("pref_enable_pip", true)
    fun pipEpisodeToasts() = preferenceStore.getBoolean("pref_pip_episode_toasts", true)
    fun pipOnExit() = preferenceStore.getBoolean("pref_pip_on_exit", false)
    fun pipReplaceWithPrevious() = preferenceStore.getBoolean("pip_replace_with_previous", false)

    fun rememberAudioplayerBrightness() = preferenceStore.getBoolean("pref_remember_brightness", false)
    fun audioplayerBrightnessValue() = preferenceStore.getFloat("audioplayer_brightness_value", -1.0F)

    fun rememberAudioplayerVolume() = preferenceStore.getBoolean("pref_remember_volume", false)
    fun audioplayerVolumeValue() = preferenceStore.getFloat("audioplayer_volume_value", -1.0F)

    fun autoplayEnabled() = preferenceStore.getBoolean("pref_auto_play_enabled", false)

    fun invertedPlaybackTxt() = preferenceStore.getBoolean("pref_invert_playback_txt", false)
    fun invertedDurationTxt() = preferenceStore.getBoolean("pref_invert_duration_txt", false)

    fun mpvConf() = preferenceStore.getString("pref_mpv_conf", "")

    fun mpvInput() = preferenceStore.getString("pref_mpv_input", "")

    fun defaultAudioplayerOrientationType() = preferenceStore.getInt(
        "pref_default_audioplayer_orientation_type_key",
        10,
    )
    fun adjustOrientationVideoDimensions() = preferenceStore.getBoolean(
        "pref_adjust_orientation_video_dimensions",
        true,
    )

    fun defaultAudioplayerOrientationLandscape() = preferenceStore.getInt(
        "pref_default_audioplayer_orientation_landscape_key",
        6,
    )
    fun defaultAudioplayerOrientationPortrait() = preferenceStore.getInt(
        "pref_default_audioplayer_orientation_portrait_key",
        7,
    )

    fun audioplayerSpeed() = preferenceStore.getFloat("pref_audioplayer_speed", 1F)

    fun audioplayerSmoothSeek() = preferenceStore.getBoolean("pref_audioplayer_smooth_seek", false)

    fun mediaChapterSeek() = preferenceStore.getBoolean("pref_media_control_chapter_seeking", false)

    fun audioplayerViewMode() = preferenceStore.getInt("pref_audioplayer_view_mode", AspectState.FIT.index)

    fun audioplayerFullscreen() = preferenceStore.getBoolean("audioplayer_fullscreen", true)

    fun hideControls() = preferenceStore.getBoolean("audioplayer_hide_controls", false)

    fun screenshotSubtitles() = preferenceStore.getBoolean("pref_screenshot_subtitles", false)

    fun gestureVolumeBrightness() = preferenceStore.getBoolean(
        "pref_gesture_volume_brightness",
        true,
    )
    fun gestureHorizontalSeek() = preferenceStore.getBoolean("pref_gesture_horizontal_seek", true)
    fun audioplayerStatisticsPage() = preferenceStore.getInt("pref_audioplayer_statistics_page", 0)

    fun alwaysUseExternalAudioplayer() = preferenceStore.getBoolean(
        "pref_always_use_external_audioplayer",
        false,
    )
    fun externalAudioplayerPreference() = preferenceStore.getString("external_audioplayer_preference", "")

    fun progressPreference() = preferenceStore.getFloat("pref_progress_preference", 0.85F)

    fun defaultIntroLength() = preferenceStore.getInt("pref_default_intro_length", 85)
    fun skipLengthPreference() = preferenceStore.getInt("pref_skip_length_preference", 10)

    fun aniSkipEnabled() = preferenceStore.getBoolean("pref_enable_ani_skip", false)
    fun autoSkipAniSkip() = preferenceStore.getBoolean("pref_enable_auto_skip_ani_skip", false)
    fun waitingTimeAniSkip() = preferenceStore.getInt("pref_waiting_time_aniskip", 5)
    fun enableNetflixStyleAniSkip() = preferenceStore.getBoolean(
        "pref_enable_netflixStyle_aniskip",
        false,
    )

    fun hwDec() = preferenceStore.getString("pref_hwdec", HwDecState.defaultHwDec.mpvValue)
    fun deband() = preferenceStore.getInt("pref_deband", 0)

    fun rememberAudioDelay() = preferenceStore.getBoolean("pref_remember_audio_delay", false)
    fun audioDelay() = preferenceStore.getInt("pref_audio_delay", 0)

    fun rememberSubtitlesDelay() = preferenceStore.getBoolean(
        "pref_remember_subtitles_delay",
        false,
    )
    fun subtitlesDelay() = preferenceStore.getInt("pref_subtitles_delay", 0)

    fun overrideSubsASS() = preferenceStore.getBoolean("pref_override_subtitles_ass", false)

    fun subtitleFont() = preferenceStore.getString("pref_subtitle_font", "Sans Serif")

    fun subtitleFontSize() = preferenceStore.getInt("pref_subtitles_font_size", 55)
    fun boldSubtitles() = preferenceStore.getBoolean("pref_bold_subtitles", false)
    fun italicSubtitles() = preferenceStore.getBoolean("pref_italic_subtitles", false)

    fun textColorSubtitles() = preferenceStore.getInt("pref_text_color_subtitles", -1)
    fun borderColorSubtitles() = preferenceStore.getInt("pref_border_color_subtitles", -16777216)
    fun backgroundColorSubtitles() = preferenceStore.getInt("pref_background_color_subtitles", 0)
}
