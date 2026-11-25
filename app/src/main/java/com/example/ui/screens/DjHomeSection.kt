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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AutomixConfig
import com.example.data.model.AutomixUiState
import com.example.data.model.PlaybackUiState
import com.example.data.model.TrackAnalysis
import com.example.ui.components.ArtworkImage
import com.example.ui.components.EditorialGroupCard
import com.example.ui.theme.MonoBadgeStyle
import com.example.ui.theme.MonoStatStyle
import com.example.ui.theme.MonoTimecodeStyle
import kotlin.math.roundToInt

@Composable
fun DjHomeSection(
    automixState: AutomixUiState,
    automixConfig: AutomixConfig,
    playbackState: PlaybackUiState,
    analyses: Map<String, TrackAnalysis>,
    totalLibraryTracks: Int,
    onStartAutomix: () -> Unit,
    onStopAutomix: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNextMix: () -> Unit,
    onAnalyzeLibrary: () -> Unit,
    onNavigateTab: (DjSectionTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeDeck = if (automixState.activeDeckIndex == 0) automixState.deckA else automixState.deckB
    val incomingDeck = if (automixState.activeDeckIndex == 0) automixState.deckB else automixState.deckA
    val currentTrack = activeDeck.track ?: playbackState.currentTrack
    val incomingTrack = incomingDeck.track ?: automixState.djQueue.firstOrNull()?.track
    val activeAnalysis = currentTrack?.let { analyses[it.id] }
    val analyzedCount = analyses.size

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("dj_home_section"),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Automix Status & Deck Preview Card
        item {
            EditorialGroupCard(
                cornerRadius = 16.dp,
                borderColor = if (automixState.isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.outlineVariant,
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (automixState.isActive) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                            )
                            Text(
                                text = if (automixState.isActive) "Automix on" else "Automix ready",
                                style = MonoBadgeStyle,
                                color = if (automixState.isActive) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .clickable { onNavigateTab(DjSectionTab.PROFILES) }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = automixState.activeProfileName,
                                style = MonoBadgeStyle,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }

                    // Current Track vs Incoming Track Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ArtworkImage(
                            artworkUri = currentTrack?.displayArtwork,
                            fallbackUri = currentTrack?.contentUri,
                            modifier = Modifier.size(64.dp),
                            cornerRadius = 12.dp,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (automixState.isActive) "Now on ${activeDeck.deckName}" else "Ready Deck",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = currentTrack?.title ?: "Select a track or start Automix",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = currentTrack?.artist ?: "${automixConfig.mode.label} · ${automixConfig.transitionStyle.label}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    // 4-Metric Technical Readout (JetBrains Mono)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        val bpmText = when {
                            activeDeck.bpm > 0f -> "${activeDeck.bpm.roundToInt()}"
                            activeAnalysis != null -> "${activeAnalysis.bpm.roundToInt()}"
                            else -> "--"
                        }
                        val keyText = activeDeck.camelotCode.takeIf { it != "--" }
                            ?: activeAnalysis?.camelotCode
                            ?: "--"
                        val genreText = activeDeck.genreLabel.ifBlank {
                            activeAnalysis?.subgenre?.ifBlank { currentTrack?.genre } ?: "--"
                        }
                        val energyText = activeAnalysis?.let { "${(it.energy * 100).roundToInt()}%" } ?: "--"

                        TelemetryMetricCell(label = "BPM", value = bpmText)
                        TelemetryMetricCell(label = "KEY", value = keyText)
                        TelemetryMetricCell(label = "ENERGY", value = energyText)
                        TelemetryMetricCell(label = "GENRE", value = genreText, maxWidthDp = 90)
                    }

                    // Primary Start / Pause / Stop Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (!automixState.isActive) {
                            Button(
                                onClick = onStartAutomix,
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .testTag("dj_home_start_btn"),
                            ) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Start Automix", fontWeight = FontWeight.SemiBold)
                            }
                            OutlinedButton(
                                onClick = { onNavigateTab(DjSectionTab.AUTOMIX) },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.height(44.dp),
                            ) {
                                Text("Open Decks")
                            }
                        } else {
                            Button(
                                onClick = onTogglePlayPause,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp),
                            ) {
                                Icon(
                                    imageVector = if (playbackState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (playbackState.isPlaying) "Pause" else "Resume", fontWeight = FontWeight.SemiBold)
                            }
                            OutlinedButton(
                                onClick = onSkipNextMix,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.height(44.dp),
                            ) {
                                Icon(Icons.Rounded.SkipNext, contentDescription = "Mix Next", modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Mix")
                            }
                            OutlinedButton(
                                onClick = onStopAutomix,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.height(44.dp),
                            ) {
                                Icon(Icons.Rounded.Stop, contentDescription = "Stop Automix", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }

        // Next Track & Queue Overview
        item {
            EditorialGroupCard(cornerRadius = 14.dp) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Up Next in DJ Queue",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        )
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onNavigateTab(DjSectionTab.QUEUE) }
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "${automixState.djQueue.size} queued",
                                style = MonoTimecodeStyle,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                contentDescription = "Open DJ Queue",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }

                    if (incomingTrack == null && automixState.djQueue.isEmpty()) {
                        Text(
                            text = "Start Automix to build a harmonically matched queue based on your active DJ Profile.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        val previewItems = automixState.djQueue.take(3)
                        if (previewItems.isNotEmpty()) {
                            previewItems.forEachIndexed { idx, item ->
                                if (idx > 0) {
                                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onNavigateTab(DjSectionTab.QUEUE) }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "${idx + 1}".padStart(2, '0'),
                                        style = MonoBadgeStyle,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.width(24.dp),
                                    )
                                    ArtworkImage(
                                        artworkUri = item.track.displayArtwork,
                                        fallbackUri = item.track.contentUri,
                                        modifier = Modifier.size(38.dp),
                                        cornerRadius = 6.dp,
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.track.title,
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = "${item.track.artist} · ${item.analysis?.bpm?.roundToInt() ?: "--"} BPM · ${item.analysis?.camelotCode ?: "--"}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    Text(
                                        text = "${item.compatibilityScore}%",
                                        style = MonoBadgeStyle,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // DJ Analysis Summary
        item {
            EditorialGroupCard(cornerRadius = 14.dp) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateTab(DjSectionTab.ANALYSIS) }
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Library Audio Analysis",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            )
                            Text(
                                text = if (automixState.isAnalyzing) {
                                    "Scanning BPM, Camelot Key & Energy (${automixState.analyzedCount}/${automixState.totalTrackCount})"
                                } else {
                                    "$analyzedCount of $totalLibraryTracks tracks analyzed"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        OutlinedButton(
                            onClick = onAnalyzeLibrary,
                            enabled = !automixState.isAnalyzing,
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Text(if (automixState.isAnalyzing) "Running..." else "Analyze")
                        }
                    }
                    val ratio = if (totalLibraryTracks > 0) {
                        (analyzedCount.toFloat() / totalLibraryTracks.toFloat()).coerceIn(0f, 1f)
                    } else 0f
                    LinearProgressIndicator(
                        progress = { ratio },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(CircleShape),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
            }
        }

        // Quick Launch Actions Grid (2x2)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    DjQuickLaunchCard(
                        icon = Icons.Rounded.GraphicEq,
                        title = "Automix Decks",
                        subtitle = "Dual-deck transition view",
                        onClick = { onNavigateTab(DjSectionTab.AUTOMIX) },
                        modifier = Modifier.weight(1f),
                    )
                    DjQuickLaunchCard(
                        icon = Icons.Rounded.QueueMusic,
                        title = "DJ Queue",
                        subtitle = "Compatibility & history",
                        onClick = { onNavigateTab(DjSectionTab.QUEUE) },
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    DjQuickLaunchCard(
                        icon = Icons.Rounded.Tune,
                        title = "DJ Profiles",
                        subtitle = "Presets & custom mixes",
                        onClick = { onNavigateTab(DjSectionTab.PROFILES) },
                        modifier = Modifier.weight(1f),
                    )
                    DjQuickLaunchCard(
                        icon = Icons.Rounded.Analytics,
                        title = "Music Analysis",
                        subtitle = "BPM, Key & Genre insights",
                        onClick = { onNavigateTab(DjSectionTab.ANALYSIS) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun TelemetryMetricCell(
    label: String,
    value: String,
    maxWidthDp: Int = 64,
) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MonoStatStyle.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DjQuickLaunchCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(0.75.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
