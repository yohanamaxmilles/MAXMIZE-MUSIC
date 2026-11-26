package com.example.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.AutomixUiState
import com.example.data.model.DjQueuedTrack
import com.example.data.model.MixHistoryEntry
import com.example.data.model.PlaybackUiState
import com.example.data.model.TrackAnalysis
import com.example.ui.components.ArtworkImage
import com.example.ui.theme.MonoBadgeStyle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun DjQueueScreen(
    automixState: AutomixUiState,
    playbackState: PlaybackUiState,
    analyses: Map<String, TrackAnalysis>,
    mixHistory: List<MixHistoryEntry>,
    onBack: () -> Unit,
    onOpenSearch: () -> Unit,
    onPlayDjTrackNow: (String) -> Unit,
    onMoveDjQueueItem: (Int, Int) -> Unit,
    onRemoveDjQueueItem: (Int) -> Unit,
    onRecomputeQueue: () -> Unit,
    onStopAutomix: () -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier,
    embeddedInHub: Boolean = false,
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Queue, 1 = History

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("dj_queue_screen"),
    ) {
        DjStudioSectionHeader(
            eyebrow = "PLAYBACK FLOW",
            title = "Your queue",
            description = "Arrange upcoming tracks and review recent mixes.",
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
        )
        if (!embeddedInHub) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                        contentDescription = "Back",
                        modifier = Modifier.size(30.dp),
                    )
                }
                Text(
                    text = "DJ Queue",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                )
                IconButton(onClick = onOpenSearch) {
                    Icon(Icons.Rounded.Search, contentDescription = "Search library")
                }
            }
        }

        // Queue | History Segmented Switch
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            listOf("Queue", "History").forEachIndexed { idx, label ->
                val selected = selectedTab == idx
                val shape = RoundedCornerShape(10.dp)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(shape)
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surface
                        )
                        .border(
                            width = 0.75.dp,
                            color = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant,
                            shape = shape,
                        )
                        .clickable { selectedTab = idx }
                        .testTag("dj_subtab_${label.lowercase()}"),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = if (selected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        if (selectedTab == 0) {
            val activeTrack = if (automixState.activeDeckIndex == 0) {
                automixState.deckA.track ?: playbackState.currentTrack
            } else {
                automixState.deckB.track ?: playbackState.currentTrack
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (activeTrack != null) {
                    item {
                        val a = analyses[activeTrack.id]
                        val shape = RoundedCornerShape(12.dp)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(shape)
                                .background(MaterialTheme.colorScheme.surface)
                                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), shape)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "NOW",
                                style = MonoBadgeStyle,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.width(30.dp),
                            )
                            ArtworkImage(
                                artworkUri = activeTrack.displayArtwork,
                                fallbackUri = activeTrack.contentUri,
                                modifier = Modifier.size(46.dp),
                                cornerRadius = 8.dp,
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = activeTrack.title,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = if (a != null) {
                                        "${activeTrack.artist} · ${a.bpm.roundToInt()} BPM · ${a.camelotCode} · ${a.subgenre.ifBlank { activeTrack.genre }}"
                                    } else activeTrack.artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Icon(
                                imageVector = Icons.Rounded.Equalizer,
                                contentDescription = "Now Mixing",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }

                if (automixState.djQueue.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.QueueMusic,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(40.dp),
                            )
                            Text("No DJ Queue Active", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "Start Automix to generate a harmonically matched queue.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    itemsIndexed(automixState.djQueue, key = { idx, item -> "${item.track.id}_$idx" }) { index, item ->
                        DjQueueTrackRow(
                            item = item,
                            index = index,
                            total = automixState.djQueue.size,
                            onMixNow = { onPlayDjTrackNow(item.track.id) },
                            onMoveUp = { onMoveDjQueueItem(index, index - 1) },
                            onMoveDown = { onMoveDjQueueItem(index, index + 1) },
                            onRemove = { onRemoveDjQueueItem(index) },
                        )
                    }
                }
            }

            HorizontalDivider(thickness = 0.75.dp, color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val btnShape = RoundedCornerShape(12.dp)
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(btnShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(0.75.dp, MaterialTheme.colorScheme.outlineVariant, btnShape)
                        .clickable(onClick = onStopAutomix),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Clear", style = MaterialTheme.typography.labelLarge)
                }
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(btnShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(0.75.dp, MaterialTheme.colorScheme.outlineVariant, btnShape)
                        .clickable(onClick = onRecomputeQueue),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Shuffle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Remix Order", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("dj_history_list"),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (mixHistory.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(40.dp),
                            )
                            Text("No Mix History Yet", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "Completed Automix transitions will be logged here.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    items(mixHistory, key = { it.id }) { entry ->
                        val shape = RoundedCornerShape(12.dp)
                        val timeLabel = remember(entry.mixedAt) {
                            SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(entry.mixedAt))
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(shape)
                                .background(MaterialTheme.colorScheme.surface)
                                .border(0.75.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "${entry.fromTitle} -> ${entry.toTitle}",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = "${entry.compatibilityScore}%",
                                    style = MonoBadgeStyle,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Text(
                                text = "${entry.bpmFrom.toInt()} -> ${entry.bpmTo.toInt()} BPM · ${entry.camelotFrom} -> ${entry.camelotTo} · ${entry.transitionStyle.label}",
                                style = MonoBadgeStyle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = "${entry.mixMode.label} · $timeLabel",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    }
                }
            }
            if (mixHistory.isNotEmpty()) {
                HorizontalDivider(thickness = 0.75.dp, color = MaterialTheme.colorScheme.outlineVariant)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onClearHistory)
                        .padding(14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Clear Mix History", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun DjQueueTrackRow(
    item: DjQueuedTrack,
    index: Int,
    total: Int,
    onMixNow: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    val a = item.analysis
    val metaLine = buildString {
        append(item.track.artist)
        if (a != null) {
            append(" · ${a.bpm.roundToInt()} BPM · ${a.camelotCode}")
            val g = a.subgenre.ifBlank { item.track.genre }
            if (g.isNotBlank()) append(" · $g")
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(0.75.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(onClick = onMixNow)
            .padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "#${(index + 1).toString().padStart(2, '0')}",
            style = MonoBadgeStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(28.dp),
        )
        ArtworkImage(
            artworkUri = item.track.displayArtwork,
            fallbackUri = item.track.contentUri,
            modifier = Modifier.size(44.dp),
            cornerRadius = 8.dp,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.track.title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = metaLine,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF24311A))
                .padding(horizontal = 7.dp, vertical = 3.dp),
        ) {
            Text(
                text = "${item.compatibilityScore}%",
                style = MonoBadgeStyle,
                color = Color(0xFFE2F36B),
            )
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = "DJ Queue track options",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Mix Now") },
                    leadingIcon = { Icon(Icons.Rounded.PlayArrow, contentDescription = null) },
                    onClick = { menuOpen = false; onMixNow() },
                )
                DropdownMenuItem(
                    text = { Text("Move Up") },
                    leadingIcon = { Icon(Icons.Rounded.ArrowUpward, contentDescription = null) },
                    enabled = index > 0,
                    onClick = { menuOpen = false; onMoveUp() },
                )
                DropdownMenuItem(
                    text = { Text("Move Down") },
                    leadingIcon = { Icon(Icons.Rounded.ArrowDownward, contentDescription = null) },
                    enabled = index < total - 1,
                    onClick = { menuOpen = false; onMoveDown() },
                )
                DropdownMenuItem(
                    text = { Text("Remove") },
                    leadingIcon = { Icon(Icons.Rounded.Close, contentDescription = null) },
                    onClick = { menuOpen = false; onRemove() },
                )
            }
        }
    }
}
