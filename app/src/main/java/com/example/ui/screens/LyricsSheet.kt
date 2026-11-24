package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.interaction.DragInteraction
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.LyricsResult
import com.example.data.model.LyricsSearchCandidate
import com.example.data.model.LyricsSource
import com.example.data.model.Track
import com.example.ui.components.ArtworkImage
import com.example.ui.components.MaxmizeSlider
import com.example.ui.components.formatTimecode
import com.example.ui.theme.BadgeGoldBg
import com.example.ui.theme.BadgeGoldFg
import com.example.ui.theme.MonoBadgeStyle
import com.example.ui.theme.MonoTimecodeStyle

@Composable
fun LyricsSheet(
    track: Track,
    lyricsResult: LyricsResult?,
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    fontSizeSp: Float,
    searchCandidates: List<LyricsSearchCandidate>,
    isSearchingOnline: Boolean,
    onSeekTo: (Long) -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onRefreshLyrics: () -> Unit,
    onSearchOnline: (String?) -> Unit,
    onApplyCandidate: (LyricsSearchCandidate) -> Unit,
    onAdjustSyncOffset: (Long) -> Unit,
    onChangeFontSize: (Float) -> Unit,
    onImportLyricsUri: (android.net.Uri) -> Unit,
    onSaveManualLyrics: (String) -> Unit,
    onClearManualLyrics: () -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var showEditorDialog by remember { mutableStateOf(false) }
    var showSearchDialog by remember { mutableStateOf(false) }
    var showTimingBar by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var autoFollowEnabled by remember(track.id) { mutableStateOf(true) }
    var isProgrammaticScroll by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) onImportLyricsUri(uri)
    }

    // Strict trackId guard: never display lyrics belonging to a different track
    val matchingResult = lyricsResult?.takeIf { it.trackId == track.id }
    val lines = matchingResult?.lines.orEmpty()
    val isSynced = matchingResult?.isSynced == true
    val hasWordSync = matchingResult?.hasWordSync == true
    val isLoading = matchingResult?.isLoading == true
    val syncOffsetMs = matchingResult?.syncOffsetMs ?: 0L
    val effectivePositionMs = (positionMs + syncOffsetMs).coerceAtLeast(0L)

    val activeLineIndex = remember(track.id, lines, isSynced, effectivePositionMs) {
        if (!isSynced || lines.isEmpty()) -1
        else lines.indexOfLast { (it.timestampMs ?: Long.MAX_VALUE) <= effectivePositionMs }
    }

    LaunchedEffect(listState.interactionSource) {
        listState.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start && isSynced) {
                autoFollowEnabled = false
            }
        }
    }

    LaunchedEffect(track.id) {
        autoFollowEnabled = true
        listState.scrollToItem(0)
    }

    LaunchedEffect(track.id, activeLineIndex, autoFollowEnabled) {
        if (autoFollowEnabled && activeLineIndex >= 0 && isSynced) {
            isProgrammaticScroll = true
            runCatching {
                listState.animateScrollToItem((activeLineIndex - 2).coerceAtLeast(0))
            }
            isProgrammaticScroll = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("lyrics_screen"),
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                    contentDescription = "Back",
                    modifier = Modifier.size(30.dp),
                )
            }
            Text(
                text = "Lyrics",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        onSearchOnline("${track.title} ${track.artist}")
                        showSearchDialog = true
                    },
                    modifier = Modifier.testTag("search_lyrics_btn"),
                ) {
                    Icon(Icons.Rounded.Search, contentDescription = "Search online lyrics")
                }
                IconButton(
                    onClick = onRefreshLyrics,
                    modifier = Modifier.testTag("refresh_lyrics_btn"),
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Refresh lyrics")
                }
                Box {
                    IconButton(
                        onClick = { menuOpen = true },
                        modifier = Modifier.testTag("lyrics_more_btn"),
                    ) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "Lyrics options")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(if (showTimingBar) "Hide Sync & Size Controls" else "Sync Offset & Font Size") },
                            leadingIcon = { Icon(Icons.Rounded.Timer, contentDescription = null) },
                            onClick = { menuOpen = false; showTimingBar = !showTimingBar },
                        )
                        DropdownMenuItem(
                            text = { Text("Import .LRC File") },
                            leadingIcon = { Icon(Icons.Rounded.FileOpen, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                importLauncher.launch(arrayOf("text/*", "application/octet-stream", "*/*"))
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Edit / Paste Lyrics") },
                            leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                            onClick = { menuOpen = false; showEditorDialog = true },
                        )
                        if (matchingResult != null && matchingResult.source != LyricsSource.NONE) {
                            DropdownMenuItem(
                                text = { Text("Remove Cached Lyrics") },
                                leadingIcon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null) },
                                onClick = { menuOpen = false; onClearManualLyrics() },
                            )
                        }
                    }
                }
            }
        }

        // Track Artwork & Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ArtworkImage(
                artworkUri = track.customArtworkUri ?: track.artworkUri,
                fallbackUri = track.contentUri,
                modifier = Modifier.size(64.dp),
                cornerRadius = 12.dp,
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val badgeLabel = when {
                        isLoading -> "LOOKING UP LYRICS..."
                        matchingResult != null && matchingResult.source != LyricsSource.NONE -> buildString {
                            append(matchingResult.source.badgeLabel)
                            append(
                                when {
                                    hasWordSync -> " · WORD SYNC"
                                    isSynced -> " · SYNCED"
                                    else -> " · UNSYNCED"
                                }
                            )
                        }
                        else -> "NO LYRICS"
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(BadgeGoldBg)
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = badgeLabel,
                            style = MonoBadgeStyle,
                            color = BadgeGoldFg,
                        )
                    }
                    if (syncOffsetMs != 0L) {
                        val sign = if (syncOffsetMs > 0) "+" else ""
                        Text(
                            text = "${sign}${syncOffsetMs}ms",
                            style = MonoBadgeStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (showTimingBar) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Rounded.Timer, contentDescription = null, modifier = Modifier.size(16.dp))
                    TextButton(onClick = { onAdjustSyncOffset(-250L) }) { Text("-0.25s") }
                    TextButton(onClick = { onAdjustSyncOffset(250L) }) { Text("+0.25s") }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(Icons.Rounded.FormatSize, contentDescription = null, modifier = Modifier.size(16.dp))
                    TextButton(onClick = { onChangeFontSize(fontSizeSp - 2f) }) { Text("A-") }
                    TextButton(onClick = { onChangeFontSize(fontSizeSp + 2f) }) { Text("A+") }
                }
            }
        }

        HorizontalDivider(thickness = 0.75.dp, color = MaterialTheme.colorScheme.outlineVariant)

        // Lyrics Body
        when {
            isLoading && lines.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Text(
                            text = "Searching synced lyrics for ${track.title}...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            lines.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(28.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Lyrics,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(44.dp),
                        )
                        Text("No Lyrics Available", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = matchingResult?.statusNote
                                ?: "No cached, embedded, or matching online lyrics were found for this track.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = {
                                    onSearchOnline("${track.title} ${track.artist}")
                                    showSearchDialog = true
                                },
                                shape = RoundedCornerShape(10.dp),
                            ) {
                                Text("Search Online")
                            }
                            OutlinedButton(
                                onClick = { importLauncher.launch(arrayOf("text/*", "*/*")) },
                                shape = RoundedCornerShape(10.dp),
                            ) {
                                Text("Import .LRC")
                            }
                        }
                        Button(
                            onClick = { showEditorDialog = true },
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add Lyrics")
                        }
                    }
                }
            }
            else -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 26.dp, vertical = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        itemsIndexed(lines, key = { idx, line -> "${track.id}_${idx}_${line.timestampMs}" }) { index, line ->
                            val isActive = isSynced && index == activeLineIndex
                            val ts = line.timestampMs
                            val activeSize = (fontSizeSp + 2f).sp
                            val normalSize = fontSizeSp.sp

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .let { mod ->
                                        if (ts != null) mod.clickable {
                                            autoFollowEnabled = true
                                            onSeekTo((ts - syncOffsetMs).coerceAtLeast(0L))
                                        } else mod
                                    }
                                    .padding(vertical = 5.dp, horizontal = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                if (isActive && line.words.isNotEmpty()) {
                                    val primaryColor = MaterialTheme.colorScheme.primary
                                    val upcomingColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                                    val annotated = buildAnnotatedString {
                                        line.words.forEach { w ->
                                            val sung = effectivePositionMs >= w.startMs
                                            withStyle(
                                                SpanStyle(
                                                    color = if (sung) primaryColor else upcomingColor,
                                                    fontWeight = FontWeight.Bold,
                                                )
                                            ) {
                                                append(w.text)
                                            }
                                        }
                                    }
                                    Text(
                                        text = annotated,
                                        style = MaterialTheme.typography.headlineSmall.copy(
                                            fontSize = activeSize,
                                            lineHeight = (fontSizeSp + 9f).sp,
                                        ),
                                        textAlign = TextAlign.Center,
                                    )
                                } else {
                                    Text(
                                        text = line.text,
                                        style = MaterialTheme.typography.headlineSmall.copy(
                                            fontSize = if (isActive) activeSize else normalSize,
                                            lineHeight = (fontSizeSp + 8f).sp,
                                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                        ),
                                        color = when {
                                            isActive -> MaterialTheme.colorScheme.primary
                                            !isSynced -> MaterialTheme.colorScheme.onBackground
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                                        },
                                        textAlign = TextAlign.Center,
                                    )
                                }

                                if (!line.translation.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = line.translation,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontSize = (fontSizeSp - 4f).coerceAtLeast(13f).sp,
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                            alpha = if (isActive) 0.95f else 0.65f
                                        ),
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                        }
                    }

                    if (isSynced && !autoFollowEnabled && activeLineIndex >= 0) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 14.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(MaterialTheme.colorScheme.primary)
                                .clickable {
                                    autoFollowEnabled = true
                                    coroutineScope.launch {
                                        runCatching {
                                            listState.animateScrollToItem((activeLineIndex - 2).coerceAtLeast(0))
                                        }
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 9.dp)
                                .testTag("lyrics_follow_sync_btn"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Timer,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = "Follow Sync",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    }
                }
            }
        }

        // Bottom Gold Playback Dock
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 24.dp, vertical = 12.dp),
        ) {
            val safeDuration = durationMs.coerceAtLeast(1L)
            var isDragging by remember(track.id) { mutableStateOf(false) }
            var dragPos by remember(track.id) { mutableFloatStateOf(0f) }
            val sliderVal = if (isDragging) dragPos else positionMs.toFloat().coerceIn(0f, safeDuration.toFloat())

            MaxmizeSlider(
                value = sliderVal,
                onValueChange = { isDragging = true; dragPos = it },
                onValueChangeFinished = { isDragging = false; onSeekTo(dragPos.toLong()) },
                valueRange = 0f..safeDuration.toFloat(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = formatTimecode(if (isDragging) dragPos.toLong() else positionMs),
                    style = MonoTimecodeStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = formatTimecode(durationMs),
                    style = MonoTimecodeStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onSkipPrevious, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.SkipPrevious,
                        contentDescription = stringResource(R.string.cd_previous_track),
                        modifier = Modifier.size(32.dp),
                    )
                }
                Spacer(modifier = Modifier.width(24.dp))
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable(onClick = onTogglePlayPause),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(R.string.cd_play_pause),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(30.dp),
                    )
                }
                Spacer(modifier = Modifier.width(24.dp))
                IconButton(onClick = onSkipNext, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = stringResource(R.string.cd_next_track),
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
        }
    }

    if (showEditorDialog) {
        var text by remember(matchingResult) { mutableStateOf(matchingResult?.rawText.orEmpty()) }
        AlertDialog(
            onDismissRequest = { showEditorDialog = false },
            title = { Text("Edit or Paste Lyrics") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Plain text or [mm:ss.xx] LRC") },
                    minLines = 6,
                    maxLines = 12,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(onClick = {
                    onSaveManualLyrics(text)
                    showEditorDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showEditorDialog = false }) { Text("Cancel") }
            },
        )
    }

    if (showSearchDialog) {
        var query by remember(track.id) { mutableStateOf("${track.title} ${track.artist}") }
        AlertDialog(
            onDismissRequest = { showSearchDialog = false },
            title = { Text("Search Online Lyrics") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            label = { Text("Title / Artist") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        Button(onClick = { onSearchOnline(query) }) {
                            Text("Go")
                        }
                    }
                    if (isSearchingOnline) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp))
                        }
                    } else if (searchCandidates.isEmpty()) {
                        Text(
                            text = "No matching online lyrics found. Try editing the query or check your internet connection.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.height(240.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            items(searchCandidates, key = { it.id }) { cand ->
                                val shape = RoundedCornerShape(10.dp)
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(shape)
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                        .clickable {
                                            onApplyCandidate(cand)
                                            showSearchDialog = false
                                        }
                                        .padding(10.dp),
                                ) {
                                    Text(
                                        text = "${cand.trackName} · ${cand.artistName}",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = "${cand.provider} · ${if (cand.hasSyncedLyrics) "SYNCED" else "PLAIN"} · ${cand.matchScore}% match",
                                        style = MonoBadgeStyle,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSearchDialog = false }) { Text("Close") }
            },
        )
    }
}
