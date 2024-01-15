package eu.kanade.presentation.browse.audiobook

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.GetApp
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.browse.BaseBrowseItem
import eu.kanade.presentation.browse.audiobook.components.AudiobookExtensionIcon
import eu.kanade.presentation.browse.manga.ExtensionHeader
import eu.kanade.presentation.browse.manga.ExtensionTrustDialog
import eu.kanade.presentation.entries.components.DotSeparatorNoSpaceText
import eu.kanade.tachiyomi.extension.InstallStep
import eu.kanade.tachiyomi.extension.audiobook.model.AudiobookExtension
import eu.kanade.tachiyomi.ui.browse.audiobook.extension.AudiobookExtensionUiModel
import eu.kanade.tachiyomi.ui.browse.audiobook.extension.AudiobookExtensionsScreenModel
import eu.kanade.tachiyomi.util.system.LocaleHelper
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.material.PullRefresh
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.components.material.topSmallPaddingValues
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.plus
import tachiyomi.presentation.core.util.secondaryItemAlpha

@Composable
fun AudiobookExtensionScreen(
    state: AudiobookExtensionsScreenModel.State,
    contentPadding: PaddingValues,
    searchQuery: String?,
    onLongClickItem: (AudiobookExtension) -> Unit,
    onClickItemCancel: (AudiobookExtension) -> Unit,
    onClickItemWebView: (AudiobookExtension.Available) -> Unit,
    onInstallExtension: (AudiobookExtension.Available) -> Unit,
    onUninstallExtension: (AudiobookExtension) -> Unit,
    onUpdateExtension: (AudiobookExtension.Installed) -> Unit,
    onTrustExtension: (AudiobookExtension.Untrusted) -> Unit,
    onOpenExtension: (AudiobookExtension.Installed) -> Unit,
    onClickUpdateAll: () -> Unit,
    onRefresh: () -> Unit,
) {
    PullRefresh(
        refreshing = state.isRefreshing,
        onRefresh = onRefresh,
        enabled = { !state.isLoading },
    ) {
        when {
            state.isLoading -> LoadingScreen(Modifier.padding(contentPadding))
            state.isEmpty -> {
                val msg = if (!searchQuery.isNullOrEmpty()) {
                    MR.strings.no_results_found
                } else {
                    MR.strings.empty_screen
                }
                EmptyScreen(
                    stringRes = msg,
                    modifier = Modifier.padding(contentPadding),
                )
            }
            else -> {
                AudiobookExtensionContent(
                    state = state,
                    contentPadding = contentPadding,
                    onLongClickItem = onLongClickItem,
                    onClickItemCancel = onClickItemCancel,
                    onClickItemWebView = onClickItemWebView,
                    onInstallExtension = onInstallExtension,
                    onUninstallExtension = onUninstallExtension,
                    onUpdateExtension = onUpdateExtension,
                    onTrustExtension = onTrustExtension,
                    onOpenExtension = onOpenExtension,
                    onClickUpdateAll = onClickUpdateAll,
                )
            }
        }
    }
}

