package eu.kanade.tachiyomi.ui.browse.audiobook.migration.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.StateScreenModel
import eu.kanade.domain.entries.audiobook.interactor.UpdateAudiobook
import eu.kanade.domain.entries.audiobook.model.hasCustomCover
import eu.kanade.domain.entries.audiobook.model.toSAudiobook
import eu.kanade.domain.items.audiobookchapter.interactor.SyncChaptersWithSource
import eu.kanade.tachiyomi.audiobooksource.AudiobookSource
import eu.kanade.tachiyomi.audiobooksource.model.SChapter
import eu.kanade.tachiyomi.data.cache.AudiobookCoverCache
import eu.kanade.tachiyomi.data.download.audiobook.AudiobookDownloadManager
import eu.kanade.tachiyomi.data.track.EnhancedAudiobookTracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.ui.browse.audiobook.migration.AudiobookMigrationFlags
import kotlinx.coroutines.flow.update
import tachiyomi.core.preference.Preference
import tachiyomi.core.preference.PreferenceStore
import tachiyomi.core.util.lang.launchIO
import tachiyomi.core.util.lang.withUIContext
import tachiyomi.domain.category.audiobook.interactor.GetAudiobookCategories
import tachiyomi.domain.category.audiobook.interactor.SetAudiobookCategories
import tachiyomi.domain.entries.audiobook.model.Audiobook
import tachiyomi.domain.entries.audiobook.model.AudiobookUpdate
import tachiyomi.domain.items.audiobookchapter.interactor.GetChaptersByAudiobookId
import tachiyomi.domain.items.audiobookchapter.model.toChapterUpdate
import tachiyomi.domain.items.audiobookchapter.interactor.UpdateChapter
import tachiyomi.domain.source.audiobook.service.AudiobookSourceManager
import tachiyomi.domain.track.audiobook.interactor.GetAudiobookTracks
import tachiyomi.domain.track.audiobook.interactor.InsertAudiobookTrack
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.LabeledCheckbox
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.Instant

@Composable
internal fun MigrateAudiobookDialog(
    oldAudiobook: Audiobook,
    newAudiobook: Audiobook,
    screenModel: MigrateAudiobookDialogScreenModel,
    onDismissRequest: () -> Unit,
    onClickTitle: () -> Unit,
    onPopScreen: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val state by screenModel.state.collectAsState()

    val flags = remember { AudiobookMigrationFlags.getFlags(oldAudiobook, screenModel.migrateFlags.get()) }
    val selectedFlags = remember { flags.map { it.isDefaultSelected }.toMutableStateList() }

    if (state.isMigrating) {
        LoadingScreen(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.7f)),
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = {
                Text(text = stringResource(MR.strings.migration_dialog_what_to_include))
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                ) {
                    flags.forEachIndexed { index, flag ->
                        LabeledCheckbox(
                            label = stringResource(flag.titleId),
                            checked = selectedFlags[index],
                            onCheckedChange = { selectedFlags[index] = it },
                        )
                    }
                }
            },
            confirmButton = {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    TextButton(
                        onClick = {
                            onDismissRequest()
                            onClickTitle()
                        },
                    ) {
                        Text(text = stringResource(MR.strings.action_show_audiobook))
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    TextButton(
                        onClick = {
                            scope.launchIO {
                                screenModel.migrateAudiobook(
                                    oldAudiobook,
                                    newAudiobook,
                                    false,
                                    AudiobookMigrationFlags.getSelectedFlagsBitMap(selectedFlags, flags),
                                )
                                withUIContext { onPopScreen() }
                            }
                        },
                    ) {
                        Text(text = stringResource(MR.strings.copy))
                    }
                    TextButton(
                        onClick = {
                            scope.launchIO {
                                screenModel.migrateAudiobook(
                                    oldAudiobook,
                                    newAudiobook,
                                    true,
                                    AudiobookMigrationFlags.getSelectedFlagsBitMap(selectedFlags, flags),
                                )

                                withUIContext { onPopScreen() }
                            }
                        },
                    ) {
                        Text(text = stringResource(MR.strings.migrate))
                    }
                }
            },
        )
    }
}

