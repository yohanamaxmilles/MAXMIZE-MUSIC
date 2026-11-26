package com.example.ui.lyrics

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.LyricLine
import com.example.data.model.LyricsDocument
import com.example.data.model.Track
import kotlin.math.abs

private val LyricLineShape = RoundedCornerShape(12.dp)

@Composable
fun LyricsLoadingState(
    track: Track,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("lyrics_loading_state"),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.dp,
                modifier = Modifier.size(36.dp),
            )
            Text(
                text = "Finding lyrics for ${track.title}…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun LyricsErrorState(
    message: String,
    isOffline: Boolean,
    onRetry: () -> Unit,
    onSearchOnline: () -> Unit,
    onEditManual: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("lyrics_error_state"),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            Icon(
                imageVector = if (isOffline) Icons.Rounded.CloudOff else Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(44.dp),
            )
            Text(
                text = if (isOffline) "Connection unavailable" else "Couldn't load lyrics",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = onRetry,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("lyrics_retry_btn"),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Retry")
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onSearchOnline,
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                    Text("Search")
                }
                OutlinedButton(
                    onClick = onEditManual,
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                    Text("Paste Lyrics")
                }
            }
        }
    }
}

@Composable
fun LyricsEmptyState(
    reason: String?,
    onSearchOnline: () -> Unit,
    onEditManual: () -> Unit,
    onImportFile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("lyrics_empty_state"),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Lyrics,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(44.dp),
            )
            Text(
                text = "No lyrics found",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Text(
                text = reason ?: "Search online, paste your own lyrics, or import an .lrc file.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = onSearchOnline,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("lyrics_empty_search_btn"),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Search Online")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = onEditManual,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("lyrics_empty_edit_btn"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.EditNote,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Paste")
                }
                OutlinedButton(
                    onClick = onImportFile,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("lyrics_empty_import_btn"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Import .lrc")
                }
            }
        }
    }
}

