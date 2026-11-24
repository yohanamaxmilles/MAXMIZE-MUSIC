package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.dj.GenreEngine
import com.example.data.local.db.GenreRuleOverrideEntity
import com.example.data.model.AutomixConfig
import com.example.data.model.DjProfile
import com.example.data.model.EnergyProgression
import com.example.data.model.GenreSummary
import com.example.data.model.HarmonicMixLevel
import com.example.data.model.MixMode
import com.example.data.model.MoodFilter
import com.example.data.model.Playlist
import com.example.data.model.TransitionStyle
import com.example.ui.components.EditorialChip
import com.example.ui.components.EditorialGroupCard
import com.example.ui.components.MaxmizeSlider
import com.example.ui.components.MaxmizeSwitch
import com.example.ui.theme.MonoTimecodeStyle
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AutomixSettingsScreen(
    config: AutomixConfig,
    genres: List<GenreSummary>,
    playlists: List<Playlist>,
    profiles: List<DjProfile>,
    genreRules: List<GenreRuleOverrideEntity>,
    onBack: () -> Unit,
    onUpdateConfig: ((AutomixConfig) -> AutomixConfig) -> Unit,
    onApplyProfile: (DjProfile) -> Unit,
    onSaveCustomProfile: (name: String, description: String) -> Unit,
    onDeleteCustomProfile: (String) -> Unit,
    onUpsertGenreRule: (String, String, Boolean) -> Unit,
    onDeleteGenreRule: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showGenreDialog by remember { mutableStateOf(false) }
    var showTransitionDialog by remember { mutableStateOf(false) }
    var showPoolDialog by remember { mutableStateOf(false) }
    var showSaveProfileDialog by remember { mutableStateOf(false) }
    var showRulesDialog by remember { mutableStateOf(false) }

    val activeProfile = remember(profiles, config.activeProfileId) {
        profiles.firstOrNull { it.id == config.activeProfileId }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("automix_settings_screen"),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        // Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                            contentDescription = "Back",
                            modifier = Modifier.size(30.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "DJ Intelligence & Automix",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    )
                }
                IconButton(
                    onClick = { showSaveProfileDialog = true },
                    modifier = Modifier.testTag("save_dj_profile_btn"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.BookmarkAdd,
                        contentDescription = "Save current settings as custom DJ profile",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        // DJ Profile Selector
        item {
            Column(modifier = Modifier.padding(top = 6.dp, bottom = 12.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "DJ Profiles",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "${profiles.size} presets",
                        style = MonoTimecodeStyle,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(profiles, key = { it.id }) { profile ->
                        val selected = profile.id == config.activeProfileId
                        val cardShape = RoundedCornerShape(12.dp)
                        Column(
                            modifier = Modifier
                                .width(196.dp)
                                .clip(cardShape)
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surface
                                )
                                .border(
                                    width = if (selected) 1.5.dp else 0.75.dp,
                                    color = if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant,
                                    shape = cardShape,
                                )
                                .clickable { onApplyProfile(profile) }
                                .padding(12.dp)
                                .testTag("dj_profile_${profile.id}"),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = profile.name,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                if (!profile.isBuiltIn) {
                                    IconButton(
                                        onClick = { onDeleteCustomProfile(profile.id) },
                                        modifier = Modifier.size(22.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.DeleteOutline,
                                            contentDescription = "Delete custom profile",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                            }
                            Text(
                                text = profile.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "${profile.bpmMin}-${profile.bpmMax} BPM · ${profile.transitionStyle.label}",
                                style = MonoTimecodeStyle,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                if (activeProfile != null) {
                    Text(
                        text = activeProfile.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                    )
                }
            }
        }

        // Mix Mode Segmented Selector
        item {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                Text(
                    text = "Mix Mode",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MixMode.entries.forEach { mode ->
                        val selected = config.mode == mode
                        val shape = RoundedCornerShape(10.dp)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .clip(shape)
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surface
                                )
                                .border(
                                    width = 0.75.dp,
                                    color = if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline,
                                    shape = shape,
                                )
                                .clickable { onUpdateConfig { it.copy(mode = mode) } },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = mode.label,
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = if (selected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = config.mode.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // DJ Intelligence Settings (Genre, Harmonic Level, Energy Curve, Beam Depth, Rules)
        item {
            Text(
                text = "DJ Intelligence",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
            )
            EditorialGroupCard(modifier = Modifier.padding(horizontal = 20.dp)) {
                ParamSwitchRow(
                    title = "Strict Genre Matching",
                    subtitle = "Prevent incompatible jumps (e.g. Pop -> Hard Techno)",
                    checked = config.strictGenreMatching,
                    onCheckedChange = { checked ->
                        onUpdateConfig { it.copy(strictGenreMatching = checked) }
                    },
                    modifier = Modifier.testTag("switch_strict_genre"),
                )
                ParamDivider()
                ParamSwitchRow(
                    title = "Open-Format Bridging",
                    subtitle = "Allow cross-genre transitions via harmonic & BPM locks",
                    checked = config.openFormatBridging,
                    onCheckedChange = { checked ->
                        onUpdateConfig { it.copy(openFormatBridging = checked) }
                    },
                    modifier = Modifier.testTag("switch_open_format"),
                )
                ParamDivider()
                ParamSwitchRow(
                    title = "Phrase Alignment",
                    subtitle = "Sync incoming track cue to 8-beat phrase boundaries",
                    checked = config.phraseMatchEnabled,
                    onCheckedChange = { checked ->
                        onUpdateConfig { it.copy(phraseMatchEnabled = checked) }
                    },
                )
                ParamDivider()

                // Harmonic Mix Level
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Harmonic Mixing", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        HarmonicMixLevel.entries.forEach { level ->
                            val selected = config.harmonicMixLevel == level
                            val shape = RoundedCornerShape(10.dp)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                                    .clip(shape)
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceContainerHigh
                                    )
                                    .border(
                                        width = 0.75.dp,
                                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                        shape = shape,
                                    )
                                    .clickable { onUpdateConfig { it.copy(harmonicMixLevel = level) } }
                                    .testTag("harmonic_level_${level.name.lowercase()}"),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = level.label,
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = config.harmonicMixLevel.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                ParamDivider()

                // Energy Progression Curve
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Energy Progression", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        EnergyProgression.entries.forEach { curve ->
                            EditorialChip(
                                label = curve.label,
                                selected = config.energyProgression == curve,
                                onClick = { onUpdateConfig { it.copy(energyProgression = curve) } },
                                modifier = Modifier.testTag("energy_curve_${curve.name.lowercase()}"),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = config.energyProgression.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                ParamDivider()

                // Multi-Track Look-Ahead Beam Depth
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Smart Queue Look-Ahead", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                        Text("${config.lookAheadCount} tracks", style = MonoTimecodeStyle, color = MaterialTheme.colorScheme.primary)
                    }
                    MaxmizeSlider(
                        value = config.lookAheadCount.toFloat(),
                        onValueChange = { v -> onUpdateConfig { it.copy(lookAheadCount = v.roundToInt().coerceIn(1, 5)) } },
                        valueRange = 1f..5f,
                    )
                }

                ParamDivider()

                // Custom Genre Allow / Block Rules
                ParamNavRow(
                    title = "Genre Compatibility Rules",
                    value = if (genreRules.isEmpty()) "Default" else "${genreRules.size} custom",
                    onClick = { showRulesDialog = true },
                    modifier = Modifier.testTag("genre_rules_nav_row"),
                )
            }
        }

        // Custom Scoring Weights
        item {
            Text(
                text = "Transition Scoring Weights",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
            )
            val totalW = (config.bpmWeight + config.harmonicWeight + config.energyWeight + config.genreWeight)
                .coerceAtLeast(0.01f)
            EditorialGroupCard(modifier = Modifier.padding(horizontal = 20.dp)) {
                WeightSliderRow("Tempo / BPM", config.bpmWeight, ((config.bpmWeight / totalW) * 100).roundToInt()) { w ->
                    onUpdateConfig { it.copy(bpmWeight = w) }
                }
                ParamDivider()
                WeightSliderRow("Harmonic Key", config.harmonicWeight, ((config.harmonicWeight / totalW) * 100).roundToInt()) { w ->
                    onUpdateConfig { it.copy(harmonicWeight = w) }
                }
                ParamDivider()
                WeightSliderRow("Energy Curve", config.energyWeight, ((config.energyWeight / totalW) * 100).roundToInt()) { w ->
                    onUpdateConfig { it.copy(energyWeight = w) }
                }
                ParamDivider()
                WeightSliderRow("Genre Similarity", config.genreWeight, ((config.genreWeight / totalW) * 100).roundToInt()) { w ->
                    onUpdateConfig { it.copy(genreWeight = w) }
                }
            }
        }

        // Mix Parameters
        item {
            Text(
                text = "Mix Parameters",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
            )
            EditorialGroupCard(modifier = Modifier.padding(horizontal = 20.dp)) {
                // BPM Range
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("BPM Range", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                        Text(
                            text = "${config.bpmMin} - ${config.bpmMax} BPM",
                            style = MonoTimecodeStyle,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Min BPM (${config.bpmMin})", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    MaxmizeSlider(
                        value = config.bpmMin.toFloat(),
                        onValueChange = { v ->
                            val nextMin = v.toInt().coerceIn(50, config.bpmMax - 10)
                            onUpdateConfig { it.copy(bpmMin = nextMin) }
                        },
                        valueRange = 50f..170f,
                    )
                    Text("Max BPM (${config.bpmMax})", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    MaxmizeSlider(
                        value = config.bpmMax.toFloat(),
                        onValueChange = { v ->
                            val nextMax = v.toInt().coerceIn(config.bpmMin + 10, 220)
                            onUpdateConfig { it.copy(bpmMax = nextMax) }
                        },
                        valueRange = 80f..220f,
                    )
                }

                ParamDivider()
                ParamSwitchRow("BPM Matching", "Beatmatch incoming track tempo", config.bpmMatchEnabled) { checked ->
                    onUpdateConfig { it.copy(bpmMatchEnabled = checked) }
                }
                ParamDivider()
                ParamSwitchRow("Key Matching", "Camelot Wheel harmonic progression", config.keyMatchingEnabled) { checked ->
                    onUpdateConfig { it.copy(keyMatchingEnabled = checked) }
                }
                ParamDivider()
                ParamSwitchRow("Key Lock", "Preserve natural vocal & instrument pitch", config.keyLockEnabled) { checked ->
                    onUpdateConfig { it.copy(keyLockEnabled = checked) }
                }
                ParamDivider()
                ParamSwitchRow("Auto Gain (LUFS)", "Balance track volume smoothly", config.autoGainEnabled) { checked ->
                    onUpdateConfig { it.copy(autoGainEnabled = checked) }
                }
                ParamDivider()
                ParamSwitchRow("Avoid Repeats", "Skip recently played songs in session", config.avoidRepeats) { checked ->
                    onUpdateConfig { it.copy(avoidRepeats = checked) }
                }
                ParamDivider()

                // Genre Preference
                ParamNavRow(
                    title = "Genre Preference",
                    value = config.genrePreference,
                    onClick = { showGenreDialog = true },
                )
                ParamDivider()

                // Transition Style
                ParamNavRow(
                    title = "Transition Style",
                    value = config.transitionStyle.label,
                    onClick = { showTransitionDialog = true },
                )
                ParamDivider()

                // Mix Pool Source
                val poolLabel = config.sourcePlaylistId?.let { id ->
                    playlists.firstOrNull { it.id == id }?.name
                } ?: "All Local Songs"
                ParamNavRow(
                    title = "Mix Source Pool",
                    value = poolLabel,
                    onClick = { showPoolDialog = true },
                )
                ParamDivider()

                // Transition Duration
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Transition Duration", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                        Text("${config.transitionDurationSec}s", style = MonoTimecodeStyle, color = MaterialTheme.colorScheme.primary)
                    }
                    MaxmizeSlider(
                        value = config.transitionDurationSec.toFloat(),
                        onValueChange = { v -> onUpdateConfig { it.copy(transitionDurationSec = v.toInt().coerceIn(2, 16)) } },
                        valueRange = 2f..16f,
                    )
                }

                ParamDivider()

                // Mix Duration
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Mix Duration", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                        Text("${config.mixDurationMinutes} min", style = MonoTimecodeStyle, color = MaterialTheme.colorScheme.primary)
                    }
                    MaxmizeSlider(
                        value = config.mixDurationMinutes.toFloat(),
                        onValueChange = { v -> onUpdateConfig { it.copy(mixDurationMinutes = v.toInt().coerceIn(10, 240)) } },
                        valueRange = 10f..180f,
                    )
                }

                ParamDivider()

                // Auto-Select Mood + Chips
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Auto-Select Mood", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                        MaxmizeSwitch(
                            checked = config.autoSelectMood,
                            onCheckedChange = { checked -> onUpdateConfig { it.copy(autoSelectMood = checked) } },
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        MoodFilter.entries.forEach { mood ->
                            val selected = config.mood == mood
                            val shape = RoundedCornerShape(10.dp)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .clip(shape)
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceContainerHigh
                                    )
                                    .border(
                                        width = 0.75.dp,
                                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                        shape = shape,
                                    )
                                    .clickable(enabled = config.autoSelectMood) {
                                        onUpdateConfig { it.copy(mood = mood) }
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = mood.label,
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showGenreDialog) {
        val options = (listOf("All Genres") + genres.map { it.genreName } + GenreEngine.SUPPORTED_GENRES)
            .filter { it.isNotBlank() && !it.equals("Unknown Genre", true) }
            .distinct()
        AlertDialog(
            onDismissRequest = { showGenreDialog = false },
            title = { Text("Genre Preference") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    options.take(18).forEach { g ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onUpdateConfig { it.copy(genrePreference = g) }
                                    showGenreDialog = false
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = config.genrePreference == g, onClick = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(g)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showGenreDialog = false }) { Text("Close") } },
        )
    }

    if (showTransitionDialog) {
        AlertDialog(
            onDismissRequest = { showTransitionDialog = false },
            title = { Text("Transition Style") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    TransitionStyle.entries.forEach { style ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onUpdateConfig { it.copy(transitionStyle = style) }
                                    showTransitionDialog = false
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = config.transitionStyle == style, onClick = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(style.label, fontWeight = FontWeight.SemiBold)
                                Text(style.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showTransitionDialog = false }) { Text("Close") } },
        )
    }

    if (showPoolDialog) {
        AlertDialog(
            onDismissRequest = { showPoolDialog = false },
            title = { Text("Mix Source Pool") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onUpdateConfig { it.copy(sourcePlaylistId = null) }
                                showPoolDialog = false
                            }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = config.sourcePlaylistId == null, onClick = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("All Local Songs")
                    }
                    playlists.forEach { pl ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onUpdateConfig { it.copy(sourcePlaylistId = pl.id) }
                                    showPoolDialog = false
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = config.sourcePlaylistId == pl.id, onClick = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("${pl.name} (${pl.trackCount} songs)")
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showPoolDialog = false }) { Text("Close") } },
        )
    }

    if (showSaveProfileDialog) {
        var profileName by remember { mutableStateOf("") }
        var profileDesc by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showSaveProfileDialog = false },
            title = { Text("Save DJ Profile") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = profileName,
                        onValueChange = { profileName = it },
                        label = { Text("Profile Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = profileDesc,
                        onValueChange = { profileDesc = it },
                        label = { Text("Description (optional)") },
                        maxLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSaveCustomProfile(profileName, profileDesc)
                        showSaveProfileDialog = false
                    },
                    enabled = profileName.isNotBlank(),
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showSaveProfileDialog = false }) { Text("Cancel") }
            },
        )
    }

    if (showRulesDialog) {
        var genreA by remember { mutableStateOf("Afrobeats") }
        var genreB by remember { mutableStateOf("Amapiano") }
        var ruleAllowed by remember { mutableStateOf(true) }
        AlertDialog(
            onDismissRequest = { showRulesDialog = false },
            title = { Text("Custom Genre Rules") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "Override default strict rules to allow or block pairs of subgenres.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = genreA,
                        onValueChange = { genreA = it },
                        label = { Text("Genre A") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = genreB,
                        onValueChange = { genreB = it },
                        label = { Text("Genre B") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        EditorialChip(
                            label = "Allow Pair",
                            selected = ruleAllowed,
                            onClick = { ruleAllowed = true },
                            modifier = Modifier.weight(1f),
                        )
                        EditorialChip(
                            label = "Block Pair",
                            selected = !ruleAllowed,
                            onClick = { ruleAllowed = false },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    OutlinedButton(
                        onClick = { onUpsertGenreRule(genreA, genreB, ruleAllowed) },
                        enabled = genreA.isNotBlank() && genreB.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Rule")
                    }
                    if (genreRules.isNotEmpty()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        genreRules.forEach { rule ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "${if (rule.allowed) "ALLOW" else "BLOCK"}: ${rule.genreA} <-> ${rule.genreB}",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(onClick = { onDeleteGenreRule(rule.genreA, rule.genreB) }) {
                                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Remove rule", modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showRulesDialog = false }) { Text("Done") } },
        )
    }
}

@Composable
private fun WeightSliderRow(
    title: String,
    rawWeight: Float,
    normalizedPct: Int,
    onWeightChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
            Text("$normalizedPct%", style = MonoTimecodeStyle, color = MaterialTheme.colorScheme.primary)
        }
        MaxmizeSlider(
            value = rawWeight,
            onValueChange = onWeightChange,
            valueRange = 0.05f..1.0f,
        )
    }
}

@Composable
private fun ParamDivider() {
    HorizontalDivider(
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

@Composable
private fun ParamSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    modifier: Modifier = Modifier,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        MaxmizeSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ParamNavRow(
    title: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 4.dp),
        )
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}