@Composable
private fun AudiobookExtensionContent(
    state: AudiobookExtensionsScreenModel.State,
    contentPadding: PaddingValues,
    onLongClickItem: (AudiobookExtension) -> Unit,
    onClickItemWebView: (AudiobookExtension.Available) -> Unit,
    onClickItemCancel: (AudiobookExtension) -> Unit,
    onInstallExtension: (AudiobookExtension.Available) -> Unit,
    onUninstallExtension: (AudiobookExtension) -> Unit,
    onUpdateExtension: (AudiobookExtension.Installed) -> Unit,
    onTrustExtension: (AudiobookExtension.Untrusted) -> Unit,
    onOpenExtension: (AudiobookExtension.Installed) -> Unit,
    onClickUpdateAll: () -> Unit,
) {
    var trustState by remember { mutableStateOf<AudiobookExtension.Untrusted?>(null) }

    FastScrollLazyColumn(
        contentPadding = contentPadding + topSmallPaddingValues,
    ) {
        state.items.forEach { (header, items) ->
            item(
                contentType = "header",
                key = "extensionHeader-${header.hashCode()}",
            ) {
                when (header) {
                    is AudiobookExtensionUiModel.Header.Resource -> {
                        val action: @Composable RowScope.() -> Unit =
                            if (header.textRes == MR.strings.ext_updates_pending) {
                                {
                                    Button(onClick = { onClickUpdateAll() }) {
                                        Text(
                                            text = stringResource(MR.strings.ext_update_all),
                                            style = LocalTextStyle.current.copy(
                                                color = MaterialTheme.colorScheme.onPrimary,
                                            ),
                                        )
                                    }
                                }
                            } else {
                                {}
                            }
                        ExtensionHeader(
                            textRes = header.textRes,
                            modifier = Modifier.animateItemPlacement(),
                            action = action,
                        )
                    }
                    is AudiobookExtensionUiModel.Header.Text -> {
                        ExtensionHeader(
                            text = header.text,
                            modifier = Modifier.animateItemPlacement(),
                        )
                    }
                }
            }

            items(
                items = items,
                contentType = { "item" },
                key = { "extension-${it.hashCode()}" },
            ) { item ->
                AudiobookExtensionItem(
                    item = item,
                    modifier = Modifier.animateItemPlacement(),
                    onClickItem = {
                        when (it) {
                            is AudiobookExtension.Available -> onInstallExtension(it)
                            is AudiobookExtension.Installed -> onOpenExtension(it)
                            is AudiobookExtension.Untrusted -> { trustState = it }
                        }
                    },
                    onLongClickItem = onLongClickItem,
                    onClickItemCancel = onClickItemCancel,
                    onClickItemWebView = onClickItemWebView,
                    onClickItemAction = {
                        when (it) {
                            is AudiobookExtension.Available -> onInstallExtension(it)
                            is AudiobookExtension.Installed -> {
                                if (it.hasUpdate) {
                                    onUpdateExtension(it)
                                } else {
                                    onOpenExtension(it)
                                }
                            }

                            is AudiobookExtension.Untrusted -> {
                                trustState = it
                            }
                        }
                    },
                )
            }
        }
    }
    if (trustState != null) {
        ExtensionTrustDialog(
            onClickConfirm = {
                onTrustExtension(trustState!!)
                trustState = null
            },
            onClickDismiss = {
                onUninstallExtension(trustState!!)
                trustState = null
            },
            onDismissRequest = {
                trustState = null
            },
        )
    }
}

