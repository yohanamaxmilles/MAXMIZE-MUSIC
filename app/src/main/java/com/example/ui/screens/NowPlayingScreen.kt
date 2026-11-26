package com.example.ui.screens

import androidx.activity.compose.BackHandler
import com.example.ui.components.CoverAmbientStyle
import com.example.ui.components.MaxmizeIcons
import com.example.ui.components.extractCoverAmbientStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.Crossfade
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.PlaybackUiState
import com.example.data.model.RepeatMode
import com.example.data.model.Track
import com.example.ui.components.ArtworkImage
import com.example.ui.components.MaxmizeSlider
import com.example.ui.components.formatTimecode
import com.example.ui.theme.LavenderCard
import com.example.ui.theme.LavenderCardDeep
import com.example.ui.theme.MonoBadgeStyle
import com.example.ui.theme.MonoTimecodeStyle
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    playbackState: PlaybackUiState,
    positionMsFlow: StateFlow<Long>? = null,
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
                .background(NowPlayingBg)
                .testTag("now_playing_screen"),
        ) {
            val isWideOrLandscape = maxWidth > maxHeight || maxWidth >= 600.dp
            val compact = maxHeight < 700.dp

            // Blurred, colour-blended artwork that fills the whole screen (updates with the track)
            NowPlayingBackdrop(track = track)

            if (isWideOrLandscape) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding(),
                ) {
                    NowPlayingTopBar(
                        track = track,
                        isAutomixActive = isAutomixActive,
                        sleepTimerActive = playbackState.sleepTimer.isActive,
                        moreMenuOpen = moreMenuOpen,
                        onMoreMenuOpenChange = { moreMenuOpen = it },
                        onCollapse = onCollapse,
                        onOpenDjMode = onOpenDjMode,
                        onOpenSleepTimer = onOpenSleepTimer,
                        onStartDjMix = onStartDjMix,
                        onSeekBackward = onSeekBackward,
                        onSeekForward = onSeekForward,
                        onAddToPlaylist = onAddToPlaylist,
                        onEditMetadata = onEditMetadata,
                        onShowAudioTuning = { showAudioTuningSheet = true },
                        onTrackInfo = onTrackInfo,
                    )
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
                                cornerRadius = 28.dp,
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
                                positionMsFlow = positionMsFlow,
                                compact = true,
                                showUpNextCard = false,
                                onToggleFavorite = { onToggleFavorite(track.id) },
                                onSeekTo = onSeekTo,
                                onToggleShuffle = onToggleShuffle,
                                onSkipPrevious = onSkipPrevious,
                                onTogglePlayPause = onTogglePlayPause,
                                onSkipNext = onSkipNext,
                                onCycleRepeat = onCycleRepeat,
                                onOpenLyrics = { onOpenLyrics(track) },
                                onOpenQueue = { showQueueScreen = true },
                                onSelectQueueIndex = onSelectQueueIndex,
                            )
                        }
                    }
                }
            } else {
                // Sharp full-bleed artwork hero covering the upper part of the screen
                val heroHeight = maxHeight * 0.58f
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(heroHeight),
                ) {
                    ArtworkPager(
                        queue = playbackState.queue,
                        currentIndex = playbackState.currentIndex,
                        onPageSelected = onSelectQueueIndex,
                        cornerRadius = 0.dp,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    0f to Color.Black.copy(alpha = 0.28f),
                                    0.16f to Color.Transparent,
                                    0.60f to Color.Transparent,
                                    0.84f to NowPlayingBg.copy(alpha = 0.72f),
                                    1f to NowPlayingBg,
                                ),
                            ),
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding(),
                ) {
                    NowPlayingTopBar(
                        track = track,
                        isAutomixActive = isAutomixActive,
                        sleepTimerActive = playbackState.sleepTimer.isActive,
                        moreMenuOpen = moreMenuOpen,
                        onMoreMenuOpenChange = { moreMenuOpen = it },
                        onCollapse = onCollapse,
                        onOpenDjMode = onOpenDjMode,
                        onOpenSleepTimer = onOpenSleepTimer,
                        onStartDjMix = onStartDjMix,
                        onSeekBackward = onSeekBackward,
                        onSeekForward = onSeekForward,
                        onAddToPlaylist = onAddToPlaylist,
                        onEditMetadata = onEditMetadata,
                        onShowAudioTuning = { showAudioTuningSheet = true },
                        onTrackInfo = onTrackInfo,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    PlayerControlsBlock(
                        track = track,
                        upNextTrack = upNextTrack,
                        playbackState = playbackState,
                        positionMsFlow = positionMsFlow,
                        compact = compact,
                        showUpNextCard = true,
                        onToggleFavorite = { onToggleFavorite(track.id) },
                        onSeekTo = onSeekTo,
                        onToggleShuffle = onToggleShuffle,
                        onSkipPrevious = onSkipPrevious,
                        onTogglePlayPause = onTogglePlayPause,
                        onSkipNext = onSkipNext,
                        onCycleRepeat = onCycleRepeat,
                        onOpenLyrics = { onOpenLyrics(track) },
                        onOpenQueue = { showQueueScreen = true },
                        onSelectQueueIndex = onSelectQueueIndex,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }

        // Dedicated Full-Screen Queue View (matches showcase Queue screen)
        AnimatedVisibility(
            visible = showQueueScreen,
            enter = slideInVertically(tween(260, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(180)),
            exit = slideOutVertically(tween(220, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(160)),
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

private val NowPlayingBg = Color(0xFF09090D)

@Composable
private fun NowPlayingBackdrop(track: Track) {
    val context = LocalContext.current
    val ambient by androidx.compose.runtime.produceState(
        initialValue = CoverAmbientStyle(),
        track.id,
        track.displayArtwork,
    ) {
        value = extractCoverAmbientStyle(
            context = context,
            artworkUri = track.displayArtwork,
            fallbackUri = track.contentUri,
            seedKey = track.id,
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to ambient.primaryColor.copy(alpha = 0.72f),
                    0.5f to ambient.secondaryColor.copy(alpha = 0.52f),
                    1f to NowPlayingBg,
                ),
            ),
    ) {
        val blurredBmp = ambient.blurredBitmap
        if (blurredBmp != null) {
            Image(
                bitmap = blurredBmp,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = 0.42f,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to NowPlayingBg.copy(alpha = 0.35f),
                        0.55f to NowPlayingBg.copy(alpha = 0.78f),
                        1f to NowPlayingBg,
                    ),
                ),
        )
    }
}

@Composable
private fun ArtworkPager(
    queue: List<Track>,
    currentIndex: Int,
    onPageSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 0.dp,
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
        key = { page -> queue.getOrNull(page)?.id ?: page },
        modifier = modifier,
    ) { page ->
        val pageTrack = queue[page]
        ArtworkImage(
            artworkUri = pageTrack.customArtworkUri ?: pageTrack.artworkUri,
            fallbackUri = pageTrack.contentUri,
            maxSizePx = 800,
            modifier = Modifier.fillMaxSize(),
            cornerRadius = cornerRadius,
        )
    }
}

@Composable
private fun TopBarCircleButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.26f)),
    ) { content() }
}

