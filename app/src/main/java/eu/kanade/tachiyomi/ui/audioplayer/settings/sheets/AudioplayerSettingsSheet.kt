package eu.kanade.tachiyomi.ui.audioplayer.settings.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.tachiyomi.ui.audioplayer.settings.AudioplayerSettingsScreenModel
import eu.kanade.tachiyomi.ui.audioplayer.viewer.HwDecState
import eu.kanade.tachiyomi.ui.audioplayer.viewer.PlayerStatsPage
import `is`.xyz.mpv.MPVLib
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState

@Composable
fun AudioplayerSettingsSheet(
    screenModel: AudioplayerSettingsScreenModel,
    onDismissRequest: () -> Unit,
) {
    val verticalGesture by remember {
        mutableStateOf(
            screenModel.preferences.gestureVolumeBrightness(),
        )
    }
    val horizontalGesture by remember {
        mutableStateOf(
            screenModel.preferences.gestureHorizontalSeek(),
        )
    }
    var statisticsPage by remember {
        mutableStateOf(
            screenModel.preferences.audioplayerStatisticsPage().get(),
        )
    }
    var decoder by remember { mutableStateOf(screenModel.preferences.hwDec().get()) }

    // TODO: Shift to MPV-Lib
    val toggleAudioplayerStatsPage: (Int) -> Unit = { page ->
        if ((statisticsPage == 0) xor (page == 0)) {
            MPVLib.command(arrayOf("script-binding", "stats/display-stats-toggle"))
        }
        if (page != 0) {
            MPVLib.command(arrayOf("script-binding", "stats/display-page-$page"))
        }
        statisticsPage = page
        screenModel.preferences.audioplayerStatisticsPage().set(page)
    }

    val toggleAudioplayerDecoder: (HwDecState) -> Unit = { hwDecState ->
        val hwDec = hwDecState.mpvValue
        MPVLib.setOptionString("hwdec", hwDec)
        decoder = hwDec
        screenModel.preferences.hwDec().set(hwDec)
    }

    AdaptiveSheet(
        hideSystemBars = true,
        onDismissRequest = onDismissRequest,
    ) {
        Column(
            modifier = Modifier.padding(MaterialTheme.padding.medium),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
        ) {
            Text(
                text = stringResource(MR.strings.settings_dialog_header),
                style = MaterialTheme.typography.titleMedium,
                fontSize = 20.sp,
            )

            screenModel.ToggleableRow(
                textRes = MR.strings.enable_volume_brightness_gestures,
                isChecked = verticalGesture.collectAsState().value,
                onClick = { screenModel.togglePreference { verticalGesture } },
            )

            screenModel.ToggleableRow(
                textRes = MR.strings.enable_horizontal_seek_gesture,
                isChecked = horizontalGesture.collectAsState().value,
                onClick = { screenModel.togglePreference { horizontalGesture } },
            )

            // TODO: (Merge_Change) below two Columns to be switched to using 'SettingsChipRow'
            //  from 'SettingsItems.kt'

            Column(
                modifier = Modifier.fillMaxWidth().padding(
                    horizontal = MaterialTheme.padding.medium,
                ),
            ) {
                Text(
                    text = stringResource(MR.strings.player_hwdec_mode),
                    style = MaterialTheme.typography.titleSmall,
                )

                Row(
                    modifier = Modifier.padding(vertical = MaterialTheme.padding.tiny),
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                ) {
                    HwDecState.entries.forEach {
                        if (!HwDecState.isHwSupported && it.title == "HW+") return@forEach
                        FilterChip(
                            selected = decoder == it.mpvValue,
                            onClick = { toggleAudioplayerDecoder(it) },
                            label = { Text(it.title) },
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth().padding(
                    horizontal = MaterialTheme.padding.medium,
                ),
            ) {
                Text(
                    text = stringResource(MR.strings.toggle_player_statistics_page),
                    style = MaterialTheme.typography.titleSmall,
                )

                Row(
                    modifier = Modifier.padding(vertical = MaterialTheme.padding.tiny),
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                ) {
                    PlayerStatsPage.entries.forEach {
                        FilterChip(
                            selected = statisticsPage == it.page,
                            onClick = { toggleAudioplayerStatsPage(it.page) },
                            label = { Text(stringResource(it.textRes)) },
                        )
                    }
                }
            }
        }
    }
}