@Composable
private fun AudiobookExtensionItem(
    item: AudiobookExtensionUiModel.Item,
    onClickItem: (AudiobookExtension) -> Unit,
    onLongClickItem: (AudiobookExtension) -> Unit,
    onClickItemWebView: (AudiobookExtension.Available) -> Unit,
    onClickItemCancel: (AudiobookExtension) -> Unit,
    onClickItemAction: (AudiobookExtension) -> Unit,
    modifier: Modifier = Modifier,
) {
    val (extension, installStep) = item
    BaseBrowseItem(
        modifier = modifier
            .combinedClickable(
                onClick = { onClickItem(extension) },
                onLongClick = { onLongClickItem(extension) },
            ),
        onClickItem = { onClickItem(extension) },
        onLongClickItem = { onLongClickItem(extension) },
        icon = {
            Box(
                modifier = Modifier
                    .size(40.dp),
                contentAlignment = Alignment.Center,
            ) {
                val idle = installStep.isCompleted()
                if (!idle) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(40.dp),
                        strokeWidth = 2.dp,
                    )
                }

                val padding by animateDpAsState(
                    targetValue = if (idle) 0.dp else 8.dp,
                    label = "iconPadding",
                )
                AudiobookExtensionIcon(
                    extension = extension,
                    modifier = Modifier
                        .matchParentSize()
                        .padding(padding),
                )
            }
        },
        action = {
            AudiobookExtensionItemActions(
                extension = extension,
                installStep = installStep,
                onClickItemWebView = onClickItemWebView,
                onClickItemCancel = onClickItemCancel,
                onClickItemAction = onClickItemAction,
            )
        },
    ) {
        AudiobookExtensionItemContent(
            extension = extension,
            installStep = installStep,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun AudiobookExtensionItemContent(
    extension: AudiobookExtension,
    installStep: InstallStep,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(start = MaterialTheme.padding.medium),
    ) {
        Text(
            text = extension.name,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
        )
        // Won't look good but it's not like we can ellipsize overflowing content
        FlowRow(
            modifier = Modifier.secondaryItemAlpha(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ProvideTextStyle(value = MaterialTheme.typography.bodySmall) {
                if (extension is AudiobookExtension.Installed && extension.lang.isNotEmpty()) {
                    Text(
                        text = LocaleHelper.getSourceDisplayName(
                            extension.lang,
                            LocalContext.current,
                        ),
                    )
                }

                if (extension.versionName.isNotEmpty()) {
                    Text(
                        text = extension.versionName,
                    )
                }

                val warning = when {
                    extension is AudiobookExtension.Untrusted -> MR.strings.ext_untrusted
                    extension is AudiobookExtension.Installed && extension.isUnofficial -> MR.strings.ext_unofficial
                    extension is AudiobookExtension.Installed && extension.isObsolete -> MR.strings.ext_obsolete
                    extension.isNsfw -> MR.strings.ext_nsfw_short
                    else -> null
                }
                if (warning != null) {
                    Text(
                        text = stringResource(warning).uppercase(),
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (!installStep.isCompleted()) {
                    DotSeparatorNoSpaceText()
                    Text(
                        text = when (installStep) {
                            InstallStep.Pending -> stringResource(MR.strings.ext_pending)
                            InstallStep.Downloading -> stringResource(MR.strings.ext_downloading)
                            InstallStep.Installing -> stringResource(MR.strings.ext_installing)
                            else -> error("Must not show non-install process text")
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AudiobookExtensionItemActions(
    extension: AudiobookExtension,
    installStep: InstallStep,
    modifier: Modifier = Modifier,
    onClickItemWebView: (AudiobookExtension.Available) -> Unit = {},
    onClickItemCancel: (AudiobookExtension) -> Unit = {},
    onClickItemAction: (AudiobookExtension) -> Unit = {},
) {
    val isIdle = installStep.isCompleted()

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when {
            !isIdle -> {
                IconButton(onClick = { onClickItemCancel(extension) }) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(MR.strings.action_cancel),
                    )
                }
            }
            installStep == InstallStep.Error -> {
                IconButton(onClick = { onClickItemAction(extension) }) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = stringResource(MR.strings.action_retry),
                    )
                }
            }
            installStep == InstallStep.Idle -> {
                when (extension) {
                    is AudiobookExtension.Installed -> {
                        if (extension.hasUpdate) {
                            IconButton(onClick = { onClickItemAction(extension) }) {
                                Icon(
                                    imageVector = Icons.Outlined.GetApp,
                                    contentDescription = stringResource(MR.strings.ext_update),
                                )
                            }
                        }

                        IconButton(onClick = { onClickItemAction(extension) }) {
                            Icon(
                                imageVector = Icons.Outlined.Settings,
                                contentDescription = stringResource(MR.strings.action_settings),
                            )
                        }
                    }
                    is AudiobookExtension.Untrusted -> {
                        IconButton(onClick = { onClickItemAction(extension) }) {
                            Icon(
                                imageVector = Icons.Outlined.VerifiedUser,
                                contentDescription = stringResource(MR.strings.ext_trust),
                            )
                        }
                    }
                    is AudiobookExtension.Available -> {
                        if (extension.sources.isNotEmpty()) {
                            IconButton(
                                onClick = { onClickItemWebView(extension) },
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Public,
                                    contentDescription = stringResource(MR.strings.action_open_in_web_view),
                                )
                            }
                        }

                        IconButton(onClick = { onClickItemAction(extension) }) {
                            Icon(
                                imageVector = Icons.Outlined.GetApp,
                                contentDescription = stringResource(MR.strings.ext_install),
                            )
                        }
                    }
                }
            }
        }
    }
}
