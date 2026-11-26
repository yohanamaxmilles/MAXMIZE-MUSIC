package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.AutomixConfig
import com.example.data.model.AutomixUiState
import com.example.data.model.PlaybackUiState
import com.example.data.model.TrackAnalysis
import com.example.ui.components.ArtworkImage
import kotlin.math.roundToInt

private val StudioCard = Color(0xFF121B25)
private val StudioMuted = Color(0xFF91A0AF)
private val StudioAccent = Color(0xFFE2F36B)

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
    val nextTrack = incomingDeck.track ?: automixState.djQueue.firstOrNull()?.track
    val currentAnalysis = currentTrack?.let { analyses[it.id] }
    val bpm = when {
        activeDeck.bpm > 0f -> activeDeck.bpm.roundToInt().toString()
        currentAnalysis != null -> currentAnalysis.bpm.roundToInt().toString()
        else -> "--"
    }
    val key = activeDeck.camelotCode.takeIf { it != "--" } ?: currentAnalysis?.camelotCode ?: "--"

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 18.dp).testTag("dj_home_section"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("YOUR SESSION", style = MaterialTheme.typography.labelSmall, color = StudioMuted, fontWeight = FontWeight.Bold)
                Text(if (automixState.isActive) "Mix in progress" else "Ready when you are", style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold)
            }
            Text(if (automixState.isActive) "ACTIVE" else "READY", style = MaterialTheme.typography.labelMedium, color = if (automixState.isActive) Color(0xFF6BE0AE) else StudioAccent, fontWeight = FontWeight.Bold)
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = StudioCard),
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    ArtworkImage(artworkUri = currentTrack?.displayArtwork, fallbackUri = currentTrack?.contentUri, modifier = Modifier.size(82.dp), cornerRadius = 16.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(if (automixState.isActive) "NOW PLAYING · ${activeDeck.deckName}" else "CURRENT TRACK", style = MaterialTheme.typography.labelSmall, color = StudioAccent, fontWeight = FontWeight.Bold)
                        Text(currentTrack?.title ?: "No track loaded", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(currentTrack?.artist ?: "Choose a track to begin your session", style = MaterialTheme.typography.bodySmall, color = StudioMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                Row(
                    Modifier.fillMaxWidth().background(Color(0xFF0B121A), RoundedCornerShape(16.dp)).padding(vertical = 13.dp, horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    StudioMetric("BPM", bpm)
                    StudioMetric("KEY", key)
                    StudioMetric("QUEUE", automixState.djQueue.size.toString())
                    StudioMetric("LIBRARY", totalLibraryTracks.toString())
                }

                if (!automixState.isActive) {
                    Button(onClick = onStartAutomix, modifier = Modifier.fillMaxWidth().height(52.dp).testTag("dj_home_start_btn"), shape = RoundedCornerShape(15.dp), colors = ButtonDefaults.buttonColors(containerColor = StudioAccent, contentColor = Color(0xFF10160B))) {
                        Text("START AUTOMIX", fontWeight = FontWeight.ExtraBold)
                    }
                    OutlinedButton(onClick = { onNavigateTab(DjSectionTab.AUTOMIX) }, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(15.dp)) {
                        Text("OPEN DJ DECKS")
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        Button(onClick = onTogglePlayPause, modifier = Modifier.weight(1f).height(50.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = StudioAccent, contentColor = Color(0xFF10160B))) {
                            Text(if (playbackState.isPlaying) "PAUSE" else "RESUME", fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(onClick = onSkipNextMix, modifier = Modifier.weight(1f).height(50.dp), shape = RoundedCornerShape(14.dp)) { Text("NEXT MIX") }
                        OutlinedButton(onClick = onStopAutomix, modifier = Modifier.height(50.dp), shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(horizontal = 14.dp)) { Text("STOP") }
                    }
                }
            }
        }

        Text("UP NEXT", style = MaterialTheme.typography.labelMedium, color = StudioMuted, fontWeight = FontWeight.Bold)
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = StudioCard)) {
            Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ArtworkImage(artworkUri = nextTrack?.displayArtwork, fallbackUri = nextTrack?.contentUri, modifier = Modifier.size(58.dp), cornerRadius = 12.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(nextTrack?.title ?: "Queue is empty", style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(nextTrack?.artist ?: "Add tracks to prepare your next transition", style = MaterialTheme.typography.bodySmall, color = StudioMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(automixState.djQueue.size.toString(), style = MaterialTheme.typography.titleMedium, color = StudioAccent, fontWeight = FontWeight.Bold)
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StudioAction("QUEUE", "Manage tracks", Modifier.weight(1f)) { onNavigateTab(DjSectionTab.QUEUE) }
            StudioAction("ANALYSIS", "${analyses.size} analysed", Modifier.weight(1f)) { onNavigateTab(DjSectionTab.ANALYSIS) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StudioAction("RHYTHM AI", "Drum detection", Modifier.weight(1f)) { onNavigateTab(DjSectionTab.RHYTHM) }
            StudioAction("PROFILES", automixState.activeProfileName, Modifier.weight(1f)) { onNavigateTab(DjSectionTab.PROFILES) }
        }

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF101720))) {
            Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("LIBRARY ANALYSIS", style = MaterialTheme.typography.labelMedium, color = Color.White, fontWeight = FontWeight.Bold)
                    Text(if (automixState.isAnalyzing) "Analysing ${automixState.analyzedCount}/${automixState.totalTrackCount} tracks" else "${analyses.size} of $totalLibraryTracks tracks analysed", style = MaterialTheme.typography.bodySmall, color = StudioMuted)
                }
                OutlinedButton(onClick = onAnalyzeLibrary, enabled = !automixState.isAnalyzing, shape = RoundedCornerShape(12.dp)) {
                    Text(if (automixState.isAnalyzing) "WORKING" else "ANALYSE")
                }
            }
        }
    }
}

@Composable
private fun StudioMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = StudioMuted, fontWeight = FontWeight.Bold)
        Text(value, style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun StudioAction(title: String, subtitle: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(modifier = modifier, onClick = onClick, shape = RoundedCornerShape(17.dp), colors = CardDefaults.cardColors(containerColor = StudioCard)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = StudioAccent, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = StudioMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
