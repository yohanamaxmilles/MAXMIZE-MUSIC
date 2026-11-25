package com.example.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Loop
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.dj.HarmonicCompatibility
import com.example.data.model.AutomixConfig
import com.example.data.model.AutomixUiState
import com.example.data.model.DeckState
import com.example.data.model.MixMode
import com.example.data.model.PlaybackUiState
import com.example.data.model.Track
import com.example.data.model.TrackAnalysis
import com.example.player.RealtimeSpectrumBus
import com.example.ui.components.ArtworkImage
import com.example.ui.components.EditorialGroupCard
import com.example.ui.theme.LavenderCard
import com.example.ui.theme.LavenderCardDeep
import com.example.ui.theme.MonoBadgeStyle
import com.example.ui.theme.MonoStatStyle
import com.example.ui.theme.MonoTimecodeStyle
import kotlin.math.roundToInt

@Composable
fun AutomixScreen(
    automixState: AutomixUiState,
    automixConfig: AutomixConfig,
    playbackState: PlaybackUiState,
    analyses: Map<String, TrackAnalysis>,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDjQueue: () -> Unit,
    onStartAutomix: () -> Unit,
    onStopAutomix: () -> Unit,
    onSkipNextMix: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onToggleBpmMatch: () -> Unit,
    onToggleKeyLock: () -> Unit,
    onCycleMixMode: () -> Unit,
    onAnalyzeLibrary: () -> Unit,
    onCorrectAnalysis: (Track, Float, String) -> Unit,
    modifier: Modifier = Modifier,
    embeddedInHub: Boolean = false,
) {
    var correctionTrack by remember { mutableStateOf<Track?>(null) }
    val spectrumBands by RealtimeSpectrumBus.bands.collectAsStateWithLifecycle()

    val activeDeck = if (automixState.activeDeckIndex == 0) automixState.deckA else automixState.deckB
    val incomingDeck = if (automixState.activeDeckIndex == 0) automixState.deckB else automixState.deckA
    val incomingPreviewTrack = incomingDeck.track ?: automixState.djQueue.firstOrNull()?.track
    val incomingPreviewAnalysis = incomingPreviewTrack?.let { analyses[it.id] }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("automix_screen"),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        if (!embeddedInHub) {
            item {
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
                        text = if (automixState.isActive) "Automix Live" else "Automix",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Row {
                        IconButton(
                            onClick = onOpenDjQueue,
                            modifier = Modifier.testTag("automix_queue_btn"),
                        ) {
                            Icon(Icons.Rounded.QueueMusic, contentDescription = "DJ Queue")
                        }
                        IconButton(
                            onClick = onOpenSettings,
                            modifier = Modifier.testTag("automix_settings_btn"),
                        ) {
                            Icon(Icons.Rounded.Settings, contentDescription = "Automix Settings")
                        }
                    }
                }
            }
        }

        // Transition Status & Compatibility Header
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .border(0.75.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                            .clickable(onClick = onOpenSettings)
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                            .testTag("automix_profile_badge"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = "${automixState.activeProfileName} · ${automixConfig.transitionStyle.label}",
                            style = MonoBadgeStyle,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    if (automixState.currentCompatibilityScore > 0) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(LavenderCardDeep)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = "${automixState.currentCompatibilityScore}% MATCH",
                                style = MonoBadgeStyle,
                                color = LavenderCard,
                            )
                        }
                    }
                }

                // Live Spectrum Waveform & Crossfade Progress Card
                EditorialGroupCard(cornerRadius = 16.dp) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = when {
                                    automixState.isMixingTransition -> "CROSSFADING TRACKS"
                                    automixState.isActive -> "DUAL-DECK AUTOMIX ACTIVE"
                                    else -> "AUTOMIX READY"
                                },
                                style = MonoBadgeStyle,
                                color = if (automixState.isActive) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = "${automixConfig.transitionDurationSec}s · ${automixConfig.mode.label}",
                                style = MonoBadgeStyle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        SpectrumWaveformStrip(
                            spectrumBands = spectrumBands,
                            isPlaying = playbackState.isPlaying || automixState.isMixingTransition,
                            isMixing = automixState.isMixingTransition,
                        )

                        Text(
                            text = automixState.statusText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )

                        if (automixState.isMixingTransition) {
                            LinearProgressIndicator(
                                progress = { automixState.transitionProgress.coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(CircleShape),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.outlineVariant,
                            )
                        }
                    }
                }
            }
        }

        // Current Deck vs Incoming Deck Cards
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                DeckStageCard(
                    roleLabel = "CURRENT DECK (${activeDeck.deckName.uppercase()})",
                    deck = activeDeck,
                    fallbackTrack = playbackState.currentTrack,
                    fallbackAnalysis = playbackState.currentTrack?.let { analyses[it.id] },
                    highlighted = true,
                    onEditClick = {
                        val t = activeDeck.track ?: playbackState.currentTrack
                        if (t != null) correctionTrack = t
                    },
                )
                DeckStageCard(
                    roleLabel = "INCOMING DECK (${incomingDeck.deckName.uppercase()})",
                    deck = incomingDeck,
                    fallbackTrack = incomingPreviewTrack,
                    fallbackAnalysis = incomingPreviewAnalysis,
                    highlighted = automixState.isMixingTransition,
                    onEditClick = {
                        val t = incomingDeck.track ?: incomingPreviewTrack
                        if (t != null) correctionTrack = t
                    },
                )
            }
        }

        // Start / Pause / Stop / Mix Next Transport Controls
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (!automixState.isActive) {
                    Button(
                        onClick = onStartAutomix,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("start_automix_btn"),
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Start Automix",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = onTogglePlayPause,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("automix_play_pause_btn"),
                        ) {
                            Icon(
                                imageVector = if (playbackState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (playbackState.isPlaying) "Pause" else "Resume", fontWeight = FontWeight.SemiBold)
                        }
                        Button(
                            onClick = onSkipNextMix,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("mix_next_btn"),
                        ) {
                            Icon(Icons.Rounded.SkipNext, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Mix Next", fontWeight = FontWeight.SemiBold)
                        }
                        OutlinedButton(
                            onClick = onStopAutomix,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("stop_automix_btn"),
                        ) {
                            Icon(Icons.Rounded.Stop, contentDescription = "Stop Automix", modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }

        // Strict Compatibility Rejection Alert
        val rejectionReason = automixState.noSuitableTrackReason
        if (!rejectionReason.isNullOrBlank()) {
            item {
                val shape = RoundedCornerShape(14.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp)
                        .clip(shape)
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f))
                        .border(0.75.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f), shape)
                        .clickable(onClick = onOpenSettings)
                        .padding(14.dp)
                        .testTag("automix_no_match_banner"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.WarningAmber,
                        contentDescription = "Strict rule warning",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(22.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "No Compatible Next Track",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        )
                        Text(
                            text = rejectionReason,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = "Adjust",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        // 6-Tile Quick Controls Matrix
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    DjFeatureTile(
                        icon = Icons.Rounded.Speed,
                        title = "BPM Match",
                        subtitle = if (automixConfig.bpmMatchEnabled) "${automixConfig.bpmMin}-${automixConfig.bpmMax}" else "Off",
                        highlighted = automixConfig.bpmMatchEnabled,
                        onClick = onToggleBpmMatch,
                        modifier = Modifier.weight(1f),
                    )
                    DjFeatureTile(
                        icon = Icons.Rounded.Lock,
                        title = "Key Lock",
                        subtitle = if (automixConfig.keyLockEnabled) "Pitch Kept" else "Vinyl",
                        highlighted = automixConfig.keyLockEnabled,
                        onClick = onToggleKeyLock,
                        modifier = Modifier.weight(1f),
                    )
                    DjFeatureTile(
                        icon = Icons.Rounded.LibraryMusic,
                        title = if (automixConfig.strictGenreMatching) "Strict Genre" else "Open Genre",
                        subtitle = automixConfig.genrePreference,
                        highlighted = automixConfig.strictGenreMatching || automixConfig.genrePreference != "All Genres",
                        onClick = onOpenSettings,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    DjFeatureTile(
                        icon = Icons.Rounded.AutoAwesome,
                        title = automixConfig.mode.label,
                        subtitle = "Tap to cycle",
                        highlighted = automixConfig.mode != MixMode.SMART,
                        onClick = onCycleMixMode,
                        modifier = Modifier.weight(1f),
                    )
                    DjFeatureTile(
                        icon = Icons.Rounded.Loop,
                        title = "Crossfade",
                        subtitle = "${automixConfig.transitionDurationSec}s",
                        highlighted = true,
                        onClick = onOpenSettings,
                        modifier = Modifier.weight(1f),
                    )
                    DjFeatureTile(
                        icon = Icons.Rounded.QueueMusic,
                        title = "DJ Queue",
                        subtitle = "${automixState.djQueue.size} upcoming",
                        highlighted = automixState.djQueue.isNotEmpty(),
                        onClick = onOpenDjQueue,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // Offline Audio Analysis Quick Trigger
        item {
            EditorialGroupCard(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                cornerRadius = 14.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Offline Audio Analysis",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        )
                        Text(
                            text = if (automixState.isAnalyzing) {
                                "Analyzing ${automixState.analyzedCount}/${automixState.totalTrackCount} tracks..."
                            } else {
                                "${analyses.size} analyzed tracks ready for mixing"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedButton(
                        onClick = onAnalyzeLibrary,
                        enabled = !automixState.isAnalyzing,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("analyze_library_btn"),
                    ) {
                        Text(if (automixState.isAnalyzing) "Analyzing..." else "Analyze")
                    }
                }
            }
        }
    }

    correctionTrack?.let { tr ->
        val existing = analyses[tr.id]
        ManualBpmKeyDialog(
            track = tr,
            initialBpm = existing?.bpm ?: 120f,
            initialKey = existing?.musicalKey ?: "A min",
            onConfirm = { bpm, key ->
                onCorrectAnalysis(tr, bpm, key)
                correctionTrack = null
            },
            onDismiss = { correctionTrack = null },
        )
    }
}

@Composable
private fun SpectrumWaveformStrip(
    spectrumBands: List<Float>,
    isPlaying: Boolean,
    isMixing: Boolean,
) {
    val gold = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.outlineVariant

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
    ) {
        val barCount = 28
        val gap = 4.dp.toPx()
        val barWidth = ((size.width - gap * (barCount - 1)) / barCount).coerceAtLeast(2.dp.toPx())
        val cy = size.height / 2f

        for (i in 0 until barCount) {
            val bandIdx = (i * 12) / barCount
            val amp = if (isPlaying) {
                spectrumBands.getOrElse(bandIdx) { 0.15f }.coerceIn(0.10f, 1f)
            } else {
                0.16f + 0.12f * ((i % 4) / 3f)
            }
            val halfH = (size.height * 0.45f * amp).coerceAtLeast(4.dp.toPx())
            val x = i * (barWidth + gap)
            val color = when {
                isMixing || amp > 0.55f -> gold
                isPlaying -> gold.copy(alpha = 0.65f)
                else -> muted
            }
            drawRoundRect(
                color = color,
                topLeft = Offset(x, cy - halfH),
                size = Size(barWidth, halfH * 2f),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
            )
        }
    }
}

@Composable
private fun DeckStageCard(
    roleLabel: String,
    deck: DeckState,
    fallbackTrack: Track?,
    fallbackAnalysis: TrackAnalysis?,
    highlighted: Boolean,
    onEditClick: () -> Unit,
) {
    val track = deck.track ?: fallbackTrack
    val bpm = when {
        deck.bpm > 0f -> deck.bpm
        fallbackAnalysis != null -> fallbackAnalysis.bpm
        else -> 0f
    }
    val keyCode = deck.camelotCode.takeIf { it != "--" } ?: fallbackAnalysis?.camelotCode ?: "--"
    val genre = deck.genreLabel.ifBlank { fallbackAnalysis?.subgenre?.ifBlank { track?.genre } ?: "" }

    val shape = RoundedCornerShape(14.dp)
    val borderColor = if (highlighted && track != null) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
    } else MaterialTheme.colorScheme.outlineVariant

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, borderColor, shape)
            .clickable(enabled = track != null, onClick = onEditClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = roleLabel,
                style = MonoBadgeStyle,
                color = if (highlighted && track != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (track != null) {
                Text(
                    text = "${(deck.volume * 100).roundToInt()}% VOL",
                    style = MonoBadgeStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ArtworkImage(
                artworkUri = track?.displayArtwork,
                fallbackUri = track?.contentUri,
                modifier = Modifier.size(56.dp),
                cornerRadius = 10.dp,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track?.title ?: "Standby",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = track?.artist ?: "Waiting for track",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            DeckTelemetryChip("BPM", if (bpm > 0f) "${bpm.roundToInt()}" else "--")
            DeckTelemetryChip("KEY", keyCode)
            DeckTelemetryChip("GENRE", genre.ifBlank { "--" })
        }
    }
}

@Composable
private fun DeckTelemetryChip(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MonoStatStyle.copy(fontSize = 13.sp),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DjFeatureTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    highlighted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 0.75.dp,
                color = if (highlighted) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.outlineVariant,
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(if (highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualBpmKeyDialog(
    track: Track,
    initialBpm: Float,
    initialKey: String,
    onConfirm: (bpm: Float, musicalKey: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var bpmText by remember { mutableStateOf(initialBpm.toInt().toString()) }
    var selectedKey by remember { mutableStateOf(initialKey) }
    var keyExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Correct BPM & Key") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "${track.title} · ${track.artist}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = bpmText,
                    onValueChange = { input -> bpmText = input.filter { it.isDigit() || it == '.' } },
                    label = { Text("Tempo (BPM)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                ExposedDropdownMenuBox(
                    expanded = keyExpanded,
                    onExpandedChange = { keyExpanded = !keyExpanded },
                ) {
                    OutlinedTextField(
                        value = "$selectedKey (${HarmonicCompatibility.camelotForKey(selectedKey)})",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Musical Key (Camelot)") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = keyExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded = keyExpanded,
                        onDismissRequest = { keyExpanded = false },
                    ) {
                        HarmonicCompatibility.ALL_KEYS.forEach { k ->
                            DropdownMenuItem(
                                text = { Text("$k (${HarmonicCompatibility.camelotForKey(k)})") },
                                onClick = {
                                    selectedKey = k
                                    keyExpanded = false
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val bpm = bpmText.toFloatOrNull()?.coerceIn(45f, 220f) ?: initialBpm
                    onConfirm(bpm, selectedKey)
                },
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
