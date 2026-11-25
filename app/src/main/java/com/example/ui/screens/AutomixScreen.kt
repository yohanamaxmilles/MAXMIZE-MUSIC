package com.example.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.dj.HarmonicCompatibility
import com.example.data.model.AutomixConfig
import com.example.data.model.AutomixTechnique
import com.example.data.model.DjOperatingMode
import com.example.data.model.AutomixUiState
import com.example.data.model.DeckState
import com.example.data.model.MixMode
import com.example.data.model.PlaybackUiState
import com.example.data.model.Track
import com.example.data.model.TrackAnalysis
import com.example.player.RealtimeSpectrumBus
import com.example.ui.components.ArtworkImage
import com.example.ui.components.EditorialGroupCard
import com.example.ui.theme.BadgeGoldBg
import com.example.ui.theme.BadgeGoldFg
import com.example.ui.theme.MonoBadgeStyle
import com.example.ui.theme.MonoTimecodeStyle

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
    onSelectTechnique: (AutomixTechnique) -> Unit,
    onAnalyzeLibrary: () -> Unit,
    onCorrectAnalysis: (Track, Float, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var correctionTrack by remember { mutableStateOf<Track?>(null) }
    val spectrumBands by RealtimeSpectrumBus.bands.collectAsStateWithLifecycle()

    val activeTrack = if (automixState.activeDeckIndex == 0) {
        automixState.deckA.track ?: playbackState.currentTrack
    } else {
        automixState.deckB.track ?: playbackState.currentTrack
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("automix_screen"),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        // Top Header
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
                    text = if (automixState.isActive) "DJ session in progress" else if (automixConfig.operatingMode == DjOperatingMode.INTELLIGENT_DJ) "Intelligent DJ" else "Automix",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Row {
                    IconButton(
                        onClick = onOpenDjQueue,
                        modifier = Modifier.testTag("automix_queue_btn"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.QueueMusic,
                            contentDescription = "DJ Queue & History",
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.testTag("automix_settings_btn"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = "Automix Settings",
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                }
            }
        }

        // Turntable Disc & Live Spectrum Visualizer (Zero Gradients)
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                VinylTurntableVisualizer(
                    isActive = automixState.isActive,
                    isPlaying = playbackState.isPlaying || automixState.isMixingTransition,
                    isMixing = automixState.isMixingTransition,
                    spectrumBands = spectrumBands,
                    onCenterClick = {
                        if (automixState.isActive) onTogglePlayPause() else onStartAutomix()
                    },
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Active DJ Profile Badge
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(0.75.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                        .clickable(onClick = onOpenSettings)
                        .padding(horizontal = 12.dp, vertical = 5.dp)
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
                        text = "${automixState.activeProfileName} · ${automixConfig.harmonicMixLevel.label} Key · ${automixConfig.energyProgression.label}",
                        style = MonoBadgeStyle,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = if (automixState.isActive) "Mixing your tracks..." else if (automixConfig.operatingMode == DjOperatingMode.INTELLIGENT_DJ) "Intelligent DJ" else "Automix",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (automixState.isActive) {
                        automixState.statusText
                    } else {
                        if (automixConfig.operatingMode == DjOperatingMode.INTELLIGENT_DJ) "Your DJ reads the musical flow, manages energy, chooses compatible tracks and performs live transitions." else "Choose a mixing technique. MAXMIZE matches tracks using local BPM, key, genre and energy evidence."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )

                if (automixConfig.operatingMode == DjOperatingMode.AUTOMIX) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "MIXING TECHNIQUE",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AutomixTechnique.entries.take(3).forEach { technique ->
                            TechniqueChoice(
                                technique = technique,
                                selected = automixConfig.technique == technique,
                                onClick = { onSelectTechnique(technique) },
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AutomixTechnique.entries.drop(3).forEach { technique ->
                            TechniqueChoice(
                                technique = technique,
                                selected = automixConfig.technique == technique,
                                onClick = { onSelectTechnique(technique) },
                            )
                        }
                    }
                }

                if (automixState.isMixingTransition) {
                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { automixState.transitionProgress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth(0.75f)
                            .height(4.dp)
                            .clip(CircleShape),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }

        // Primary Gold CTA / Stop Control
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (!automixState.isActive) {
                    Button(
                        onClick = onStartAutomix,
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
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
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedButton(
                            onClick = onStopAutomix,
                            shape = CircleShape,
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("stop_automix_btn"),
                        ) {
                            Icon(Icons.Rounded.Stop, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Stop Automix", fontWeight = FontWeight.SemiBold)
                        }
                        Button(
                            onClick = onSkipNextMix,
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                            modifier = Modifier
                                .weight(0.85f)
                                .height(50.dp)
                                .testTag("mix_next_btn"),
                        ) {
                            Icon(Icons.Rounded.SkipNext, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Mix Next", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        // Strict Compatibility Rejection Alert (honest feedback instead of bad forced match)
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
                            color = MaterialTheme.colorScheme.onBackground,
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

        // Active Track Card (when Automix is running)
        if (automixState.isActive && activeTrack != null) {
            item {
                val activeAnalysis = analyses[activeTrack.id]
                EditorialGroupCard(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    cornerRadius = 14.dp,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { correctionTrack = activeTrack }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ArtworkImage(
                            artworkUri = activeTrack.customArtworkUri ?: activeTrack.artworkUri,
                            fallbackUri = activeTrack.contentUri,
                            modifier = Modifier.size(52.dp),
                            cornerRadius = 10.dp,
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = activeTrack.title,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = activeTrack.artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (activeAnalysis != null) {
                                Text(
                                    text = "${activeAnalysis.bpm.toInt()} BPM · ${activeAnalysis.camelotCode} (${activeAnalysis.musicalKey}) · ${activeAnalysis.subgenre.ifBlank { activeTrack.genre }}",
                                    style = MonoBadgeStyle,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 3.dp),
                                )
                            }
                        }
                        if (automixState.djQueue.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(BadgeGoldBg)
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            ) {
                                Text(
                                    text = "${automixState.currentCompatibilityScore}% Match",
                                    style = MonoBadgeStyle,
                                    color = BadgeGoldFg,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Dual-Deck Live Telemetry
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                DeckTelemetryCard(
                    deck = automixState.deckA,
                    onEditClick = { automixState.deckA.track?.let { correctionTrack = it } },
                    modifier = Modifier.weight(1f),
                )
                DeckTelemetryCard(
                    deck = automixState.deckB,
                    onEditClick = { automixState.deckB.track?.let { correctionTrack = it } },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // 6-Tile DJ Feature Matrix
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    DjFeatureTile(
                        icon = Icons.Rounded.Speed,
                        title = "BPM Match",
                        subtitle = if (automixConfig.bpmMatchEnabled) "On · ${automixConfig.bpmMin}-${automixConfig.bpmMax}" else "Off",
                        highlighted = automixConfig.bpmMatchEnabled,
                        onClick = onToggleBpmMatch,
                        modifier = Modifier.weight(1f),
                    )
                    DjFeatureTile(
                        icon = Icons.Rounded.Lock,
                        title = "Key Lock",
                        subtitle = if (automixConfig.keyLockEnabled) "Pitch Kept" else "Vinyl Pitch",
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
                        subtitle = "${automixConfig.transitionDurationSec}s · ${automixConfig.transitionStyle.label}",
                        highlighted = true,
                        onClick = onOpenSettings,
                        modifier = Modifier.weight(1f),
                    )
                    DjFeatureTile(
                        icon = Icons.Rounded.QueueMusic,
                        title = "Smart Queue",
                        subtitle = "${automixState.djQueue.size} upcoming",
                        highlighted = automixState.djQueue.isNotEmpty(),
                        onClick = onOpenDjQueue,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // Offline DSP Analysis Status Card
        item {
            EditorialGroupCard(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                cornerRadius = 14.dp,
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
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
                                    "Analyzing BPM, Camelot Key & Genre (${automixState.analyzedCount}/${automixState.totalTrackCount})"
                                } else {
                                    "${analyses.size} analyzed tracks stored locally"
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
                            Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (automixState.isAnalyzing) "Analyzing..." else "Analyze")
                        }
                    }
                    if (automixState.isAnalyzing && automixState.totalTrackCount > 0) {
                        LinearProgressIndicator(
                            progress = {
                                (automixState.analyzedCount.toFloat() / automixState.totalTrackCount.coerceAtLeast(1))
                                    .coerceIn(0f, 1f)
                            },
                            modifier = Modifier.fillMaxWidth().height(3.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.outline,
                        )
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
private fun VinylTurntableVisualizer(
    isActive: Boolean,
    isPlaying: Boolean,
    isMixing: Boolean,
    spectrumBands: List<Float>,
    onCenterClick: () -> Unit,
) {
    val transition = rememberInfiniteTransition(label = "turntableSpin")
    val spinAngle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "discRotation",
    )

    val gold = MaterialTheme.colorScheme.primary
    val discSurface = MaterialTheme.colorScheme.surface
    val grooveColor = MaterialTheme.colorScheme.outline

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(210.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cy = size.height / 2f
            val cx = size.width / 2f
            val discRadius = 86.dp.toPx()

            // Live 12-band PCM frequency bars driven directly by ExoPlayer decoded PCM buffer
            val barCount = 12
            val barWidth = 3.dp.toPx()
            val barSpacing = 5.5.dp.toPx()
            val maxBarHalfH = 44.dp.toPx()

            for (side in listOf(-1, 1)) {
                for (i in 0 until barCount) {
                    val distFromDisc = discRadius + 12.dp.toPx() + i * barSpacing
                    val x = cx + side * distFromDisc
                    if (x < 6f || x > size.width - 6f) continue

                    val bandIdx = if (side == -1) (barCount - 1 - i) else i
                    val amp = if (isPlaying) {
                        spectrumBands.getOrElse(bandIdx) { 0.08f }.coerceIn(0.06f, 1.0f)
                    } else {
                        0.12f + 0.06f * ((i % 3) / 2f)
                    }
                    val envelope = 1f - (i.toFloat() / barCount) * 0.42f
                    val halfH = (maxBarHalfH * envelope * amp).coerceAtLeast(3.dp.toPx())
                    drawRoundRect(
                        color = if (isMixing || amp > 0.55f) gold else gold.copy(alpha = 0.68f),
                        topLeft = Offset(x - barWidth / 2f, cy - halfH),
                        size = Size(barWidth, halfH * 2f),
                        cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
                    )
                }
            }

            // Matte Black Vinyl Disc + Subtle Grooves
            drawCircle(
                color = if (isActive) gold else grooveColor,
                radius = discRadius + 2.dp.toPx(),
                center = Offset(cx, cy),
                style = Stroke(width = if (isActive) 2.dp.toPx() else 1.2.dp.toPx()),
            )
            drawCircle(
                color = discSurface,
                radius = discRadius,
                center = Offset(cx, cy),
            )

            val angle = if (isPlaying) spinAngle else 25f
            rotate(degrees = angle, pivot = Offset(cx, cy)) {
                for (rFactor in listOf(0.84f, 0.72f, 0.60f, 0.48f)) {
                    drawCircle(
                        color = grooveColor,
                        radius = discRadius * rFactor,
                        center = Offset(cx, cy),
                        style = Stroke(width = 1.dp.toPx()),
                    )
                }
                drawCircle(
                    color = gold,
                    radius = 3.5.dp.toPx(),
                    center = Offset(cx, cy - discRadius * 0.74f),
                )
            }

            // Gold Inner Hub Ring
            drawCircle(
                color = gold.copy(alpha = 0.25f),
                radius = 30.dp.toPx(),
                center = Offset(cx, cy),
            )
            drawCircle(
                color = gold,
                radius = 27.dp.toPx(),
                center = Offset(cx, cy),
                style = Stroke(width = 2.dp.toPx()),
            )
        }

        // Interactive Center Play / Pause Spindle Button
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .clickable(onClick = onCenterClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isActive && isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = "Toggle Automix playback",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

@Composable
private fun DeckTelemetryCard(
    deck: DeckState,
    onEditClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    val borderColor = if (deck.isActiveForeground && deck.track != null) {
        MaterialTheme.colorScheme.primary
    } else MaterialTheme.colorScheme.outlineVariant

    Column(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, borderColor, shape)
            .clickable(enabled = deck.track != null, onClick = onEditClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = deck.deckName.uppercase(),
                style = MonoBadgeStyle,
                color = if (deck.isActiveForeground) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = if (deck.bpm > 0f) "${deck.bpm.toInt()} BPM · ${deck.camelotCode}" else "IDLE",
                style = MonoTimecodeStyle.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = deck.track?.title ?: "Standby",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = if (deck.genreLabel.isNotBlank() && deck.track != null) {
                "${deck.track.artist} · ${deck.genreLabel}"
            } else {
                deck.track?.artist ?: "Waiting for next track"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            .padding(vertical = 14.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (highlighted) BadgeGoldBg else MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (highlighted) BadgeGoldFg else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(19.dp),
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


@Composable
private fun TechniqueChoice(
    technique: AutomixTechnique,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val label = when (technique) {
        AutomixTechnique.BEAT_SYNC -> "Beat Sync"
        AutomixTechnique.HARMONIC -> "Harmonic"
        AutomixTechnique.PHRASE_MATCH -> "Phrase"
        AutomixTechnique.ENERGY_FLOW -> "Energy"
        AutomixTechnique.GENRE_MATCH -> "Genre"
        AutomixTechnique.HUMAN_STYLE -> "Human Style"
    }
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.testTag("technique_${technique.name.lowercase()}"),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                else MaterialTheme.colorScheme.surface,
            contentColor = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}