@Composable
private fun NowPlayingTopBar(
    track: Track,
    isAutomixActive: Boolean,
    sleepTimerActive: Boolean,
    moreMenuOpen: Boolean,
    onMoreMenuOpenChange: (Boolean) -> Unit,
    onCollapse: () -> Unit,
    onOpenDjMode: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    onStartDjMix: (Track) -> Unit,
    onSeekBackward: () -> Unit,
    onSeekForward: () -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onEditMetadata: (Track) -> Unit,
    onShowAudioTuning: () -> Unit,
    onTrackInfo: (Track) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        TopBarCircleButton(
            onClick = onCollapse,
            modifier = Modifier.testTag("now_playing_collapse_btn"),
        ) {
            Icon(
                imageVector = MaxmizeIcons.ChevronDown,
                contentDescription = "Collapse player",
                tint = Color.White,
                modifier = Modifier.size(24.dp),
            )
        }

        if (isAutomixActive) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(100.dp))
                    .background(LavenderCardDeep.copy(alpha = 0.85f))
                    .border(1.dp, LavenderCard.copy(alpha = 0.55f), RoundedCornerShape(100.dp))
                    .clickable(onClick = onOpenDjMode)
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .testTag("now_playing_automix_pill"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = MaxmizeIcons.Audio,
                    contentDescription = "Automix active",
                    tint = LavenderCard,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = "Automix on",
                    style = MaterialTheme.typography.labelMedium,
                    color = LavenderCard,
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TopBarCircleButton(
                onClick = onOpenSleepTimer,
                modifier = Modifier.testTag("now_playing_sleep_btn"),
            ) {
                Icon(
                    imageVector = MaxmizeIcons.Moon,
                    contentDescription = "Sleep timer",
                    tint = if (sleepTimerActive) MaterialTheme.colorScheme.primary else Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
            Box {
                TopBarCircleButton(
                    onClick = { onMoreMenuOpenChange(true) },
                    modifier = Modifier.testTag("now_playing_more_btn"),
                ) {
                    Icon(
                        imageVector = MaxmizeIcons.More,
                        contentDescription = stringResource(R.string.cd_more_options),
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
                DropdownMenu(expanded = moreMenuOpen, onDismissRequest = { onMoreMenuOpenChange(false) }) {
                    DropdownMenuItem(
                        text = { Text(if (isAutomixActive) "Open DJ Mode" else "Mix in DJ Mode") },
                        leadingIcon = { Icon(Icons.Rounded.GraphicEq, contentDescription = null) },
                        onClick = {
                            onMoreMenuOpenChange(false)
                            if (isAutomixActive) onOpenDjMode() else onStartDjMix(track)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Seek Back 10s") },
                        leadingIcon = { Icon(Icons.Rounded.Replay10, contentDescription = null) },
                        onClick = { onMoreMenuOpenChange(false); onSeekBackward() },
                    )
                    DropdownMenuItem(
                        text = { Text("Seek Forward 10s") },
                        leadingIcon = { Icon(Icons.Rounded.Forward10, contentDescription = null) },
                        onClick = { onMoreMenuOpenChange(false); onSeekForward() },
                    )
                    DropdownMenuItem(
                        text = { Text("Add to Playlist") },
                        leadingIcon = { Icon(Icons.Rounded.PlaylistAdd, contentDescription = null) },
                        onClick = { onMoreMenuOpenChange(false); onAddToPlaylist(track) },
                    )
                    DropdownMenuItem(
                        text = { Text("Edit Metadata & Art") },
                        leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                        onClick = { onMoreMenuOpenChange(false); onEditMetadata(track) },
                    )
                    DropdownMenuItem(
                        text = { Text("Speed & Balance") },
                        leadingIcon = { Icon(Icons.Rounded.Tune, contentDescription = null) },
                        onClick = { onMoreMenuOpenChange(false); onShowAudioTuning() },
                    )
                    DropdownMenuItem(
                        text = { Text("Track Info") },
                        leadingIcon = { Icon(Icons.Rounded.Info, contentDescription = null) },
                        onClick = { onMoreMenuOpenChange(false); onTrackInfo(track) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TransportSideButton(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    circleBackground: Boolean = false,
    iconSize: Dp = 22.dp,
    buttonSize: Dp = 48.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.90f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 500f),
        label = "transportScale",
    )
    Box(
        modifier = modifier
            .size(buttonSize)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .then(if (circleBackground) Modifier.background(Color.White.copy(alpha = 0.09f)) else Modifier)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
private fun PlayerControlsBlock(
    track: Track,
    upNextTrack: Track?,
    playbackState: PlaybackUiState,
    positionMsFlow: StateFlow<Long>?,
    onToggleFavorite: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onSkipPrevious: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onCycleRepeat: () -> Unit,
    onOpenLyrics: () -> Unit,
    onOpenQueue: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    showUpNextCard: Boolean = true,
    onSelectQueueIndex: (Int) -> Unit = {},
) {
    val accent = MaterialTheme.colorScheme.primary
    val softWhite = Color.White.copy(alpha = 0.88f)

    Column(modifier = modifier.fillMaxWidth()) {
        // Track title, artist, favorite
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = if (compact) 24.sp else 28.sp,
                        lineHeight = if (compact) 30.sp else 34.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = if (compact) 16.sp else 18.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    color = Color.White.copy(alpha = 0.68f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier
                    .size(52.dp)
                    .testTag("now_playing_favorite_btn"),
            ) {
                Icon(
                    imageVector = if (track.isFavorite) MaxmizeIcons.HeartFilled else MaxmizeIcons.Heart,
                    contentDescription = stringResource(R.string.cd_favorite),
                    tint = if (track.isFavorite) accent else Color.White,
                    modifier = Modifier.size(28.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(if (compact) 4.dp else 10.dp))

        // Seek bar (isolated leaf composable so time ticks never recompose artwork/controls)
        NowPlayingSeekBarSection(
            trackId = track.id,
            positionMsFlow = positionMsFlow,
            fallbackPositionMs = playbackState.positionMs,
            durationMs = playbackState.durationMs,
            onSeekTo = onSeekTo,
        )

        Spacer(modifier = Modifier.height(if (compact) 8.dp else 14.dp))

        // Main transport controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TransportSideButton(
                icon = MaxmizeIcons.Shuffle,
                contentDescription = stringResource(R.string.cd_shuffle),
                tint = if (playbackState.shuffleEnabled) accent else softWhite,
                onClick = onToggleShuffle,
                circleBackground = true,
                modifier = Modifier.testTag("now_playing_shuffle_btn"),
            )

            TransportSideButton(
                icon = MaxmizeIcons.Previous,
                contentDescription = stringResource(R.string.cd_previous_track),
                tint = Color.White,
                onClick = onSkipPrevious,
                iconSize = 32.dp,
                buttonSize = 56.dp,
                modifier = Modifier.testTag("now_playing_prev_btn"),
            )

            // Large neon play/pause with soft glow and press animation
            val playInteraction = remember { MutableInteractionSource() }
            val playPressed by playInteraction.collectIsPressedAsState()
            val playScale by animateFloatAsState(
                targetValue = if (playPressed) 0.92f else 1f,
                animationSpec = spring(dampingRatio = 0.5f, stiffness = 420f),
                label = "playScale",
            )
            Box(
                modifier = Modifier.size(76.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .requiredSize(124.dp)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(accent.copy(alpha = 0.30f), Color.Transparent),
                            ),
                            CircleShape,
                        ),
                )
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .graphicsLayer { scaleX = playScale; scaleY = playScale }
                        .clip(CircleShape)
                        .background(accent)
                        .clickable(
                            interactionSource = playInteraction,
                            indication = null,
                            onClick = onTogglePlayPause,
                        )
                        .testTag("now_playing_play_pause_btn"),
                    contentAlignment = Alignment.Center,
                ) {
                    Crossfade(
                        targetState = playbackState.isPlaying,
                        animationSpec = tween(160),
                        label = "playPauseIcon",
                    ) { playing ->
                        Icon(
                            imageVector = if (playing) MaxmizeIcons.Pause else MaxmizeIcons.Play,
                            contentDescription = stringResource(R.string.cd_play_pause),
                            tint = Color(0xFF0A0A0F),
                            modifier = Modifier.size(34.dp),
                        )
                    }
                }
            }

            TransportSideButton(
                icon = MaxmizeIcons.Next,
                contentDescription = stringResource(R.string.cd_next_track),
                tint = Color.White,
                onClick = onSkipNext,
                iconSize = 32.dp,
                buttonSize = 56.dp,
                modifier = Modifier.testTag("now_playing_next_btn"),
            )

            TransportSideButton(
                icon = if (playbackState.repeatMode == RepeatMode.ONE) MaxmizeIcons.RepeatOne else MaxmizeIcons.Repeat,
                contentDescription = stringResource(R.string.cd_repeat),
                tint = if (playbackState.repeatMode != RepeatMode.OFF) accent else softWhite,
                onClick = onCycleRepeat,
                circleBackground = true,
                modifier = Modifier.testTag("now_playing_repeat_btn"),
            )
        }

        Spacer(modifier = Modifier.height(if (compact) 10.dp else 18.dp))

        // Lyrics pill only (Queue lives in Up Next below; EQ lives in Settings -> Audio)
        NowPlayingActionChip(
            icon = MaxmizeIcons.Lyrics,
            label = "Lyrics",
            onClick = onOpenLyrics,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("now_playing_lyrics_btn"),
        )

        if (showUpNextCard) {
            Spacer(modifier = Modifier.height(if (compact) 10.dp else 12.dp))
            UpNextGlassCard(
                track = track,
                upNextTrack = upNextTrack,
                playbackState = playbackState,
                onOpenQueue = onOpenQueue,
                onSelectQueueIndex = onSelectQueueIndex,
            )
        }
    }
}

@Composable
private fun NowPlayingSeekBarSection(
    trackId: String,
    positionMsFlow: StateFlow<Long>?,
    fallbackPositionMs: Long,
    durationMs: Long,
    onSeekTo: (Long) -> Unit,
) {
    val livePos = if (positionMsFlow != null) {
        positionMsFlow.collectAsStateWithLifecycle().value
    } else {
        fallbackPositionMs
    }
    val duration = durationMs.coerceAtLeast(1L)
    var isDragging by remember(trackId) { mutableStateOf(false) }
    var dragPos by remember(trackId) { mutableFloatStateOf(0f) }
    val sliderValue = if (isDragging) dragPos else livePos.toFloat().coerceIn(0f, duration.toFloat())

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
        trackHeight = 3.dp,
        thumbRadius = 7.dp,
        modifier = Modifier.testTag("now_playing_seekbar"),
    )

    val timeStyle = MaterialTheme.typography.labelLarge.copy(
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        fontFeatureSettings = "tnum",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = formatTimecode(if (isDragging) dragPos.toLong() else livePos),
            style = timeStyle,
            color = Color.White.copy(alpha = 0.62f),
        )
        Text(
            text = formatTimecode(durationMs),
            style = timeStyle,
            color = Color.White.copy(alpha = 0.62f),
        )
    }
}

@Composable
private fun UpNextGlassCard(
    track: Track,
    upNextTrack: Track?,
    playbackState: PlaybackUiState,
    onOpenQueue: () -> Unit,
    onSelectQueueIndex: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val cardShape = RoundedCornerShape(26.dp)
    val upcoming = remember(playbackState.queue, playbackState.currentIndex) {
        val start = playbackState.currentIndex + 1
        if (start in playbackState.queue.indices) {
            playbackState.queue.drop(start).take(3).mapIndexed { i, t -> (start + i) to t }
        } else emptyList()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(Color.White.copy(alpha = 0.07f))
            .border(1.dp, Color.White.copy(alpha = 0.10f), cardShape)
            .animateContentSize(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val previewTrack = upNextTrack ?: track
            ArtworkImage(
                artworkUri = previewTrack.customArtworkUri ?: previewTrack.artworkUri,
                fallbackUri = previewTrack.contentUri,
                maxSizePx = 160,
                modifier = Modifier.size(48.dp),
                cornerRadius = 24.dp,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (upNextTrack != null) "Up Next" else "Queue (${playbackState.queue.size})",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                )
                Text(
                    text = if (upNextTrack != null) {
                        "${upNextTrack.title} · ${upNextTrack.artist}"
                    } else {
                        "Tap to view playback queue"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.66f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(
                onClick = onOpenQueue,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.10f))
                    .testTag("up_next_open_queue_btn"),
            ) {
                Icon(
                    imageVector = MaxmizeIcons.Queue,
                    contentDescription = "Open queue",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (expanded) MaxmizeIcons.ChevronDown else MaxmizeIcons.ChevronUp,
                    contentDescription = if (expanded) "Collapse up next" else "Expand up next",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        if (expanded) {
            upcoming.forEach { (index, t) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectQueueIndex(index) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ArtworkImage(
                        artworkUri = t.customArtworkUri ?: t.artworkUri,
                        fallbackUri = t.contentUri,
                        modifier = Modifier.size(42.dp),
                        cornerRadius = 12.dp,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = t.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = t.artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.60f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Text(
                text = "View full queue",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenQueue)
                    .padding(horizontal = 18.dp, vertical = 14.dp),
            )
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
    val shape = RoundedCornerShape(26.dp)
    Row(
        modifier = modifier
            .height(52.dp)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color.White.copy(alpha = 0.10f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = Color.White,
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
