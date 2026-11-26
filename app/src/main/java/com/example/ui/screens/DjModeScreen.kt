package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.db.GenreRuleOverrideEntity
import com.example.data.model.AutomixConfig
import com.example.data.model.AutomixUiState
import com.example.data.model.DjProfile
import com.example.data.model.GenreSummary
import com.example.data.model.MixHistoryEntry
import com.example.data.model.PlaybackUiState
import com.example.data.model.Playlist
import com.example.data.model.Track
import com.example.data.model.TrackAnalysis
import com.example.ui.components.MaxmizeTopHeader
import com.example.ui.theme.MonoBadgeStyle

enum class DjSectionTab(val label: String, val testTag: String) {
    HOME("Studio", "dj_tab_home"),
    AUTOMIX("Automix", "dj_tab_automix"),
    QUEUE("Queue", "dj_tab_queue"),
    PROFILES("Profiles", "dj_tab_profiles"),
    ANALYSIS("Analysis", "dj_tab_analysis"),
    RHYTHM("Rhythm AI", "dj_tab_rhythm"),
    SETTINGS("Settings", "dj_tab_settings"),
}

@Composable
fun DjModeScreen(
    selectedTab: DjSectionTab,
    onSelectTab: (DjSectionTab) -> Unit,
    automixState: AutomixUiState,
    automixConfig: AutomixConfig,
    playbackState: PlaybackUiState,
    tracks: List<Track>,
    analyses: Map<String, TrackAnalysis>,
    mixHistory: List<MixHistoryEntry>,
    genres: List<GenreSummary>,
    playlists: List<Playlist>,
    profiles: List<DjProfile>,
    genreRules: List<GenreRuleOverrideEntity>,
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit,
    onStartAutomix: () -> Unit,
    onStopAutomix: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNextMix: () -> Unit,
    onToggleBpmMatch: () -> Unit,
    onToggleKeyLock: () -> Unit,
    onCycleMixMode: () -> Unit,
    onAnalyzeLibrary: () -> Unit,
    onCorrectAnalysis: (Track, Float, String) -> Unit,
    onPlayDjTrackNow: (String) -> Unit,
    onMoveDjQueueItem: (Int, Int) -> Unit,
    onRemoveDjQueueItem: (Int) -> Unit,
    onRecomputeQueue: () -> Unit,
    onClearHistory: () -> Unit,
    onUpdateConfig: ((AutomixConfig) -> AutomixConfig) -> Unit,
    onApplyProfile: (DjProfile) -> Unit,
    onSaveCustomProfile: (String, String) -> Unit,
    onDeleteCustomProfile: (String) -> Unit,
    onUpsertGenreRule: (String, String, Boolean) -> Unit,
    onDeleteGenreRule: (String, String) -> Unit,
    onPreviewVoice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (selectedTab != DjSectionTab.HOME) {
        BackHandler { onSelectTab(DjSectionTab.HOME) }
    }

    var moreMenuExpanded by remember { mutableStateOf(false) }
    val primaryTabs = listOf(DjSectionTab.HOME, DjSectionTab.AUTOMIX, DjSectionTab.QUEUE, DjSectionTab.ANALYSIS)
    val moreTabs = listOf(DjSectionTab.PROFILES, DjSectionTab.RHYTHM, DjSectionTab.SETTINGS)

    Column(
        modifier = modifier.fillMaxSize().background(Color(0xFF090D12)).testTag("dj_mode_screen"),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().background(Color(0xFF0D131B))
                .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("MAXMIZE", style = MaterialTheme.typography.labelSmall, color = Color(0xFF9AA8B8), fontWeight = FontWeight.Bold)
                    Text("DJ STUDIO", style = MaterialTheme.typography.headlineSmall, color = Color(0xFFF4F7FA), fontWeight = FontWeight.ExtraBold)
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.clip(RoundedCornerShape(50)).background(if (automixState.isActive) Color(0xFF173B32) else Color(0xFF202A35)).padding(horizontal = 11.dp, vertical = 6.dp)) {
                        Text(if (automixState.isActive) "● LIVE SESSION" else "● STANDBY", style = MaterialTheme.typography.labelSmall, color = if (automixState.isActive) Color(0xFF6BE0AE) else Color(0xFFAAB7C5), fontWeight = FontWeight.Bold)
                    }
                    Text(if (automixState.isActive) automixState.activeProfileName else "Performance workspace", style = MaterialTheme.typography.labelSmall, color = Color(0xFF8997A7))
                }
            }
            Text("Your mix. Your control.", style = MaterialTheme.typography.bodySmall, color = Color(0xFF8997A7))
        }

        LazyRow(
            modifier = Modifier.fillMaxWidth().background(Color(0xFF0D131B)).padding(bottom = 12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(primaryTabs, key = { it.name }) { tab ->
                DjNavigationPill(tab.label, selectedTab == tab, tab.testTag) { onSelectTab(tab) }
            }
            item {
                Box {
                    DjNavigationPill("More  ⌄", selectedTab in moreTabs || moreMenuExpanded, "dj_tab_more") { moreMenuExpanded = true }
                    DropdownMenu(expanded = moreMenuExpanded, onDismissRequest = { moreMenuExpanded = false }) {
                        moreTabs.forEach { tab ->
                            DropdownMenuItem(text = { Text(tab.label) }, onClick = { moreMenuExpanded = false; onSelectTab(tab) })
                        }
                    }
                }
            }
        }
        HorizontalDivider(color = Color(0xFF202B37), thickness = 1.dp)
        Box(Modifier.weight(1f).fillMaxWidth().background(Color(0xFF090D12))) {
            when (selectedTab) {
                DjSectionTab.HOME -> DjHomeSection(
                    automixState, automixConfig, playbackState, analyses, tracks.size,
                    onStartAutomix, onStopAutomix, onTogglePlayPause, onSkipNextMix, onAnalyzeLibrary, onSelectTab,
                )
                DjSectionTab.AUTOMIX -> AutomixScreen(
                    automixState = automixState, automixConfig = automixConfig, playbackState = playbackState,
                    analyses = analyses, onBack = { onSelectTab(DjSectionTab.HOME) },
                    onOpenSettings = { onSelectTab(DjSectionTab.SETTINGS) }, onOpenDjQueue = { onSelectTab(DjSectionTab.QUEUE) },
                    onStartAutomix = onStartAutomix, onStopAutomix = onStopAutomix, onSkipNextMix = onSkipNextMix,
                    onTogglePlayPause = onTogglePlayPause, onToggleBpmMatch = onToggleBpmMatch, onToggleKeyLock = onToggleKeyLock,
                    onCycleMixMode = onCycleMixMode, onAnalyzeLibrary = onAnalyzeLibrary, onCorrectAnalysis = onCorrectAnalysis, embeddedInHub = true,
                )
                DjSectionTab.QUEUE -> DjQueueScreen(
                    automixState = automixState, playbackState = playbackState, analyses = analyses, mixHistory = mixHistory,
                    onBack = { onSelectTab(DjSectionTab.HOME) }, onOpenSearch = onOpenSearch, onPlayDjTrackNow = onPlayDjTrackNow,
                    onMoveDjQueueItem = onMoveDjQueueItem, onRemoveDjQueueItem = onRemoveDjQueueItem, onRecomputeQueue = onRecomputeQueue,
                    onStopAutomix = onStopAutomix, onClearHistory = onClearHistory, embeddedInHub = true,
                )
                DjSectionTab.PROFILES -> DjProfilesSection(config = automixConfig, profiles = profiles, onApplyProfile = onApplyProfile, onSaveCustomProfile = onSaveCustomProfile, onDeleteCustomProfile = onDeleteCustomProfile)
                DjSectionTab.ANALYSIS -> MusicAnalysisSection(tracks = tracks, analyses = analyses, automixState = automixState, onAnalyzeLibrary = onAnalyzeLibrary, onCorrectAnalysis = onCorrectAnalysis)
                DjSectionTab.RHYTHM -> RhythmIntelligenceSection()
                DjSectionTab.SETTINGS -> AutomixSettingsScreen(
                    config = automixConfig, genres = genres, playlists = playlists, profiles = profiles, genreRules = genreRules,
                    onBack = { onSelectTab(DjSectionTab.HOME) }, onUpdateConfig = onUpdateConfig, onApplyProfile = onApplyProfile,
                    onSaveCustomProfile = onSaveCustomProfile, onDeleteCustomProfile = onDeleteCustomProfile,
                    onUpsertGenreRule = onUpsertGenreRule, onDeleteGenreRule = onDeleteGenreRule, onPreviewVoice = onPreviewVoice, embeddedInHub = true,
                )
            }
        }
    }
}

@Composable
private fun DjNavigationPill(label: String, selected: Boolean, testTag: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier.height(40.dp).clip(shape)
            .background(if (selected) Color(0xFFE2F36B) else Color(0xFF17212C))
            .border(1.dp, if (selected) Color(0xFFE2F36B) else Color(0xFF273544), shape)
            .clickable(onClick = onClick).padding(horizontal = 15.dp).testTag(testTag),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) Color(0xFF10160B) else Color(0xFFD0D9E2), fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
    }
}
