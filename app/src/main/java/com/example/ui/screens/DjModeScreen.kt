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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    HOME("DJ Home", "dj_tab_home"),
    AUTOMIX("Automix", "dj_tab_automix"),
    QUEUE("Queue", "dj_tab_queue"),
    PROFILES("Profiles", "dj_tab_profiles"),
    ANALYSIS("Analysis", "dj_tab_analysis"),
    SETTINGS("DJ Settings", "dj_tab_settings"),
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("dj_mode_screen"),
    ) {
        MaxmizeTopHeader(
            title = "DJ Mode",
            subtitle = if (automixState.isActive) "Live · ${automixState.activeProfileName}" else "Smart mixing",
            onOpenSettings = onOpenSettings,
        )

        // Internal 6-section DJ navigation bar
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 12.dp),
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(DjSectionTab.entries.filter { it != DjSectionTab.SETTINGS }, key = { it.name }) { tab ->
                val selected = selectedTab == tab
                val shape = RoundedCornerShape(14.dp)
                Box(
                    modifier = Modifier
                        .height(40.dp)
                        .clip(shape)
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceContainer
                        )
                        .border(
                            width = 0.75.dp,
                            color = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant,
                            shape = shape,
                        )
                        .clickable { onSelectTab(tab) }
                        .padding(horizontal = 14.dp)
                        .testTag(tab.testTag),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        ),
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        HorizontalDivider(thickness = 0.75.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))

        Box(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                DjSectionTab.HOME -> DjHomeSection(
                    automixState = automixState,
                    automixConfig = automixConfig,
                    playbackState = playbackState,
                    analyses = analyses,
                    totalLibraryTracks = tracks.size,
                    onStartAutomix = onStartAutomix,
                    onStopAutomix = onStopAutomix,
                    onTogglePlayPause = onTogglePlayPause,
                    onSkipNextMix = onSkipNextMix,
                    onAnalyzeLibrary = onAnalyzeLibrary,
                    onNavigateTab = onSelectTab,
                )

                DjSectionTab.AUTOMIX -> AutomixScreen(
                    automixState = automixState,
                    automixConfig = automixConfig,
                    playbackState = playbackState,
                    analyses = analyses,
                    onBack = { onSelectTab(DjSectionTab.HOME) },
                    onOpenSettings = { onSelectTab(DjSectionTab.SETTINGS) },
                    onOpenDjQueue = { onSelectTab(DjSectionTab.QUEUE) },
                    onStartAutomix = onStartAutomix,
                    onStopAutomix = onStopAutomix,
                    onSkipNextMix = onSkipNextMix,
                    onTogglePlayPause = onTogglePlayPause,
                    onToggleBpmMatch = onToggleBpmMatch,
                    onToggleKeyLock = onToggleKeyLock,
                    onCycleMixMode = onCycleMixMode,
                    onAnalyzeLibrary = onAnalyzeLibrary,
                    onCorrectAnalysis = onCorrectAnalysis,
                    embeddedInHub = true,
                )

                DjSectionTab.QUEUE -> DjQueueScreen(
                    automixState = automixState,
                    playbackState = playbackState,
                    analyses = analyses,
                    mixHistory = mixHistory,
                    onBack = { onSelectTab(DjSectionTab.HOME) },
                    onOpenSearch = onOpenSearch,
                    onPlayDjTrackNow = onPlayDjTrackNow,
                    onMoveDjQueueItem = onMoveDjQueueItem,
                    onRemoveDjQueueItem = onRemoveDjQueueItem,
                    onRecomputeQueue = onRecomputeQueue,
                    onStopAutomix = onStopAutomix,
                    onClearHistory = onClearHistory,
                    embeddedInHub = true,
                )

                DjSectionTab.PROFILES -> DjProfilesSection(
                    config = automixConfig,
                    profiles = profiles,
                    onApplyProfile = onApplyProfile,
                    onSaveCustomProfile = onSaveCustomProfile,
                    onDeleteCustomProfile = onDeleteCustomProfile,
                )

                DjSectionTab.ANALYSIS -> MusicAnalysisSection(
                    tracks = tracks,
                    analyses = analyses,
                    automixState = automixState,
                    onAnalyzeLibrary = onAnalyzeLibrary,
                    onCorrectAnalysis = onCorrectAnalysis,
                )

                DjSectionTab.SETTINGS -> AutomixSettingsScreen(
                    config = automixConfig,
                    genres = genres,
                    playlists = playlists,
                    profiles = profiles,
                    genreRules = genreRules,
                    onBack = { onSelectTab(DjSectionTab.HOME) },
                    onUpdateConfig = onUpdateConfig,
                    onApplyProfile = onApplyProfile,
                    onSaveCustomProfile = onSaveCustomProfile,
                    onDeleteCustomProfile = onDeleteCustomProfile,
                    onUpsertGenreRule = onUpsertGenreRule,
                    onDeleteGenreRule = onDeleteGenreRule,
                    onPreviewVoice = onPreviewVoice,
                    embeddedInHub = true,
                )
            }
        }
    }
}
