package com.example.ui.lyrics

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.LyricLine
import com.example.data.model.LyricsDocument
import com.example.data.model.LyricsSearchCandidate
import com.example.data.model.Track
import com.example.ui.components.ArtworkImage
import com.example.ui.components.MaxmizeIcons
import com.example.ui.components.formatTimecode
import kotlinx.coroutines.flow.StateFlow

@Composable
fun LyricsScreen(
    viewModel: LyricsViewModel,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DisposableEffect(viewModel) {
        viewModel.setLyricsActive(true)
        onDispose { viewModel.setLyricsActive(false) }
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val searchState by viewModel.searchState.collectAsStateWithLifecycle()
    val fontSizeSp by viewModel.fontSizeSp.collectAsStateWithLifecycle()

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            viewModel.importLyricsUri(uri)
        }
    }

    LyricsScreenContent(
        uiState = uiState,
        searchState = searchState,
        fontSizeSp = fontSizeSp,
        onDismiss = onDismiss,
        onRetry = viewModel::retry,
        onRefresh = viewModel::refresh,
        onTogglePlayPause = viewModel.playbackController::togglePlayPause,
        onSeekTo = viewModel.playbackController::seekTo,
        onSkipPrevious = viewModel.playbackController::skipPrevious,
        onSkipNext = viewModel.playbackController::skipNext,
        onLineTapped = viewModel::onLyricLineTapped,
        onUserScrolled = viewModel::onUserScrolledManually,
        onResumeAutoFollow = viewModel::resumeAutoFollow,
        onSearchOnline = viewModel::searchOnline,
        onApplyCandidate = viewModel::applyCandidate,
        onAdjustSyncOffset = viewModel::adjustSyncOffset,
        onResetSyncOffset = viewModel::resetSyncOffset,
        onSaveManualLyrics = viewModel::saveManualLyrics,
        onSaveSyncedLines = viewModel::saveSyncedLines,
        onImportFileClick = {
            importLauncher.launch(arrayOf("text/*", "application/octet-stream", "*/*"))
        },
        onClearManualLyrics = viewModel::clearManualLyrics,
        onFontSizeChange = viewModel::setFontSize,
        modifier = modifier,
        positionMsFlow = viewModel.playbackPositionMs,
    )
}

