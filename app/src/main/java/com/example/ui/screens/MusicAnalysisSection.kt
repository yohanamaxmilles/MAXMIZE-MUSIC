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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.AutomixUiState
import com.example.data.model.Track
import com.example.data.model.TrackAnalysis
import com.example.ui.components.ArtworkImage
import com.example.ui.components.EditorialChip
import com.example.ui.components.EditorialGroupCard
import com.example.ui.theme.BadgeGoldBg
import com.example.ui.theme.BadgeGoldFg
import com.example.ui.theme.MonoBadgeStyle
import com.example.ui.theme.MonoStatStyle
import com.example.ui.theme.MonoTimecodeStyle
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun MusicAnalysisSection(
    tracks: List<Track>,
    analyses: Map<String, TrackAnalysis>,
    automixState: AutomixUiState,
    onAnalyzeLibrary: () -> Unit,
    onCorrectAnalysis: (Track, Float, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var filterMode by remember { mutableIntStateOf(0) } // 0 = Analyzed, 1 = Low Confidence, 2 = Awaiting
    var correctionTrack by remember { mutableStateOf<Track?>(null) }

    val stats = remember(tracks, analyses) {
        val analyzedList = tracks.mapNotNull { t -> analyses[t.id]?.let { t to it } }
        val awaiting = tracks.filter { !analyses.containsKey(it.id) }
        val lowConfidence = analyzedList.filter { (_, a) ->
            (a.bpmConfidence < 0.45f || a.keyConfidence < 0.4f) && !a.userCorrected
        }
        val avgBpm = analyzedList.map { it.second.bpm }.takeIf { it.isNotEmpty() }?.average()?.toFloat() ?: 0f
        val minBpm = analyzedList.minOfOrNull { it.second.bpm } ?: 0f
        val maxBpm = analyzedList.maxOfOrNull { it.second.bpm } ?: 0f
        val avgEnergy = analyzedList.map { it.second.energy }.takeIf { it.isNotEmpty() }?.average()?.toFloat() ?: 0f
        val avgLufs = analyzedList.map { it.second.loudnessLufs }.takeIf { it.isNotEmpty() }?.average()?.toFloat() ?: 0f

        val bpmBuckets = listOf(
            "<90" to analyzedList.count { it.second.bpm < 90f },
            "90-110" to analyzedList.count { it.second.bpm in 90f..109.9f },
            "110-125" to analyzedList.count { it.second.bpm in 110f..124.9f },
            "125-140" to analyzedList.count { it.second.bpm in 125f..139.9f },
            "140+" to analyzedList.count { it.second.bpm >= 140f },
        )

        val topKeys = analyzedList
            .groupBy { it.second.camelotCode }
            .mapValues { it.value.size }
            .entries
            .sortedByDescending { it.value }
            .take(6)

        val topGenres = analyzedList
            .groupBy { it.second.subgenre.ifBlank { it.second.primaryGenre } }
            .mapValues { it.value.size }
            .entries
            .sortedByDescending { it.value }
            .take(6)

        AnalysisSummary(
            analyzedPairs = analyzedList,
            awaitingTracks = awaiting,
            lowConfidencePairs = lowConfidence,
            avgBpm = avgBpm,
            minBpm = minBpm,
            maxBpm = maxBpm,
            avgEnergy = avgEnergy,
            avgLufs = avgLufs,
            bpmBuckets = bpmBuckets,
            topKeys = topKeys.map { it.key to it.value },
            topGenres = topGenres.map { it.key to it.value },
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("dj_analysis_section"),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Overview Progress Card
        item {
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "DSP Audio Analysis",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            )
                            Text(
                                text = if (automixState.isAnalyzing) {
                                    "Analyzing ${automixState.analyzedCount} of ${automixState.totalTrackCount} tracks..."
                                } else {
                                    "${stats.analyzedPairs.size} analyzed · ${stats.awaitingTracks.size} awaiting · ${stats.lowConfidencePairs.size} review"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Button(
                            onClick = onAnalyzeLibrary,
                            enabled = !automixState.isAnalyzing && tracks.isNotEmpty(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                            modifier = Modifier.testTag("analysis_run_btn"),
                        ) {
                            Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (automixState.isAnalyzing) "Analyzing" else "Analyze")
                        }
                    }

                    val progress = if (tracks.isNotEmpty()) {
                        (stats.analyzedPairs.size.toFloat() / tracks.size.toFloat()).coerceIn(0f, 1f)
                    } else 0f
                    LinearProgressIndicator(
                        progress = {
                            if (automixState.isAnalyzing && automixState.totalTrackCount > 0) {
                                (automixState.analyzedCount.toFloat() / automixState.totalTrackCount).coerceIn(0f, 1f)
                            } else progress
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(CircleShape),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.outlineVariant,
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        AnalysisStatBlock("AVG BPM", if (stats.avgBpm > 0f) "${stats.avgBpm.roundToInt()}" else "--")
                        AnalysisStatBlock(
                            "RANGE",
                            if (stats.maxBpm > 0f) "${stats.minBpm.roundToInt()}-${stats.maxBpm.roundToInt()}" else "--",
                        )
                        AnalysisStatBlock("ENERGY", if (stats.analyzedPairs.isNotEmpty()) "${(stats.avgEnergy * 100).roundToInt()}%" else "--")
                        AnalysisStatBlock("LOUDNESS", if (stats.analyzedPairs.isNotEmpty()) String.format(Locale.US, "%.1f", stats.avgLufs) else "--")
                    }
                }
            }
        }

        // BPM Distribution & Top Keys / Genres
        if (stats.analyzedPairs.isNotEmpty()) {
            item {
                EditorialGroupCard(cornerRadius = 14.dp) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = "BPM Distribution",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        )
                        val maxBucket = (stats.bpmBuckets.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)
                        stats.bpmBuckets.forEach { (bucket, count) ->
                            DistributionBarRow(label = bucket, count = count, maxCount = maxBucket)
                        }

                        if (stats.topKeys.isNotEmpty()) {
                            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                            Text(
                                text = "Camelot Key Breakdown",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            )
                            val maxKey = (stats.topKeys.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)
                            stats.topKeys.forEach { (key, count) ->
                                DistributionBarRow(label = key, count = count, maxCount = maxKey)
                            }
                        }

                        if (stats.topGenres.isNotEmpty()) {
                            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                            Text(
                                text = "Genre Distribution",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            )
                            val maxGenre = (stats.topGenres.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)
                            stats.topGenres.forEach { (genre, count) ->
                                DistributionBarRow(label = genre, count = count, maxCount = maxGenre)
                            }
                        }
                    }
                }
            }
        }

        // Track Inspector Filter Chips
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EditorialChip(
                    label = "Analyzed (${stats.analyzedPairs.size})",
                    selected = filterMode == 0,
                    onClick = { filterMode = 0 },
                )
                EditorialChip(
                    label = "Review (${stats.lowConfidencePairs.size})",
                    selected = filterMode == 1,
                    onClick = { filterMode = 1 },
                )
                EditorialChip(
                    label = "Awaiting (${stats.awaitingTracks.size})",
                    selected = filterMode == 2,
                    onClick = { filterMode = 2 },
                )
            }
        }

        when (filterMode) {
            0 -> {
                items(stats.analyzedPairs.take(80), key = { "an_${it.first.id}" }) { (track, analysis) ->
                    AnalysisTrackRow(
                        track = track,
                        analysis = analysis,
                        lowConfidence = (analysis.bpmConfidence < 0.45f || analysis.keyConfidence < 0.4f) && !analysis.userCorrected,
                        onEdit = { correctionTrack = track },
                    )
                }
            }
            1 -> {
                if (stats.lowConfidencePairs.isEmpty()) {
                    item {
                        Text(
                            text = "All analyzed tracks passed confidence checks.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                    }
                } else {
                    items(stats.lowConfidencePairs, key = { "lc_${it.first.id}" }) { (track, analysis) ->
                        AnalysisTrackRow(
                            track = track,
                            analysis = analysis,
                            lowConfidence = true,
                            onEdit = { correctionTrack = track },
                        )
                    }
                }
            }
            else -> {
                if (stats.awaitingTracks.isEmpty()) {
                    item {
                        Text(
                            text = "Every local track in your library has been analyzed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                    }
                } else {
                    items(stats.awaitingTracks.take(80), key = { "aw_${it.id}" }) { track ->
                        AnalysisTrackRow(
                            track = track,
                            analysis = null,
                            lowConfidence = false,
                            onEdit = { correctionTrack = track },
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

private data class AnalysisSummary(
    val analyzedPairs: List<Pair<Track, TrackAnalysis>>,
    val awaitingTracks: List<Track>,
    val lowConfidencePairs: List<Pair<Track, TrackAnalysis>>,
    val avgBpm: Float,
    val minBpm: Float,
    val maxBpm: Float,
    val avgEnergy: Float,
    val avgLufs: Float,
    val bpmBuckets: List<Pair<String, Int>>,
    val topKeys: List<Pair<String, Int>>,
    val topGenres: List<Pair<String, Int>>,
)

@Composable
private fun AnalysisStatBlock(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MonoBadgeStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MonoStatStyle,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun DistributionBarRow(
    label: String,
    count: Int,
    maxCount: Int,
) {
    val fraction = (count.toFloat() / maxCount.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(86.dp),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
        Text(
            text = "$count",
            style = MonoTimecodeStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AnalysisTrackRow(
    track: Track,
    analysis: TrackAnalysis?,
    lowConfidence: Boolean,
    onEdit: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(0.75.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(onClick = onEdit)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtworkImage(
            artworkUri = track.displayArtwork,
            fallbackUri = track.contentUri,
            modifier = Modifier.size(42.dp),
            cornerRadius = 8.dp,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (analysis != null) {
                    "${analysis.bpm.roundToInt()} BPM · ${analysis.camelotCode} (${analysis.musicalKey}) · ${analysis.subgenre.ifBlank { analysis.primaryGenre }}"
                } else {
                    "${track.artist} · Awaiting analysis"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (lowConfidence) {
            Icon(
                imageVector = Icons.Rounded.WarningAmber,
                contentDescription = "Low confidence",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(end = 6.dp).size(16.dp),
            )
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(BadgeGoldBg)
                .padding(horizontal = 7.dp, vertical = 4.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Edit,
                contentDescription = "Calibrate BPM and Key",
                tint = BadgeGoldFg,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}
