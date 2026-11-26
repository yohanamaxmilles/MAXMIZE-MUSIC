package com.example.ui.lyrics

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.lyrics.LrcFormatter
import com.example.data.model.LyricLine
import com.example.data.model.LyricsDocument
import com.example.data.model.LyricsSearchCandidate
import com.example.data.model.Track
import com.example.ui.components.formatTimecode
import kotlinx.coroutines.flow.StateFlow

@Composable
private fun LyricsModalFrame(
    title: String,
    onDismiss: () -> Unit,
    actions: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    BackHandler(onBack = onDismiss)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.56f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            )
            .imePadding()
            .padding(horizontal = 20.dp, vertical = 24.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            shadowElevation = 12.dp,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.padding(22.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                content()
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    actions()
                }
            }
        }
    }
}

@Composable
fun LyricsSearchDialog(
    track: Track,
    searchState: LyricsSearchUiState,
    onSearch: (String?) -> Unit,
    onSelectCandidate: (LyricsSearchCandidate) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember(track.id) {
        val initial = listOf(track.artist, track.title)
            .filter { it.isNotBlank() && !it.equals("Unknown Artist", ignoreCase = true) }
            .joinToString(" ")
        mutableStateOf(initial)
    }

    LyricsModalFrame(
        title = "Search Online Lyrics",
        onDismiss = onDismiss,
        actions = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                label = { Text("Artist & Title") },
                modifier = Modifier
                    .weight(1f)
                    .testTag("lyrics_search_input"),
            )
            Button(
                onClick = { onSearch(query.trim().ifBlank { null }) },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("lyrics_search_submit_btn"),
            ) {
                Icon(Icons.Rounded.Search, contentDescription = "Search")
            }
        }

        when {
            searchState.isSearching -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(vertical = 12.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("Searching LRCLIB…", style = MaterialTheme.typography.bodyMedium)
                }
            }
            searchState.errorMessage != null -> {
                Text(
                    text = searchState.errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            searchState.candidates.isEmpty() -> {
                Text(
                    text = "Tap search to look up matching synced or plain lyrics.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(searchState.candidates, key = { it.id }) { candidate ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    onSelectCandidate(candidate)
                                    onDismiss()
                                },
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(12.dp),
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = candidate.trackName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = "${candidate.artistName} · ${candidate.provider}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (candidate.hasSyncedLyrics) "SYNCED" else "PLAIN",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (candidate.hasSyncedLyrics) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LyricsEditDialog(
    initialRawText: String,
    hasCustomOrSaved: Boolean,
    onSave: (String) -> Unit,
    onClearCustom: () -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember(initialRawText) { mutableStateOf(initialRawText) }

    LyricsModalFrame(
        title = "Edit or Paste Lyrics",
        onDismiss = onDismiss,
        actions = {
            if (hasCustomOrSaved) {
                TextButton(
                    onClick = {
                        onClearCustom()
                        onDismiss()
                    },
                ) {
                    Text("Clear")
                }
            }
            TextButton(onClick = onDismiss) { Text("Cancel") }
            Spacer(modifier = Modifier.width(6.dp))
            Button(
                onClick = {
                    onSave(draft)
                    onDismiss()
                },
                modifier = Modifier.testTag("lyrics_editor_save_btn"),
            ) {
                Text("Save")
            }
        },
    ) {
        Text(
            text = "Supports plain text or LRC timestamps like [00:14.25]Line.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            placeholder = { Text("[00:12.00]First line…") },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 160.dp, max = 260.dp)
                .testTag("lyrics_editor_field"),
        )
    }
}

@Composable
fun LyricsSyncToolsDialog(
    document: LyricsDocument?,
    playbackPositionMs: Long,
    onAdjustOffset: (Long) -> Unit,
    onResetOffset: () -> Unit,
    onSaveStampedLines: (List<LyricLine>) -> Unit,
    onDismiss: () -> Unit,
    positionMsFlow: StateFlow<Long>? = null,
) {
    val livePositionMs = if (positionMsFlow != null) {
        positionMsFlow.collectAsStateWithLifecycle().value
    } else {
        playbackPositionMs
    }
    val initialLines = remember(document?.trackId, document?.rawText) {
        document?.lines.orEmpty()
    }
    val stampLines = remember(initialLines) {
        mutableStateListOf<LyricLine>().apply { addAll(initialLines) }
    }
    var cursor by remember(initialLines) { mutableIntStateOf(0) }

    LyricsModalFrame(
        title = "Timing & Sync",
        onDismiss = onDismiss,
        actions = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
    ) {
        val offsetMs = document?.syncOffsetMs ?: 0L
        val offsetLabel = if (offsetMs >= 0) "+${offsetMs}ms" else "${offsetMs}ms"

        Text(
            text = "Global Timing Offset: $offsetLabel",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedButton(
                onClick = { onAdjustOffset(-500L) },
                contentPadding = PaddingValues(horizontal = 8.dp),
                modifier = Modifier.weight(1f),
            ) { Text("-500") }
            OutlinedButton(
                onClick = { onAdjustOffset(-100L) },
                contentPadding = PaddingValues(horizontal = 8.dp),
                modifier = Modifier.weight(1f),
            ) { Text("-100") }
            OutlinedButton(
                onClick = { onAdjustOffset(100L) },
                contentPadding = PaddingValues(horizontal = 8.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("sync_nudge_plus_100"),
            ) { Text("+100") }
            OutlinedButton(
                onClick = { onAdjustOffset(500L) },
                contentPadding = PaddingValues(horizontal = 8.dp),
                modifier = Modifier.weight(1f),
            ) { Text("+500") }
        }
        Row(
            horizontalArrangement = Arrangement.End,
            modifier = Modifier.fillMaxWidth(),
        ) {
            TextButton(onClick = onResetOffset) {
                Text("Reset Offset")
            }
        }

        if (stampLines.isNotEmpty()) {
            HorizontalDivider()
            Text(
                text = "Tap-to-Stamp Sync (${formatTimecode(livePositionMs)})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            val previewLine = stampLines.getOrNull(cursor)
            Text(
                text = previewLine?.let { "Next line (${cursor + 1}/${stampLines.size}): ${it.text}" }
                    ?: "All ${stampLines.size} lines stamped",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                FilledTonalButton(
                    enabled = cursor in stampLines.indices,
                    onClick = {
                        if (cursor in stampLines.indices) {
                            stampLines[cursor] = stampLines[cursor].copy(timestampMs = livePositionMs)
                            cursor = (cursor + 1).coerceAtMost(stampLines.size)
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("sync_stamp_next_btn"),
                ) {
                    Icon(Icons.Rounded.TouchApp, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Stamp Line")
                }
                Button(
                    onClick = {
                        onSaveStampedLines(stampLines.toList())
                        onDismiss()
                    },
                    modifier = Modifier.testTag("sync_save_btn"),
                ) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save")
                }
            }
            val currentStampMs = previewLine?.timestampMs
            if (currentStampMs != null) {
                Text(
                    text = "Current stamp: ${LrcFormatter.formatTimestamp(currentStampMs)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
fun LyricsFontSizeDialog(
    currentSizeSp: Float,
    onChangeFontSize: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    LyricsModalFrame(
        title = "Lyrics Text Size",
        onDismiss = onDismiss,
        actions = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
    ) {
        Text(
            text = "${currentSizeSp.toInt()} sp",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        Slider(
            value = currentSizeSp,
            onValueChange = onChangeFontSize,
            valueRange = 16f..32f,
            steps = 7,
        )
    }
}
