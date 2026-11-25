package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.PlaybackUiState
import com.example.data.model.RepeatMode
import com.example.data.model.Track
import com.example.ui.components.ArtworkImage
import com.example.ui.components.MaxmizeSlider
import com.example.ui.components.formatTimecode
import com.example.ui.theme.BadgeGoldBg
import com.example.ui.theme.BadgeGoldFg
import com.example.ui.theme.MonoBadgeStyle
import com.example.ui.theme.MonoTimecodeStyle
import kotlinx.coroutines.flow.distinctUntilChanged
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    playbackState: PlaybackUiState,
    isAutomixActive: Boolean = false,
    onCollapse: () -> Unit,
    onOpenDjMode: () -> Unit = {},
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSeekForward: () -> Unit,
    onSeekBackward: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSelectQueueIndex: (Int) -> Unit,
    onMoveQueueItem: (Int, Int) -> Unit,
    onRemoveFromQueue: (Int) -> Unit,
    onClearQueue: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleFavorite: (String) -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenLyrics: (Track) -> Unit,
    onStartDjMix: (Track) -> Unit = {},
    onOpenSleepTimer: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    onBalanceChange: (Float) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onEditMetadata: (Track) -> Unit,
    onTrackInfo: (Track) -> Unit,
) {
    var showQueueScreen by remember { mutableStateOf(false) }
    var showAudioTuningSheet by remember { mutableStateOf(false) }
    var moreMenuOpen by remember { mutableStateOf(false) }

    BackHandler(enabled = !showQueueScreen, onBack = onCollapse)

    val track = playbackState.currentTrack
    if (track == null) {
        onCollapse()
        return
    }

    val upNextTrack = remember(playbackState.queue, playbackState.currentIndex) {
        val nextIdx = playbackState.currentIndex + 1
        if (nextIdx in playbackState.queue.indices) playbackState.queue[nextIdx] else null
    }

    Box(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
                .navigationBarsPadding()
                .testTag("now_playing_screen"),
        ) {
            val isWideOrLandscape = maxWidth > maxHeight || maxWidth >= 600.dp

            Column(modifier = Modifier.fillMaxSize()) {
                // Minimal Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    IconButton(
                        onClick = onCollapse,
                        modifier = Modifier.testTag("now_playing_collapse_btn"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                            contentDescription = "Collapse player",
                            modifier = Modifier.size(30.dp),
                        )
                    }

                    if (isAutomixActive) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(100.dp))
                                .background(BadgeGoldBg)
                                .border(0.75.dp, BadgeGoldFg.copy(alpha = 0.65f), RoundedCornerShape(100.dp))
                                .clickable(onClick = onOpenDjMode)
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .testTag("now_playing_automix_pill"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.GraphicEq,
                                contentDescription = "Automix active",
                                tint = BadgeGoldFg,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = "AUTOMIX ACTIVE",
                                style = MonoBadgeStyle,
                                color = BadgeGoldFg,
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onOpenSleepTimer,
                            modifier = Modifier.testTag("now_playing_sleep_btn"),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Bedtime,
                                contentDescription = "Sleep timer",
                                tint = if (playbackState.sleepTimer.isActive) {
                                    MaterialTheme.colorScheme.primary
                                } else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(21.dp),
                            )
                        }
                        Box {
                            IconButton(
                                onClick = { moreMenuOpen = true },
                                modifier = Modifier.testTag("now_playing_more_btn"),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.MoreVert,
                                    contentDescription = stringResource(R.string.cd_more_options),
                                )
                            }
                            DropdownMenu(expanded = moreMenuOpen, onDismissRequest = { moreMenuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text(if (isAutomixActive) "Open DJ Mode" else "Mix in DJ Mode") },
                                    leadingIcon = { Icon(Icons.Rounded.GraphicEq, contentDescription = null) },
                                    onClick = {
                                        moreMenuOpen = false
                                        if (isAutomixActive) onOpenDjMode() else onStartDjMix(track)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Seek Back 10s") },
                                    leadingIcon = { Icon(Icons.Rounded.Replay10, contentDescription = null) },
                                    onClick = { moreMenuOpen = false; onSeekBackward() },
                                )
                                DropdownMenuItem(
                                    text = { Text("Seek Forward 10s") },
                                    leadingIcon = { Icon(Icons.Rounded.Forward10, contentDescription = null) },
                                    onClick = { moreMenuOpen = false; onSeekForward() },
                                )
                                DropdownMenuItem(
                                    text = { Text("Add to Playlist") },
                                    leadingIcon = { Icon(Icons.Rounded.PlaylistAdd, contentDescription = null) },
                                    onClick = { moreMenuOpen = false; onAddToPlaylist(track) },
                                )
                                DropdownMenuItem(
                                    text = { Text("Edit Metadata & Art") },
                                    leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                                    onClick = { moreMenuOpen = false; onEditMetadata(track) },
                                )
                                DropdownMenuItem(
                                    text = { Text("Speed & Balance") },
                                    leadingIcon = { Icon(Icons.Rounded.Tune, contentDescription = null) },
                                    onClick = { moreMenuOpen = false; showAudioTuningSheet = true },
                                )
                                DropdownMenuItem(
                                    text = { Text("Track Info") },
                                    leadingIcon = { Icon(Icons.Rounded.Info, contentDescription = null) },
                                    onClick = { moreMenuOpen = false; onTrackInfo(track) },
                                )
                            }
                        }
                    }
                }

                if (isWideOrLandscape) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(28.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            contentAlignment = Alignment.Center,
                        ) {
                            ArtworkPager(
                                queue = playbackState.queue,
                                currentIndex = playbackState.currentIndex,
                                onPageSelected = onSelectQueueIndex,
                                modifier = Modifier
                                    .widthIn(max = 320.dp)
                                    .aspectRatio(1f),
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1.1f),
                            verticalArrangement = Arrangement.Center,
                        ) {
                            PlayerControlsBlock(
                                track = track,
                                upNextTrack = upNextTrack,
                                playbackState = playbackState,
                                onToggleFavorite = { onToggleFavorite(track.id) },
                                onSeekTo = onSeekTo,
                                onToggleShuffle = onToggleShuffle,
                                onSkipPrevious = onSkipPrevious,
                                onTogglePlayPause = onTogglePlayPause,
                                onSkipNext = onSkipNext,
                                onCycleRepeat = onCycleRepeat,
                                onOpenEqualizer = onOpenEqualizer,
                                onOpenLyrics = { onOpenLyrics(track) },
                                onOpenQueue = { showQueueScreen = true },
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Spacer(modifier = Modifier.height(4.dp))
                        ArtworkPager(
                            queue = playbackState.queue,
                            currentIndex = playbackState.currentIndex,
                            onPageSelected = onSelectQueueIndex,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false)
                                .widthIn(max = 360.dp)
                                .aspectRatio(1f),
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        PlayerControlsBlock(
                            track = track,
                            upNextTrack = upNextTrack,
                            playbackState = playbackState,
                            onToggleFavorite = { onToggleFavorite(track.id) },
                            onSeekTo = onSeekTo,
                            onToggleShuffle = onToggleShuffle,
                            onSkipPrevious = onSkipPrevious,
                            onTogglePlayPause = onTogglePlayPause,
                            onSkipNext = onSkipNext,
                            onCycleRepeat = onCycleRepeat,
                            onOpenEqualizer = onOpenEqualizer,
                            onOpenLyrics = { onOpenLyrics(track) },
                            onOpenQueue = { showQueueScreen = true },
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        }

        // Dedicated Full-Screen Queue View (matches showcase Queue screen)
        AnimatedVisibility(
            visible = showQueueScreen,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        ) {
            QueueFullScreen(
                playbackState = playbackState,
                onBack = { showQueueScreen = false },
                onSelectQueueIndex = onSelectQueueIndex,
                onMoveQueueItem = onMoveQueueItem,
                onRemoveFromQueue = onRemoveFromQueue,
                onToggleShuffle = onToggleShuffle,
                onClearQueue = {
                    onClearQueue()
                    showQueueScreen = false
                },
            )
        }
    }

    if (showAudioTuningSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAudioTuningSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Playback Speed & Balance", style = MaterialTheme.typography.titleLarge)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Playback Speed", style = MaterialTheme.typography.titleSmall)
                    Text(
                        String.format(Locale.US, "%.2fx", playbackState.playbackSpeed),
                        style = MonoTimecodeStyle,
                    )
                }
                MaxmizeSlider(
                    value = playbackState.playbackSpeed,
                    onValueChange = onSpeedChange,
                    valueRange = 0.5f..2.0f,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Stereo Balance", style = MaterialTheme.typography.titleSmall)
                    val balLabel = when {
                        playbackState.audioBalance < -0.05f -> "L ${(-playbackState.audioBalance * 100).toInt()}%"
                        playbackState.audioBalance > 0.05f -> "R ${(playbackState.audioBalance * 100).toInt()}%"
                        else -> "Center"
                    }
                    Text(balLabel, style = MonoTimecodeStyle)
                }
                MaxmizeSlider(
                    value = playbackState.audioBalance,
                    onValueChange = onBalanceChange,
                    valueRange = -1.0f..1.0f,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = {
                        onSpeedChange(1.0f)
                        onBalanceChange(0.0f)
                    }) {
                        Text("Reset Defaults")
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtworkPager(
    queue: List<Track>,
    currentIndex: Int,
    onPageSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (queue.isEmpty()) return
    val safeIndex = currentIndex.coerceIn(0, queue.lastIndex)
    val pagerState = rememberPagerState(initialPage = safeIndex) { queue.size }

    LaunchedEffect(safeIndex) {
        if (pagerState.currentPage != safeIndex) {
            pagerState.animateScrollToPage(safeIndex)
        }
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { settled ->
                if (settled != safeIndex && settled in queue.indices) {
                    onPageSelected(settled)
                }
            }
    }

    HorizontalPager(
        state = pagerState,
        modifier = modifier,
    ) { page ->
        val pageTrack = queue[page]
        ArtworkImage(
            artworkUri = pageTrack.customArtworkUri ?: pageTrack.artworkUri,
            fallbackUri = pageTrack.contentUri,
            modifier = Modifier
                .fillMaxSize()
                .padding(2.dp),
            cornerRadius = 16.dp,
        )
    }
}

@Composable
private fun PlayerControlsBlock(
    track: Track,
    upNextTrack: Track?,
    playbackState: PlaybackUiState,
    onToggleFavorite: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onSkipPrevious: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onCycleRepeat: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenLyrics: () -> Unit,
    onOpenQueue: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Track title, artist, favorite
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("now_playing_favorite_btn"),
            ) {
                Icon(
                    imageVector = if (track.isFavorite) Icons.Rounded.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = stringResource(R.string.cd_favorite),
                    tint = if (track.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Seek bar (keyed by track.id so transitions reset any active scrub state)
        val duration = playbackState.durationMs.coerceAtLeast(1L)
        var isDragging by remember(track.id) { mutableStateOf(false) }
        var dragPos by remember(track.id) { mutableFloatStateOf(0f) }
        val sliderValue = if (isDragging) dragPos else playbackState.positionMs.toFloat().coerceIn(0f, duration.toFloat())

        MaxmizeSlider(
            value = sliderValue,
            onValueChange = {
                isDragging = true
                dragPos = it
            },
            onValueChangeFinished = {
                isDragging = false
                onSeekTo(dragPos.toLong())
            },
            valueRange = 0f..duration.toFloat(),
            modifier = Modifier.testTag("now_playing_seekbar"),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatTimecode(if (isDragging) dragPos.toLong() else playbackState.positionMs),
                style = MonoTimecodeStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = formatTimecode(playbackState.durationMs),
                style = MonoTimecodeStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Main transport controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onToggleShuffle,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("now_playing_shuffle_btn"),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Shuffle,
                    contentDescription = stringResource(R.string.cd_shuffle),
                    tint = if (playbackState.shuffleEnabled) {
                        MaterialTheme.colorScheme.primary
                    } else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            IconButton(
                onClick = onSkipPrevious,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("now_playing_prev_btn"),
            ) {
                Icon(
                    imageVector = Icons.Rounded.SkipPrevious,
                    contentDescription = stringResource(R.string.cd_previous_track),
                    modifier = Modifier.size(34.dp),
                )
            }

            Box(
                modifier = Modifier
                    .size(66.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(onClick = onTogglePlayPause)
                    .testTag("now_playing_play_pause_btn"),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (playbackState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = stringResource(R.string.cd_play_pause),
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(34.dp),
                )
            }

            IconButton(
                onClick = onSkipNext,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("now_playing_next_btn"),
            ) {
                Icon(
                    imageVector = Icons.Rounded.SkipNext,
                    contentDescription = stringResource(R.string.cd_next_track),
                    modifier = Modifier.size(34.dp),
                )
            }

            IconButton(
                onClick = onCycleRepeat,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("now_playing_repeat_btn"),
            ) {
                Icon(
                    imageVector = if (playbackState.repeatMode == RepeatMode.ONE) {
                        Icons.Rounded.RepeatOne
                    } else Icons.Rounded.Repeat,
                    contentDescription = stringResource(R.string.cd_repeat),
                    tint = if (playbackState.repeatMode != RepeatMode.OFF) {
                        MaterialTheme.colorScheme.primary
                    } else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Uncluttered 3-Pill Bottom Action Row: Queue, Lyrics, Audio
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            NowPlayingActionChip(
                icon = Icons.Rounded.QueueMusic,
                label = "Queue",
                onClick = onOpenQueue,
                modifier = Modifier
                    .weight(1f)
                    .testTag("now_playing_queue_btn"),
            )
            NowPlayingActionChip(
                icon = Icons.Rounded.Lyrics,
                label = "Lyrics",
                onClick = onOpenLyrics,
                modifier = Modifier
                    .weight(1f)
                    .testTag("now_playing_lyrics_btn"),
            )
            NowPlayingActionChip(
                icon = Icons.Rounded.Equalizer,
                label = "Audio",
                onClick = onOpenEqualizer,
                modifier = Modifier
                    .weight(1f)
                    .testTag("now_playing_eq_btn"),
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Up Next Dock Card
        val dockShape = RoundedCornerShape(14.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(dockShape)
                .background(MaterialTheme.colorScheme.surface)
                .border(0.75.dp, MaterialTheme.colorScheme.outlineVariant, dockShape)
                .clickable(onClick = onOpenQueue)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val previewTrack = upNextTrack ?: track
            ArtworkImage(
                artworkUri = previewTrack.customArtworkUri ?: previewTrack.artworkUri,
                fallbackUri = previewTrack.contentUri,
                modifier = Modifier.size(38.dp),
                cornerRadius = 8.dp,
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (upNextTrack != null) "Up Next" else "Queue (${playbackState.queue.size})",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = if (upNextTrack != null) {
                        "${upNextTrack.title} · ${upNextTrack.artist}"
                    } else {
                        "End of current queue"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowUp,
                    contentDescription = "Open queue",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun NowPlayingActionChip(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .height(42.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(0.75.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(17.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

@Composable
private fun QueueFullScreen(
    playbackState: PlaybackUiState,
    onBack: () -> Unit,
    onSelectQueueIndex: (Int) -> Unit,
    onMoveQueueItem: (Int, Int) -> Unit,
    onRemoveFromQueue: (Int) -> Unit,
    onToggleShuffle: () -> Unit,
    onClearQueue: () -> Unit,
) {
    BackHandler(onBack = onBack)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("queue_screen"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                    contentDescription = "Back to player",
                    modifier = Modifier.size(30.dp),
                )
            }
            Text(
                text = "Queue",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            )
            IconButton(
                onClick = onClearQueue,
                modifier = Modifier.testTag("clear_queue_btn"),
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = "Clear queue",
                )
            }
        }

        Text(
            text = "Up Next (${playbackState.queue.size})",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(playbackState.queue, key = { idx, t -> "${t.id}_$idx" }) { index, qTrack ->
                val isCurrent = index == playbackState.currentIndex
                val rowShape = RoundedCornerShape(12.dp)
                var rowMenuOpen by remember { mutableStateOf(false) }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(rowShape)
                        .background(
                            if (isCurrent) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.background
                        )
                        .let {
                            if (isCurrent) it.border(0.75.dp, MaterialTheme.colorScheme.outlineVariant, rowShape)
                            else it
                        }
                        .clickable { onSelectQueueIndex(index) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ArtworkImage(
                        artworkUri = qTrack.customArtworkUri ?: qTrack.artworkUri,
                        fallbackUri = qTrack.contentUri,
                        modifier = Modifier.size(48.dp),
                        cornerRadius = 8.dp,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = qTrack.title,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                            ),
                            color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = qTrack.artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (isCurrent) {
                        Icon(
                            imageVector = Icons.Rounded.Equalizer,
                            contentDescription = "Playing",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 4.dp).size(20.dp),
                        )
                    }
                    Box {
                        IconButton(
                            onClick = { rowMenuOpen = true },
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = "Queue track options",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        DropdownMenu(expanded = rowMenuOpen, onDismissRequest = { rowMenuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Move Up") },
                                leadingIcon = { Icon(Icons.Rounded.ArrowUpward, contentDescription = null) },
                                enabled = index > 0,
                                onClick = { rowMenuOpen = false; onMoveQueueItem(index, index - 1) },
                            )
                            DropdownMenuItem(
                                text = { Text("Move Down") },
                                leadingIcon = { Icon(Icons.Rounded.ArrowDownward, contentDescription = null) },
                                enabled = index < playbackState.queue.lastIndex,
                                onClick = { rowMenuOpen = false; onMoveQueueItem(index, index + 1) },
                            )
                            DropdownMenuItem(
                                text = { Text("Remove from Queue") },
                                leadingIcon = { Icon(Icons.Rounded.Close, contentDescription = null) },
                                onClick = { rowMenuOpen = false; onRemoveFromQueue(index) },
                            )
                        }
                    }
                }
            }
        }

        HorizontalDivider(thickness = 0.75.dp, color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val btnShape = RoundedCornerShape(12.dp)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
                    .clip(btnShape)
                    .background(
                        if (playbackState.shuffleEnabled) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surface
                    )
                    .border(
                        width = 0.75.dp,
                        color = if (playbackState.shuffleEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        shape = btnShape,
                    )
                    .clickable(onClick = onToggleShuffle),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Shuffle,
                    contentDescription = null,
                    tint = if (playbackState.shuffleEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Shuffle",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (playbackState.shuffleEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
            }

            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
                    .clip(btnShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(0.75.dp, MaterialTheme.colorScheme.outlineVariant, btnShape)
                    .clickable(onClick = onClearQueue),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Clear Queue",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