internal class MigrateAudiobookDialogScreenModel(
    private val sourceManager: AudiobookSourceManager = Injekt.get(),
    private val downloadManager: AudiobookDownloadManager = Injekt.get(),
    private val updateAudiobook: UpdateAudiobook = Injekt.get(),
    private val getChaptersByAudiobookId: GetChaptersByAudiobookId = Injekt.get(),
    private val syncChaptersWithSource: SyncChaptersWithSource = Injekt.get(),
    private val updateChapter: UpdateChapter = Injekt.get(),
    private val getCategories: GetAudiobookCategories = Injekt.get(),
    private val setAudiobookCategories: SetAudiobookCategories = Injekt.get(),
    private val getTracks: GetAudiobookTracks = Injekt.get(),
    private val insertTrack: InsertAudiobookTrack = Injekt.get(),
    private val coverCache: AudiobookCoverCache = Injekt.get(),
    private val preferenceStore: PreferenceStore = Injekt.get(),
) : StateScreenModel<MigrateAudiobookDialogScreenModel.State>(State()) {

    val migrateFlags: Preference<Int> by lazy {
        preferenceStore.getInt("migrate_flags", Int.MAX_VALUE)
    }

    private val enhancedServices by lazy {
        Injekt.get<TrackerManager>().trackers.filterIsInstance<EnhancedAudiobookTracker>()
    }

    suspend fun migrateAudiobook(
        oldAudiobook: Audiobook,
        newAudiobook: Audiobook,
        replace: Boolean,
        flags: Int,
    ) {
        migrateFlags.set(flags)
        val source = sourceManager.get(newAudiobook.source) ?: return
        val prevSource = sourceManager.get(oldAudiobook.source)

        mutableState.update { it.copy(isMigrating = true) }

        try {
            val chapters = source.getChapterList(newAudiobook.toSAudiobook())

            migrateAudiobookInternal(
                oldSource = prevSource,
                newSource = source,
                oldAudiobook = oldAudiobook,
                newAudiobook = newAudiobook,
                sourceChapters = chapters,
                replace = replace,
                flags = flags,
            )
        } catch (_: Throwable) {
            // Explicitly stop if an error occurred; the dialog normally gets popped at the end
            // anyway
            mutableState.update { it.copy(isMigrating = false) }
        }
    }

    private suspend fun migrateAudiobookInternal(
        oldSource: AudiobookSource?,
        newSource: AudiobookSource,
        oldAudiobook: Audiobook,
        newAudiobook: Audiobook,
        sourceChapters: List<SChapter>,
        replace: Boolean,
        flags: Int,
    ) {
        val migrateChapters = AudiobookMigrationFlags.hasChapters(flags)
        val migrateCategories = AudiobookMigrationFlags.hasCategories(flags)
        val migrateCustomCover = AudiobookMigrationFlags.hasCustomCover(flags)
        val deleteDownloaded = AudiobookMigrationFlags.hasDeleteDownloaded(flags)

        try {
            syncChaptersWithSource.await(sourceChapters, newAudiobook, newSource)
        } catch (_: Exception) {
            // Worst case, chapters won't be synced
        }

        // Update chapters read, bookmark and dateFetch
        if (migrateChapters) {
            val prevAudiobookChapters = getChaptersByAudiobookId.await(oldAudiobook.id)
            val audiobookChapters = getChaptersByAudiobookId.await(newAudiobook.id)

            val maxChapterRead = prevAudiobookChapters
                .filter { it.read }
                .maxOfOrNull { it.chapterNumber }

            val updatedAudiobookChapters = audiobookChapters.map { audiobookChapter ->
                var updatedChapter = audiobookChapter
                if (updatedChapter.isRecognizedNumber) {
                    val prevChapter = prevAudiobookChapters
                        .find { it.isRecognizedNumber && it.chapterNumber == updatedChapter.chapterNumber }

                    if (prevChapter != null) {
                        updatedChapter = updatedChapter.copy(
                            dateFetch = prevChapter.dateFetch,
                            bookmark = prevChapter.bookmark,
                        )
                    }

                    if (maxChapterRead != null && updatedChapter.chapterNumber <= maxChapterRead) {
                        updatedChapter = updatedChapter.copy(read = true)
                    }
                }

                updatedChapter
            }

            val chapterUpdates = updatedAudiobookChapters.map { it.toChapterUpdate() }
            updateChapter.awaitAll(chapterUpdates)
        }

        // Update categories
        if (migrateCategories) {
            val categoryIds = getCategories.await(oldAudiobook.id).map { it.id }
            setAudiobookCategories.await(newAudiobook.id, categoryIds)
        }

        // Update track
        getTracks.await(oldAudiobook.id).mapNotNull { track ->
            val updatedTrack = track.copy(audiobookId = newAudiobook.id)

            val service = enhancedServices
                .firstOrNull { it.isTrackFrom(updatedTrack, oldAudiobook, oldSource) }

            if (service != null) {
                service.migrateTrack(updatedTrack, newAudiobook, newSource)
            } else {
                updatedTrack
            }
        }
            .takeIf { it.isNotEmpty() }
            ?.let { insertTrack.awaitAll(it) }

        // Delete downloaded
        if (deleteDownloaded) {
            if (oldSource != null) {
                downloadManager.deleteAudiobook(oldAudiobook, oldSource)
            }
        }

        if (replace) {
            updateAudiobook.await(AudiobookUpdate(oldAudiobook.id, favorite = false, dateAdded = 0))
        }

        // Update custom cover (recheck if custom cover exists)
        if (migrateCustomCover && oldAudiobook.hasCustomCover()) {
            coverCache.setCustomCoverToCache(
                newAudiobook,
                coverCache.getCustomCoverFile(oldAudiobook.id).inputStream(),
            )
        }

        updateAudiobook.await(
            AudiobookUpdate(
                id = newAudiobook.id,
                favorite = true,
                chapterFlags = oldAudiobook.chapterFlags,
                viewerFlags = oldAudiobook.viewerFlags,
                dateAdded = if (replace) oldAudiobook.dateAdded else Instant.now().toEpochMilli(),
            ),
        )
    }

    @Immutable
    data class State(
        val isMigrating: Boolean = false,
    )
}
