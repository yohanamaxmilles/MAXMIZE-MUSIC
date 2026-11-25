package com.example.ui.screens

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Loop
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SurroundSound
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.db.MetadataUndoEntity
import com.example.data.local.prefs.UserPreferences
import com.example.data.model.AccentPreset
import com.example.data.model.EqualizerUiState
import com.example.data.model.FontChoice
import com.example.data.model.LibraryStats
import com.example.data.model.MetadataHealthReport
import com.example.data.model.ThemeMode
import com.example.data.model.Track
import com.example.data.model.UiDensity
import com.example.ui.components.EditorialChip
import com.example.ui.components.EditorialGroupCard
import com.example.ui.components.MaxmizeSlider
import com.example.ui.components.MaxmizeSwitch
import com.example.ui.theme.MonoTimecodeStyle
import com.example.ui.theme.accentColorFor
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    prefs: UserPreferences,
    equalizerState: EqualizerUiState,
    lyricsBatchStatus: String?,
    artworkBatchStatus: String?,
    failedArtworkCount: Int,
    metadataHealth: MetadataHealthReport,
    libraryStats: LibraryStats,
    undoHistory: List<MetadataUndoEntity>,
    excludedFolders: List<String>,
    trackCount: Int,
    hasAudioPermission: Boolean = true,
    onBack: () -> Unit = {},
    onRequestPermission: () -> Unit = {},
    onSetThemeMode: (ThemeMode) -> Unit,
    onSetAccentPreset: (AccentPreset) -> Unit,
    onSetFontChoice: (FontChoice) -> Unit,
    onSetFontScale: (Float) -> Unit,
    onSetUiDensity: (UiDensity) -> Unit,
    onOpenEqualizer: () -> Unit,
    onToggleBassBoost: (Boolean) -> Unit,
    onToggleVirtualizer: (Boolean) -> Unit,
    onSetGapless: (Boolean) -> Unit,
    onSetSkipSilence: (Boolean) -> Unit,
    onSetLoudnessNormalization: (Boolean) -> Unit,
    onSetCrossfade: (Int) -> Unit,
    onSetPlaybackSpeed: (Float) -> Unit,
    onSetResumePaused: (Boolean) -> Unit,
    onSetAutoDownloadLyrics: (Boolean) -> Unit,
    onSetLyricsWifiOnly: (Boolean) -> Unit,
    onSetDownloadLyricsOnPlayStart: (Boolean) -> Unit,
    onSetMusixmatchApiKey: (String) -> Unit,
    onBatchFetchLyrics: () -> Unit,
    onClearLyricsCache: () -> Unit,
    onSetAutoFixArtworkOnScan: (Boolean) -> Unit,
    onSetArtworkWifiOnly: (Boolean) -> Unit,
    onBatchFixMissingArtwork: () -> Unit,
    onRetryFailedArtwork: () -> Unit,
    onClearArtworkCache: () -> Unit,
    onEditTrackMetadata: (Track) -> Unit,
    onAutoFixTrackArtwork: (Track) -> Unit,
    onUndoMetadataEntry: (MetadataUndoEntity) -> Unit,
    onSetAutoScan: (Boolean) -> Unit,
    onSetMinDuration: (Int) -> Unit,
    onSetNotifSeek: (Boolean) -> Unit,
    onRescan: (Boolean) -> Unit,
    onImportFolder: () -> Unit,
    onIncludeFolder: (String) -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    onOpenSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var showExcludedDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showMusixmatchDialog by remember { mutableStateOf(false) }
    var showStatsDialog by remember { mutableStateOf(false) }
    var showHealthDialog by remember { mutableStateOf(false) }
    var showDuplicatesDialog by remember { mutableStateOf(false) }
    var showUndoDialog by remember { mutableStateOf(false) }

    val isDark = when (prefs.themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // Editorial Settings Header with Back Arrow
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, end = 10.dp, top = 16.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("settings_back_btn"),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                        contentDescription = "Back",
                        modifier = Modifier.size(28.dp),
                    )
                }
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
            IconButton(
                onClick = onOpenSearch,
                modifier = Modifier.testTag("settings_search_btn"),
            ) {
                Icon(Icons.Rounded.Search, contentDescription = "Search library")
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("settings_screen"),
            contentPadding = PaddingValues(top = 4.dp, bottom = 40.dp),
        ) {

        // 0. App summary
        item {
            EditorialGroupCard(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.GraphicEq,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(30.dp),
                        )
                    }
                    Column {
                        Text(
                            text = "Maxmize",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "$trackCount songs · Offline",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // 1. APPEARANCE
        item {
            SettingsCategoryLabel("APPEARANCE")
            EditorialGroupCard(modifier = Modifier.padding(horizontal = 20.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val themeItems = listOf(
                        Triple(ThemeMode.DARK, "Dark", Icons.Rounded.DarkMode),
                        Triple(ThemeMode.LIGHT, "Light", Icons.Rounded.LightMode),
                        Triple(ThemeMode.SYSTEM, "System", Icons.Rounded.PhoneAndroid),
                    )
                    themeItems.forEach { (mode, label, icon) ->
                        val selected = prefs.themeMode == mode
                        val tileShape = RoundedCornerShape(18.dp)
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(tileShape)
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceContainerHigh
                                )
                                .then(
                                    if (selected) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, tileShape)
                                    else Modifier,
                                )
                                .clickable { onSetThemeMode(mode) }
                                .padding(vertical = 16.dp)
                                .testTag("theme_chip_${mode.name.lowercase()}"),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                ),
                                color = if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                RowDivider()
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        text = "Font Family",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FontChoice.entries.forEach { font ->
                            EditorialChip(
                                label = font.label,
                                selected = prefs.fontChoice == font,
                                onClick = { onSetFontChoice(font) },
                                modifier = Modifier.testTag("font_choice_${font.name.lowercase()}"),
                            )
                        }
                    }
                }
                RowDivider()
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Text Scale", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                        Text(
                            text = "${(prefs.fontScale * 100).roundToInt()}%",
                            style = MonoTimecodeStyle,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    MaxmizeSlider(
                        value = prefs.fontScale,
                        onValueChange = { onSetFontScale(it) },
                        valueRange = 0.88f..1.18f,
                    )
                }
                RowDivider()
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        text = "Layout Density",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        UiDensity.entries.forEach { density ->
                            EditorialChip(
                                label = density.label,
                                selected = prefs.uiDensity == density,
                                onClick = { onSetUiDensity(density) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                RowDivider()
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        text = "Accent Color",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        AccentPreset.entries.forEach { preset ->
                            val swatchColor = accentColorFor(preset, isDark)
                            val selected = prefs.accentPreset == preset
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .let {
                                        if (selected) {
                                            it.border(2.dp, MaterialTheme.colorScheme.onBackground, CircleShape)
                                                .padding(4.dp)
                                        } else it
                                    }
                                    .clip(CircleShape)
                                    .background(swatchColor)
                                    .clickable { onSetAccentPreset(preset) }
                                    .testTag("accent_swatch_${preset.name.lowercase()}"),
                            )
                        }
                    }
                }
            }
        }

        // 2. PLAYBACK
        item {
            SettingsCategoryLabel("PLAYBACK")
            EditorialGroupCard(modifier = Modifier.padding(horizontal = 20.dp)) {
                SettingsSwitchRow(
                    icon = Icons.Rounded.GraphicEq,
                    title = "Gapless Playback",
                    checked = prefs.gaplessEnabled,
                    onCheckedChange = onSetGapless,
                )
                RowDivider()
                SettingsSwitchRow(
                    icon = Icons.Rounded.FastForward,
                    title = "Skip Silence",
                    subtitle = "Trim quiet intros, outros & dead air",
                    checked = prefs.skipSilenceEnabled,
                    onCheckedChange = onSetSkipSilence,
                )
                RowDivider()
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            SettingsIconBadge(Icons.Rounded.Loop)
                            Column {
                                Text(
                                    text = "Crossfade",
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                )
                                Text(
                                    text = if (prefs.crossfadeSeconds == 0) "Off" else "${prefs.crossfadeSeconds}s overlap",
                                    style = MonoTimecodeStyle,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        MaxmizeSwitch(
                            checked = prefs.crossfadeSeconds > 0,
                            onCheckedChange = { enabled ->
                                onSetCrossfade(if (enabled) 4 else 0)
                            },
                        )
                    }
                    if (prefs.crossfadeSeconds > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        MaxmizeSlider(
                            value = prefs.crossfadeSeconds.toFloat(),
                            onValueChange = { onSetCrossfade(it.toInt()) },
                            valueRange = 1f..12f,
                        )
                    }
                }
                RowDivider()
                SettingsNavRow(
                    icon = Icons.Rounded.Speed,
                    title = "Playback Speed",
                    valueText = String.format(Locale.US, "%.2fx", prefs.playbackSpeed),
                    onClick = { showSpeedDialog = true },
                )
                RowDivider()
                SettingsSwitchRow(
                    icon = Icons.Rounded.Restore,
                    title = "Restore Queue Paused on Reopen",
                    checked = prefs.resumePausedOnReopen,
                    onCheckedChange = onSetResumePaused,
                )
                RowDivider()
                SettingsSwitchRow(
                    icon = Icons.Rounded.Notifications,
                    title = "Show Track Controls in Notification",
                    checked = prefs.showNotificationSeekButtons,
                    onCheckedChange = onSetNotifSeek,
                )
            }
        }

        // 3. MUSIC LIBRARY
        item {
            SettingsCategoryLabel("MUSIC LIBRARY")
            EditorialGroupCard(modifier = Modifier.padding(horizontal = 20.dp)) {
                SettingsNavRow(
                    icon = Icons.Rounded.Refresh,
                    title = "Rescan Local Library",
                    valueText = "$trackCount tracks",
                    onClick = { onRescan(false) },
                )
                RowDivider()
                SettingsNavRow(
                    icon = Icons.Rounded.Refresh,
                    title = "Refresh All Metadata",
                    onClick = { onRescan(true) },
                )
                RowDivider()
                SettingsNavRow(
                    icon = Icons.Rounded.CreateNewFolder,
                    title = "Import Music Folder",
                    onClick = onImportFolder,
                )
                RowDivider()
                SettingsNavRow(
                    icon = Icons.Rounded.FolderOff,
                    title = "Excluded Folders",
                    valueText = "${excludedFolders.size}",
                    onClick = { showExcludedDialog = true },
                )
                RowDivider()
                SettingsSwitchRow(
                    icon = Icons.Rounded.Refresh,
                    title = "Auto-Scan on Startup",
                    checked = prefs.autoScanOnLaunch,
                    onCheckedChange = onSetAutoScan,
                )
                RowDivider()
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        text = "Minimum Track Duration",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(0, 5, 15, 30, 60).forEach { sec ->
                            EditorialChip(
                                label = if (sec == 0) "All" else "${sec}s",
                                selected = prefs.minDurationSec == sec,
                                onClick = { onSetMinDuration(sec) },
                            )
                        }
                    }
                }
                RowDivider()
                SettingsNavRow(
                    icon = Icons.Rounded.Analytics,
                    title = "Library Analytics",
                    valueText = "${libraryStats.totalTracks} songs",
                    onClick = { showStatsDialog = true },
                    modifier = Modifier.testTag("settings_library_stats"),
                )
                RowDivider()
                SettingsNavRow(
                    icon = Icons.Rounded.ContentCopy,
                    title = "Duplicate Track Finder",
                    valueText = "${metadataHealth.duplicateGroups.size} groups",
                    onClick = { showDuplicatesDialog = true },
                    modifier = Modifier.testTag("settings_duplicate_finder"),
                )
            }
        }

        // 4. LYRICS
        item {
            SettingsCategoryLabel("LYRICS")
            EditorialGroupCard(modifier = Modifier.padding(horizontal = 20.dp)) {
                SettingsSwitchRow(
                    icon = Icons.Rounded.CloudDownload,
                    title = "Auto-Download Synced Lyrics",
                    subtitle = "Fetch missing lyrics via LRCLIB & cache offline",
                    checked = prefs.autoDownloadLyrics,
                    onCheckedChange = onSetAutoDownloadLyrics,
                )
                RowDivider()
                SettingsSwitchRow(
                    icon = Icons.Rounded.Wifi,
                    title = "Wi-Fi Only Lyrics Downloads",
                    checked = prefs.lyricsWifiOnly,
                    onCheckedChange = onSetLyricsWifiOnly,
                )
                RowDivider()
                SettingsSwitchRow(
                    icon = Icons.Rounded.PlayArrow,
                    title = "Download When Playback Starts",
                    checked = prefs.downloadLyricsOnPlayStart,
                    onCheckedChange = onSetDownloadLyricsOnPlayStart,
                )
                RowDivider()
                SettingsNavRow(
                    icon = Icons.Rounded.Lyrics,
                    title = "Fetch Missing Library Lyrics",
                    valueText = lyricsBatchStatus ?: "Scan",
                    onClick = onBatchFetchLyrics,
                    modifier = Modifier.testTag("settings_batch_lyrics"),
                )
                RowDivider()
                SettingsNavRow(
                    icon = Icons.Rounded.Key,
                    title = "Musixmatch API Key (Optional)",
                    valueText = if (prefs.musixmatchApiKey.isBlank()) "Not Set" else "Configured",
                    onClick = { showMusixmatchDialog = true },
                )
                RowDivider()
                SettingsNavRow(
                    icon = Icons.Rounded.DeleteOutline,
                    title = "Clear Cached Lyrics",
                    onClick = onClearLyricsCache,
                )
            }
        }

        // 5. ARTWORK AND METADATA
        item {
            SettingsCategoryLabel("ARTWORK AND METADATA")
            EditorialGroupCard(modifier = Modifier.padding(horizontal = 20.dp)) {
                SettingsSwitchRow(
                    icon = Icons.Rounded.AutoFixHigh,
                    title = "Auto-Fix Missing Covers on Scan",
                    subtitle = "Only apply high-confidence verified album art",
                    checked = prefs.autoFixArtworkOnScan,
                    onCheckedChange = onSetAutoFixArtworkOnScan,
                )
                RowDivider()
                SettingsSwitchRow(
                    icon = Icons.Rounded.Wifi,
                    title = "Wi-Fi Only Artwork Downloads",
                    checked = prefs.artworkWifiOnly,
                    onCheckedChange = onSetArtworkWifiOnly,
                )
                RowDivider()
                SettingsNavRow(
                    icon = Icons.Rounded.Image,
                    title = "Find Missing Album Covers",
                    valueText = artworkBatchStatus ?: "${metadataHealth.missingArtworkTracks.size} missing",
                    onClick = onBatchFixMissingArtwork,
                    modifier = Modifier.testTag("settings_batch_artwork"),
                )
                RowDivider()
                SettingsNavRow(
                    icon = Icons.Rounded.Refresh,
                    title = "Retry Failed Cover Lookups",
                    valueText = if (failedArtworkCount > 0) "$failedArtworkCount queued" else "None",
                    onClick = onRetryFailedArtwork,
                )
                RowDivider()
                SettingsNavRow(
                    icon = Icons.Rounded.HealthAndSafety,
                    title = "Metadata Health Inspector",
                    valueText = if (metadataHealth.totalIssueTracks == 0) "Clean" else "${metadataHealth.totalIssueTracks} issues",
                    onClick = { showHealthDialog = true },
                    modifier = Modifier.testTag("settings_metadata_health"),
                )
                RowDivider()
                SettingsNavRow(
                    icon = Icons.Rounded.Undo,
                    title = "Metadata Edit Undo History",
                    valueText = "${undoHistory.size} edits",
                    onClick = { showUndoDialog = true },
                    modifier = Modifier.testTag("settings_undo_history"),
                )
                RowDivider()
                SettingsNavRow(
                    icon = Icons.Rounded.DeleteOutline,
                    title = "Clear Artwork Cache",
                    onClick = onClearArtworkCache,
                )
            }
        }

        // 6. AUDIO
        item {
            SettingsCategoryLabel("AUDIO")
            EditorialGroupCard(modifier = Modifier.padding(horizontal = 20.dp)) {
                SettingsNavRow(
                    icon = Icons.Rounded.Equalizer,
                    title = "Equalizer",
                    valueText = prefs.eqPreset,
                    onClick = onOpenEqualizer,
                    modifier = Modifier.testTag("settings_open_eq"),
                )
                RowDivider()
                SettingsSwitchRow(
                    icon = Icons.Rounded.Speaker,
                    title = "Bass Boost",
                    checked = equalizerState.bassBoostStrength > 0,
                    onCheckedChange = onToggleBassBoost,
                )
                RowDivider()
                SettingsSwitchRow(
                    icon = Icons.Rounded.SurroundSound,
                    title = "Virtualizer",
                    checked = equalizerState.virtualizerStrength > 0,
                    onCheckedChange = onToggleVirtualizer,
                )
                RowDivider()
                SettingsSwitchRow(
                    icon = Icons.Rounded.VolumeUp,
                    title = "Loudness Normalization",
                    subtitle = "Target balanced -14 LUFS playback level",
                    checked = prefs.loudnessNormalizationEnabled,
                    onCheckedChange = onSetLoudnessNormalization,
                )
            }
        }

        // 7. STORAGE AND BACKUP
        item {
            SettingsCategoryLabel("STORAGE AND BACKUP")
            EditorialGroupCard(modifier = Modifier.padding(horizontal = 20.dp)) {
                SettingsNavRow(
                    icon = Icons.Rounded.Backup,
                    title = "Export Playlists & Settings",
                    onClick = onExportBackup,
                    modifier = Modifier.testTag("settings_export_backup"),
                )
                RowDivider()
                SettingsNavRow(
                    icon = Icons.Rounded.Restore,
                    title = "Restore from Backup",
                    onClick = onImportBackup,
                    modifier = Modifier.testTag("settings_import_backup"),
                )
            }
        }

        // 8. PRIVACY AND PERMISSIONS
        item {
            SettingsCategoryLabel("PRIVACY AND PERMISSIONS")
            EditorialGroupCard(modifier = Modifier.padding(horizontal = 20.dp)) {
                SettingsNavRow(
                    icon = Icons.Rounded.PrivacyTip,
                    title = "Audio Storage Permission",
                    valueText = if (hasAudioPermission) "Granted" else "Grant Access",
                    onClick = {
                        if (!hasAudioPermission) {
                            onRequestPermission()
                        } else {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = android.net.Uri.parse("package:${context.packageName}")
                            }
                            runCatching { context.startActivity(intent) }
                        }
                    },
                )
                RowDivider()
                SettingsNavRow(
                    icon = Icons.Rounded.Notifications,
                    title = "Media Notification Settings",
                    onClick = {
                        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            }
                        } else {
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = android.net.Uri.parse("package:${context.packageName}")
                            }
                        }
                        runCatching { context.startActivity(intent) }
                    },
                )
                RowDivider()
                SettingsNavRow(
                    icon = Icons.Rounded.PrivacyTip,
                    title = "Offline Privacy Architecture",
                    valueText = "Local-First",
                    onClick = { showPrivacyDialog = true },
                )
            }
        }

        // 9. ABOUT MAXMIZE MUSIC
        item {
            SettingsCategoryLabel("ABOUT MAXMIZE MUSIC")
            EditorialGroupCard(modifier = Modifier.padding(horizontal = 20.dp)) {
                SettingsNavRow(
                    icon = Icons.Rounded.Info,
                    title = "About Maxmize Music",
                    valueText = "v2.0",
                    onClick = { showAboutDialog = true },
                    modifier = Modifier.testTag("settings_about_row"),
                )
            }
        }
        }
    }

    if (showStatsDialog) {
        LibraryStatsDialog(stats = libraryStats, onDismiss = { showStatsDialog = false })
    }
    if (showHealthDialog) {
        MetadataHealthDialog(
            health = metadataHealth,
            onEditTrack = onEditTrackMetadata,
            onAutoFixArtwork = onAutoFixTrackArtwork,
            onBatchFixArtwork = onBatchFixMissingArtwork,
            onDismiss = { showHealthDialog = false },
        )
    }
    if (showDuplicatesDialog) {
        DuplicateFinderDialog(health = metadataHealth, onDismiss = { showDuplicatesDialog = false })
    }
    if (showUndoDialog) {
        UndoHistoryDialog(
            undoHistory = undoHistory,
            onUndoEntry = onUndoMetadataEntry,
            onDismiss = { showUndoDialog = false },
        )
    }

    if (showSpeedDialog) {
        AlertDialog(
            onDismissRequest = { showSpeedDialog = false },
            title = { Text("Playback Speed") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = String.format(Locale.US, "%.2fx", prefs.playbackSpeed),
                        style = MonoTimecodeStyle.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    MaxmizeSlider(
                        value = prefs.playbackSpeed,
                        onValueChange = onSetPlaybackSpeed,
                        valueRange = 0.5f..2.0f,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { presetSpeed ->
                            EditorialChip(
                                label = "${presetSpeed}x",
                                selected = kotlin.math.abs(prefs.playbackSpeed - presetSpeed) < 0.03f,
                                onClick = { onSetPlaybackSpeed(presetSpeed) },
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSpeedDialog = false }) { Text("Done") }
            },
        )
    }

    if (showMusixmatchDialog) {
        var apiKey by remember(prefs.musixmatchApiKey) { mutableStateOf(prefs.musixmatchApiKey) }
        AlertDialog(
            onDismissRequest = { showMusixmatchDialog = false },
            title = { Text("Musixmatch API Key") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Optional: provide your licensed Musixmatch API key to enable fallback lyrics lookup alongside LRCLIB.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("API Key") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    onSetMusixmatchApiKey(apiKey)
                    showMusixmatchDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showMusixmatchDialog = false }) { Text("Cancel") }
            },
        )
    }

    if (showExcludedDialog) {
        AlertDialog(
            onDismissRequest = { showExcludedDialog = false },
            title = { Text("Excluded Folders") },
            text = {
                if (excludedFolders.isEmpty()) {
                    Text("No folders are currently excluded. You can exclude folders from Library -> Folders.")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        excludedFolders.forEach { folder ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = folder,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(onClick = { onIncludeFolder(folder) }) {
                                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Restore folder")
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showExcludedDialog = false }) { Text("Close") }
            },
        )
    }

    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = { Text("Offline Privacy Architecture") },
            text = {
                Text(
                    "Maxmize plays all music, analyzes BPM/Key/Genre/Loudness, and runs Dual-Deck Automix 100% offline on your device with zero account or telemetry. Optional internet access is only used when you look up missing lyrics (LRCLIB / Musixmatch) or missing album artwork (iTunes / MusicBrainz)."
                )
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyDialog = false }) { Text("OK") }
            },
        )
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("MAXMIZE MUSIC") },
            text = {
                Text(
                    "Version 2.0\n\nBuilt with Dual-Deck Media3 ExoPlayer, On-Device PCM DSP Analysis, Smart Synced Lyrics & Artwork Engines, Room, DataStore, and Jetpack Compose."
                )
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) { Text("Close") }
            },
        )
    }
}

@Composable
private fun SettingsCategoryLabel(title: String) {
    Text(
        text = title.lowercase().replaceFirstChar { it.uppercase() },
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(start = 24.dp, end = 20.dp, top = 28.dp, bottom = 12.dp),
    )
}

@Composable
private fun SettingsIconBadge(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(21.dp),
        )
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

@Composable
private fun SettingsNavRow(
    icon: ImageVector,
    title: String,
    valueText: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsIconBadge(icon)
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (!valueText.isNullOrBlank()) {
            Text(
                text = valueText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 4.dp),
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsIconBadge(icon)
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        MaxmizeSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}