@Composable
fun SyncedLyricsView(
    document: LyricsDocument,
    activeLineIndex: Int,
    activeWordIndex: Int,
    fontSizeSp: Float,
    isAutoFollowEnabled: Boolean,
    onLineTapped: (LyricLine) -> Unit,
    onUserScrolled: () -> Unit,
    onResumeAutoFollow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val density = LocalDensity.current

    // Detect only genuine user touch/wheel drags, never programmatic auto-scrolls.
    val userScrollDetector = remember(onUserScrolled) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y != 0f) {
                    onUserScrolled()
                }
                return Offset.Zero
            }
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val viewportHeightPx = with(density) { maxHeight.roundToPx() }
        val targetCenterOffsetPx = remember(viewportHeightPx) {
            -(viewportHeightPx * 0.28f).toInt()
        }
        var lastScrolledLine by remember(document.trackId, document.rawText) { mutableIntStateOf(-1) }

        LaunchedEffect(activeLineIndex, isAutoFollowEnabled, targetCenterOffsetPx) {
            if (isAutoFollowEnabled && activeLineIndex in document.lines.indices) {
                val jumpDistance = if (lastScrolledLine < 0) Int.MAX_VALUE else abs(activeLineIndex - lastScrolledLine)
                if (jumpDistance > 4) {
                    listState.scrollToItem(
                        index = activeLineIndex,
                        scrollOffset = targetCenterOffsetPx,
                    )
                } else {
                    listState.animateScrollToItem(
                        index = activeLineIndex,
                        scrollOffset = targetCenterOffsetPx,
                    )
                }
                lastScrolledLine = activeLineIndex
            }
        }

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                top = (maxHeight * 0.22f).coerceAtLeast(32.dp),
                bottom = (maxHeight * 0.42f).coerceAtLeast(96.dp),
                start = 24.dp,
                end = 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(userScrollDetector)
                .testTag("lyrics_synced_list"),
        ) {
            itemsIndexed(
                items = document.lines,
                key = { idx, line -> "${idx}_${line.timestampMs ?: -1L}_${line.text.hashCode()}" },
            ) { index, line ->
                val isActive = index == activeLineIndex
                val isPast = activeLineIndex >= 0 && index < activeLineIndex
                SyncedLyricLineItem(
                    line = line,
                    isActive = isActive,
                    isPast = isPast,
                    activeWordIndex = if (isActive) activeWordIndex else -1,
                    fontSizeSp = fontSizeSp,
                    onClick = { onLineTapped(line) },
                    modifier = Modifier.testTag("lyric_line_$index"),
                )
            }
        }

        AnimatedVisibility(
            visible = !isAutoFollowEnabled,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp),
        ) {
            Surface(
                onClick = onResumeAutoFollow,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 4.dp,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("lyrics_follow_sync_btn"),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MyLocation,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "Resume Auto-Sync",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun SyncedLyricLineItem(
    line: LyricLine,
    isActive: Boolean,
    isPast: Boolean,
    activeWordIndex: Int,
    fontSizeSp: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale by animateFloatAsState(
        targetValue = if (isActive) 1.03f else 1.0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "lyric_line_scale",
    )

    val activeColor = MaterialTheme.colorScheme.onBackground
    val inactiveColor = MaterialTheme.colorScheme.onBackground.copy(
        alpha = if (isPast) 0.34f else 0.56f,
    )
    val wordAccent = MaterialTheme.colorScheme.primary
    val spokenWordColor = MaterialTheme.colorScheme.onBackground
    val upcomingWordColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f)

    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clip(LyricLineShape)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = MaterialTheme.colorScheme.primary),
                onClick = onClick,
            )
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
    ) {
        if (isActive && line.words.isNotEmpty()) {
            val annotated = remember(line.words, activeWordIndex, wordAccent, spokenWordColor, upcomingWordColor) {
                buildAnnotatedString {
                    line.words.forEachIndexed { idx, word ->
                        val color = when {
                            idx < activeWordIndex -> spokenWordColor
                            idx == activeWordIndex -> wordAccent
                            else -> upcomingWordColor
                        }
                        withStyle(
                            SpanStyle(
                                color = color,
                                fontWeight = if (idx <= activeWordIndex) FontWeight.Bold else FontWeight.SemiBold,
                            )
                        ) {
                            append(word.text)
                        }
                    }
                }
            }
            Text(
                text = annotated,
                fontSize = fontSizeSp.sp,
                lineHeight = (fontSizeSp * 1.38f).sp,
            )
        } else {
            Text(
                text = line.text,
                fontSize = fontSizeSp.sp,
                lineHeight = (fontSizeSp * 1.38f).sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                color = if (isActive) activeColor else inactiveColor,
            )
        }

        if (!line.translation.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = line.translation,
                fontSize = (fontSizeSp * 0.72f).coerceAtLeast(13f).sp,
                lineHeight = (fontSizeSp * 0.96f).coerceAtLeast(18f).sp,
                fontWeight = FontWeight.Normal,
                color = if (isActive) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                },
            )
        }
    }
}

@Composable
fun UnsyncedLyricsView(
    document: LyricsDocument,
    fontSizeSp: Float,
    onOpenSyncTools: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier
            .fillMaxSize()
            .testTag("lyrics_unsynced_list"),
    ) {
        item(key = "unsynced_notice") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
            ) {
                Text(
                    text = "Static lyrics (no timestamps)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FilledTonalButton(
                    onClick = onOpenSyncTools,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                    Text(
                        text = "Sync Timing",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
        itemsIndexed(
            items = document.lines,
            key = { idx, line -> "${idx}_${line.text.hashCode()}" },
        ) { idx, line ->
            Text(
                text = line.text,
                fontSize = fontSizeSp.sp,
                lineHeight = (fontSizeSp * 1.42f).sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("unsynced_line_$idx"),
            )
        }
        item { Spacer(modifier = Modifier.height(32.dp)) }
    }
}
