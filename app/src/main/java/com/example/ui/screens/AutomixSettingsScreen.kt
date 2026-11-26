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
import androidx.compose.ui.unit.sp
import com.example.data.dj.GenreEngine
import com.example.data.local.db.GenreRuleOverrideEntity
import com.example.data.model.AutomixConfig
import com.example.data.model.DjProfile
import com.example.data.model.DjVoiceGender
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
    onPreviewVoice: () -> Unit,
    modifier: Modifier = Modifier,
    embeddedInHub: Boolean = false,
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
        item {
            DjStudioSectionHeader(
                    eyebrow = "STUDIO CONFIGURATION",
                    title = "Mix preferences",
                    description = "Set transition behavior, track matching and DJ voice.",
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                )
        }
        if (!embeddedInHub) {
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
                            text = "DJ Settings",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        )
                    }
                    IconButton(
                        onClick = { showSaveProfileDialog = true },
                        modifier = Modifier.testTag("save_dj_profile_btn"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.BookmarkAdd,
                            contentDescription = "Save as custom DJ profile",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }

        // A. Mixing Intelligence
        item {
            DjSettingsSectionTitle("Mixing intelligence")
            EditorialGroupCard(modifier = Modifier.padding(horizontal = 20.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Mix Mode", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                    Spacer(modifier = Modifier.height(8.dp))
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
                                    .height(38.dp)
                                    .clip(shape)
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceContainerHigh
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

                ParamDivider()

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

                val totalW = (config.bpmWeight + config.harmonicWeight + config.energyWeight + config.genreWeight)
                    .coerceAtLeast(0.01f)
                WeightSliderRow("Tempo Weight", config.bpmWeight, ((config.bpmWeight / totalW) * 100).roundToInt()) { w ->
                    onUpdateConfig { it.copy(bpmWeight = w) }
                }
                ParamDivider()
                WeightSliderRow("Harmonic Weight", config.harmonicWeight, ((config.harmonicWeight / totalW) * 100).roundToInt()) { w ->
                    onUpdateConfig { it.copy(harmonicWeight = w) }
                }
                ParamDivider()
                WeightSliderRow("Energy Weight", config.energyWeight, ((config.energyWeight / totalW) * 100).roundToInt()) { w ->
                    onUpdateConfig { it.copy(energyWeight = w) }
                }
                ParamDivider()
                WeightSliderRow("Genre Weight", config.genreWeight, ((config.genreWeight / totalW) * 100).roundToInt()) { w ->
                    onUpdateConfig { it.copy(genreWeight = w) }
                }
            }
        }

        // B. Genre Compatibility
        item {
            DjSettingsSectionTitle("Genre compatibility")
            EditorialGroupCard(modifier = Modifier.padding(horizontal = 20.dp)) {
                ParamSwitchRow(
                    title = "Strict Genre Matching",
                    subtitle = "Prevent incompatible jumps (e.g. Pop -> Hard Techno)",
                    checked = config.strictGenreMatching,
                    modifier = Modifier.testTag("switch_strict_genre"),
                    onCheckedChange = { checked -> onUpdateConfig { it.copy(strictGenreMatching = checked) } },
                )
                ParamDivider()
                ParamSwitchRow(
                    title = "Open-Format Bridging",
                    subtitle = "Allow cross-genre transitions via harmonic & BPM locks",
                    checked = config.openFormatBridging,
                    modifier = Modifier.testTag("switch_open_format"),
                    onCheckedChange = { checked -> onUpdateConfig { it.copy(openFormatBridging = checked) } },
                )
                ParamDivider()
                ParamNavRow(
                    title = "Genre Preference",
                    value = config.genrePreference,
                    onClick = { showGenreDialog = true },
                )
                ParamDivider()
                ParamNavRow(
                    title = "Genre Compatibility Rules",
                    value = if (genreRules.isEmpty()) "Default" else "${genreRules.size} custom",
                    onClick = { showRulesDialog = true },
                    modifier = Modifier.testTag("genre_rules_nav_row"),
                )
            }
        }

        // C. BPM and Beatmatching
        item {
            DjSettingsSectionTitle("Tempo and beatmatching")
            EditorialGroupCard(modifier = Modifier.padding(horizontal = 20.dp)) {
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
                ParamSwitchRow(
                    title = "BPM Matching",
                    subtitle = "Beatmatch incoming track tempo",
                    checked = config.bpmMatchEnabled,
                ) { checked ->
                    onUpdateConfig { it.copy(bpmMatchEnabled = checked) }
                }
                ParamDivider()
                ParamSwitchRow(
                    title = "Phrase Alignment",
                    subtitle = "Sync incoming track cue to 8-beat phrase boundaries",
                    checked = config.phraseMatchEnabled,
                ) { checked ->
                    onUpdateConfig { it.copy(phraseMatchEnabled = checked) }
                }
            }
        }

        // D. Harmonic Mixing
        item {
            DjSettingsSectionTitle("Harmonic mixing")
            EditorialGroupCard(modifier = Modifier.padding(horizontal = 20.dp)) {
                ParamSwitchRow(
                    title = "Key Matching",
                    subtitle = "Camelot Wheel harmonic progression",
                    checked = config.keyMatchingEnabled,
                ) { checked ->
                    onUpdateConfig { it.copy(keyMatchingEnabled = checked) }
                }
                ParamDivider()
                ParamSwitchRow(
                    title = "Key Lock",
                    subtitle = "Preserve natural vocal & instrument pitch",
                    checked = config.keyLockEnabled,
                ) { checked ->
                    onUpdateConfig { it.copy(keyLockEnabled = checked) }
                }
                ParamDivider()
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Harmonic Mixing Level", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
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
            }
        }

        // E. Energy Progression
        item {
            DjSettingsSectionTitle("Energy progression")
            EditorialGroupCard(modifier = Modifier.padding(horizontal = 20.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Energy Curve", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
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

        // F. Transition and Crossfade
        item {
            DjSettingsSectionTitle("Transitions and crossfade")
            EditorialGroupCard(modifier = Modifier.padding(horizontal = 20.dp)) {
                ParamNavRow(
                    title = "Transition Style",
                    value = config.transitionStyle.label,
                    onClick = { showTransitionDialog = true },
                )
                ParamDivider()
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
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Set Target Duration", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                        Text("${config.mixDurationMinutes} min", style = MonoTimecodeStyle, color = MaterialTheme.colorScheme.primary)
                    }
                    MaxmizeSlider(
                        value = config.mixDurationMinutes.toFloat(),
                        onValueChange = { v -> onUpdateConfig { it.copy(mixDurationMinutes = v.toInt().coerceIn(10, 240)) } },
                        valueRange = 10f..180f,
                    )
                }
            }
        }

        // G. Dual Deck
        item {
            DjSettingsSectionTitle("Dual deck")
            EditorialGroupCard(modifier = Modifier.padding(horizontal = 20.dp)) {
                ParamSwitchRow(
                    title = "Auto Gain (LUFS)",
                    subtitle = "Balance loudness smoothly across Deck A and Deck B",
                    checked = config.autoGainEnabled,
                ) { checked ->
                    onUpdateConfig { it.copy(autoGainEnabled = checked) }
                }
                ParamDivider()
                val poolLabel = config.sourcePlaylistId?.let { id ->
                    playlists.firstOrNull { it.id == id }?.name
                } ?: "All Local Songs"
                ParamNavRow(
                    title = "Deck Source Pool",
                    value = poolLabel,
                    onClick = { showPoolDialog = true },
                )
            }
        }

        // H. Automix Behavior
        item {
            DjSettingsSectionTitle("Automix behavior")
            EditorialGroupCard(modifier = Modifier.padding(horizontal = 20.dp)) {
                ParamSwitchRow(
                    title = "Avoid Repeats",
                    subtitle = "Skip recently played songs in the current DJ session",
                    checked = config.avoidRepeats,
                ) { checked ->
                    onUpdateConfig { it.copy(avoidRepeats = checked) }
                }
                ParamDivider()
                ParamNavRow(
                    title = "Save Configuration as Profile",
                    value = activeProfile?.name ?: "Custom",
                    onClick = { showSaveProfileDialog = true },
                )
            }
        }

        // I. DJ Voice
        item {
            DjSettingsSectionTitle("DJ voice")
            EditorialGroupCard(modifier = Modifier.padding(horizontal = 20.dp)) {
                Text(
                    "Choose the announcer voice used during DJ sessions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
                DjVoiceGender.entries.forEach { option ->
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clickable { onUpdateConfig { it.copy(djVoiceGender = option) } }
                            .padding(horizontal = 16.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = config.djVoiceGender == option,
                            onClick = { onUpdateConfig { it.copy(djVoiceGender = option) } },
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(option.label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                OutlinedButton(
                    onClick = onPreviewVoice,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text("Preview selected voice")
                }
                ParamDivider()
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Speaking speed", style = MaterialTheme.typography.bodyMedium)
                        Text("${config.djVoiceRate}x", style = MonoTimecodeStyle, color = MaterialTheme.colorScheme.primary)
                    }
                    MaxmizeSlider(
                        value = config.djVoiceRate,
                        onValueChange = { value -> onUpdateConfig { it.copy(djVoiceRate = value) } },
                        valueRange = 0.75f..1.15f,
                    )
                }
                ParamDivider()
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Voice level", style = MaterialTheme.typography.bodyMedium)
                        Text("${(config.djVoiceVolume * 100).roundToInt()}%", style = MonoTimecodeStyle, color = MaterialTheme.colorScheme.primary)
                    }
                    MaxmizeSlider(
                        value = config.djVoiceVolume,
                        onValueChange = { value -> onUpdateConfig { it.copy(djVoiceVolume = value) } },
                        valueRange = 0.25f..1.0f,
                    )
                }
                ParamDivider()
                Text(
                    "Music lowers while the DJ speaks.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
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
            title = { Text("Deck Source Pool") },
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
private fun DjSettingsSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp,
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 8.dp),
    )
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
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
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
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(end = 6.dp),
        )
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}