@Composable
fun LyricsScreenContent(
    uiState: LyricsScreenUiState,
    searchState: LyricsSearchUiState,
    fontSizeSp: Float,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onLineTapped: (LyricLine) -> Unit,
    onUserScrolled: () -> Unit,
    onResumeAutoFollow: () -> Unit,
    onSearchOnline: (String?) -> Unit,
    onApplyCandidate: (LyricsSearchCandidate) -> Unit,
    onAdjustSyncOffset: (Long) -> Unit,
    onResetSyncOffset: () -> Unit,
    onSaveManualLyrics: (String) -> Unit,
    onSaveSyncedLines: (List<LyricLine>) -> Unit,
    onImportFileClick: () -> Unit,
    onClearManualLyrics: () -> Unit,
    onFontSizeChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    positionMsFlow: StateFlow<Long>? = null,
) {
    BackHandler(onBack = onDismiss)

    var showMoreMenu by remember { mutableStateOf(false) }
    var showSearchDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showSyncDialog by remember { mutableStateOf(false) }
    var showFontDialog by remember { mutableStateOf(false) }

    val activeTrack: Track? = when (uiState) {
        LyricsScreenUiState.Idle -> null
        is LyricsScreenUiState.Loading -> uiState.track
        is LyricsScreenUiState.Content -> uiState.track
        is LyricsScreenUiState.Empty -> uiState.track
        is LyricsScreenUiState.Error -> uiState.track
    }
    val activeDocument: LyricsDocument? = (uiState as? LyricsScreenUiState.Content)?.document
    val positionMs: Long = (uiState as? LyricsScreenUiState.Content)?.playbackPositionMs ?: 0L
    val durationMs: Long = (uiState as? LyricsScreenUiState.Content)?.durationMs
        ?: activeTrack?.durationMs ?: 0L
    val isPlaying: Boolean = (uiState as? LyricsScreenUiState.Content)?.isPlaying ?: false

    Surface(
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        modifier = modifier
            .fillMaxSize()
            .testTag("lyrics_screen"),
    ) {
        Box(
            contentAlignment = Alignment.TopCenter,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 720.dp),
            ) {
                if (activeTrack != null) {
                    LyricsTopHeader(
                        track = activeTrack,
                        document = activeDocument,
                        showMoreMenu = showMoreMenu,
                        onShowMoreMenu = { showMoreMenu = it },
                        onDismiss = onDismiss,
                        onOpenSearch = {
                            showSearchDialog = true
                            onSearchOnline(null)
                        },
                        onRefresh = onRefresh,
                        onOpenEdit = { showEditDialog = true },
                        onOpenSyncTools = { showSyncDialog = true },
                        onOpenFontSize = { showFontDialog = true },
                        onImportFile = onImportFileClick,
                        onClearManual = onClearManualLyrics,
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    when (uiState) {
                        LyricsScreenUiState.Idle -> {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                Text(
                                    text = "Start playing a track to view lyrics",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        is LyricsScreenUiState.Loading -> {
                            LyricsLoadingState(track = uiState.track)
                        }
                        is LyricsScreenUiState.Error -> {
                            LyricsErrorState(
                                message = uiState.message,
                                isOffline = uiState.isOffline,
                                onRetry = onRetry,
                                onSearchOnline = {
                                    showSearchDialog = true
                                    onSearchOnline(null)
                                },
                                onEditManual = { showEditDialog = true },
                            )
                        }
                        is LyricsScreenUiState.Empty -> {
                            LyricsEmptyState(
                                reason = uiState.reason,
                                onSearchOnline = {
                                    showSearchDialog = true
                                    onSearchOnline(null)
                                },
                                onEditManual = { showEditDialog = true },
                                onImportFile = onImportFileClick,
                            )
                        }
                        is LyricsScreenUiState.Content -> {
                            if (uiState.document.isSynced) {
                                SyncedLyricsView(
                                    document = uiState.document,
                                    activeLineIndex = uiState.activeLineIndex,
                                    activeWordIndex = uiState.activeWordIndex,
                                    fontSizeSp = fontSizeSp,
                                    isAutoFollowEnabled = uiState.isAutoFollowEnabled,
                                    onLineTapped = onLineTapped,
                                    onUserScrolled = onUserScrolled,
                                    onResumeAutoFollow = onResumeAutoFollow,
                                )
                            } else {
                                UnsyncedLyricsView(
                                    document = uiState.document,
                                    fontSizeSp = fontSizeSp,
                                    onOpenSyncTools = { showSyncDialog = true },
                                )
                            }
                        }
                    }
                }

                if (activeTrack != null) {
                    LyricsPlaybackBar(
                        fallbackPositionMs = positionMs,
                        positionMsFlow = positionMsFlow,
                        durationMs = durationMs,
                        isPlaying = isPlaying,
                        onSeekTo = onSeekTo,
                        onTogglePlayPause = onTogglePlayPause,
                        onSkipPrevious = onSkipPrevious,
                        onSkipNext = onSkipNext,
                    )
                }
            }

            if (showSearchDialog && activeTrack != null) {
                LyricsSearchDialog(
                    track = activeTrack,
                    searchState = searchState,
                    onSearch = onSearchOnline,
                    onSelectCandidate = onApplyCandidate,
                    onDismiss = { showSearchDialog = false },
                )
            }

            if (showEditDialog && activeTrack != null) {
                LyricsEditDialog(
                    initialRawText = activeDocument?.rawText.orEmpty(),
                    hasCustomOrSaved = activeDocument != null,
                    onSave = onSaveManualLyrics,
                    onClearCustom = onClearManualLyrics,
                    onDismiss = { showEditDialog = false },
                )
            }

            if (showSyncDialog && activeTrack != null) {
                LyricsSyncToolsDialog(
                    document = activeDocument,
                    playbackPositionMs = positionMs,
                    onAdjustOffset = onAdjustSyncOffset,
                    onResetOffset = onResetSyncOffset,
                    onSaveStampedLines = onSaveSyncedLines,
                    onDismiss = { showSyncDialog = false },
                    positionMsFlow = positionMsFlow,
                )
            }

            if (showFontDialog) {
                LyricsFontSizeDialog(
                    currentSizeSp = fontSizeSp,
                    onChangeFontSize = onFontSizeChange,
                    onDismiss = { showFontDialog = false },
                )
            }
        }
    }
}

@Composable
private fun LyricsTopHeader(
    track: Track,
    document: LyricsDocument?,
    showMoreMenu: Boolean,
    onShowMoreMenu: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onOpenSearch: () -> Unit,
    onRefresh: () -> Unit,
    onOpenEdit: () -> Unit,
    onOpenSyncTools: () -> Unit,
    onOpenFontSize: () -> Unit,
    onImportFile: () -> Unit,
    onClearManual: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("lyrics_close_btn"),
            ) {
                Icon(
                    imageVector = MaxmizeIcons.ChevronDown,
                    contentDescription = "Close lyrics",
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            ArtworkImage(
                artworkUri = track.displayArtwork,
                fallbackUri = track.contentUri,
                contentDescription = "${track.title} artwork",
                cornerRadius = 8.dp,
                maxSizePx = 180,
                modifier = Modifier.size(44.dp),
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitle = buildString {
                    append(track.artist)
                    if (document != null) {
                        append(" · ")
                        append(document.source.badgeLabel)
                        if (document.isSynced) append(" · Synced")
                    }
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            IconButton(
                onClick = onOpenSearch,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("lyrics_search_btn"),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = "Search lyrics online",
                )
            }

            IconButton(
                onClick = onRefresh,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("lyrics_refresh_btn"),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = "Refresh lyrics",
                )
            }

            Box {
                IconButton(
                    onClick = { onShowMoreMenu(true) },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("lyrics_more_btn"),
                ) {
                    Icon(
                        imageVector = MaxmizeIcons.More,
                        contentDescription = "More lyrics tools",
                    )
                }
                DropdownMenu(
                    expanded = showMoreMenu,
                    onDismissRequest = { onShowMoreMenu(false) },
                ) {
                    DropdownMenuItem(
                        text = { Text("Timing & Sync") },
                        leadingIcon = { Icon(Icons.Rounded.Tune, contentDescription = null) },
                        onClick = {
                            onShowMoreMenu(false)
                            onOpenSyncTools()
                        },
                        modifier = Modifier.testTag("lyrics_menu_sync"),
                    )
                    DropdownMenuItem(
                        text = { Text("Edit / Paste Lyrics") },
                        leadingIcon = { Icon(Icons.Rounded.EditNote, contentDescription = null) },
                        onClick = {
                            onShowMoreMenu(false)
                            onOpenEdit()
                        },
                        modifier = Modifier.testTag("lyrics_menu_edit"),
                    )
                    DropdownMenuItem(
                        text = { Text("Import .lrc File") },
                        leadingIcon = { Icon(Icons.Rounded.FolderOpen, contentDescription = null) },
                        onClick = {
                            onShowMoreMenu(false)
                            onImportFile()
                        },
                        modifier = Modifier.testTag("lyrics_menu_import"),
                    )
                    DropdownMenuItem(
                        text = { Text("Text Size") },
                        leadingIcon = { Icon(Icons.Rounded.FormatSize, contentDescription = null) },
                        onClick = {
                            onShowMoreMenu(false)
                            onOpenFontSize()
                        },
                    )
                    if (document != null) {
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Clear Saved Lyrics") },
                            leadingIcon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null) },
                            onClick = {
                                onShowMoreMenu(false)
                                onClearManual()
                            },
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }
}

@Composable
private fun LyricsPlaybackBar(
    fallbackPositionMs: Long,
    positionMsFlow: StateFlow<Long>?,
    durationMs: Long,
    isPlaying: Boolean,
    onSeekTo: (Long) -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
) {
    val positionMs = if (positionMsFlow != null) {
        positionMsFlow.collectAsStateWithLifecycle().value
    } else {
        fallbackPositionMs
    }
    val safeDuration = durationMs.coerceAtLeast(1L)
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(0f) }
    val liveFraction = (positionMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
    val displayedFraction = if (isScrubbing) scrubFraction else liveFraction
    val displayedPositionMs = if (isScrubbing) {
        (scrubFraction * safeDuration).toLong()
    } else {
        positionMs
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        Spacer(modifier = Modifier.height(8.dp))

        Slider(
            value = displayedFraction,
            onValueChange = {
                isScrubbing = true
                scrubFraction = it
            },
            onValueChangeFinished = {
                onSeekTo((scrubFraction * safeDuration).toLong())
                isScrubbing = false
            },
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp),
        )

        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = formatTimecode(displayedPositionMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = formatTimecode(durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 4.dp),
        ) {
            IconButton(
                onClick = onSkipPrevious,
                modifier = Modifier.minimumInteractiveComponentSize(),
            ) {
                Icon(
                    imageVector = MaxmizeIcons.Previous,
                    contentDescription = "Previous track",
                    modifier = Modifier.size(26.dp),
                )
            }
            Spacer(modifier = Modifier.width(20.dp))
            Surface(
                onClick = onTogglePlayPause,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .size(54.dp)
                    .minimumInteractiveComponentSize()
                    .testTag("lyrics_play_pause_btn"),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isPlaying) MaxmizeIcons.Pause else MaxmizeIcons.Play,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.width(20.dp))
            IconButton(
                onClick = onSkipNext,
                modifier = Modifier.minimumInteractiveComponentSize(),
            ) {
                Icon(
                    imageVector = MaxmizeIcons.Next,
                    contentDescription = "Next track",
                    modifier = Modifier.size(26.dp),
                )
            }
        }
    }
}
