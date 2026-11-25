package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.EqBandState
import com.example.data.model.EqualizerUiState
import com.example.ui.components.EditorialChip
import com.example.ui.components.EditorialGroupCard
import com.example.ui.components.MaxmizeSlider
import com.example.ui.components.MaxmizeSwitch
import com.example.ui.theme.MonoBadgeStyle
import com.example.ui.theme.MonoTimecodeStyle
import java.util.Locale
import kotlin.math.roundToInt

private val FallbackDisplayBands = listOf(
    EqBandState(0, 60, 0),
    EqBandState(1, 230, 0),
    EqBandState(2, 910, 0),
    EqBandState(3, 3600, 0),
    EqBandState(4, 14000, 0),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EqualizerScreen(
    eqState: EqualizerUiState,
    playbackSpeed: Float,
    audioBalance: Float,
    onBack: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onSelectPreset: (String) -> Unit,
    onBandLevelChange: (Int, Int) -> Unit,
    onBassBoostChange: (Int) -> Unit,
    onVirtualizerChange: (Int) -> Unit,
    onResetEq: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    onBalanceChange: (Float) -> Unit,
) {
    var lastNonZeroBass by remember { mutableIntStateOf(450) }
    var lastNonZeroVirtualizer by remember { mutableIntStateOf(450) }
    if (eqState.bassBoostStrength > 0) lastNonZeroBass = eqState.bassBoostStrength
    if (eqState.virtualizerStrength > 0) lastNonZeroVirtualizer = eqState.virtualizerStrength

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("equalizer_screen"),
    ) {
        // Editorial Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
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
                text = "Equalizer",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            )
            IconButton(
                onClick = onResetEq,
                modifier = Modifier.testTag("eq_reset_btn"),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = "Reset equalizer",
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Master Toggle Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Equalizer",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                )
                MaxmizeSwitch(
                    checked = eqState.isEnabled,
                    onCheckedChange = onToggleEnabled,
                    modifier = Modifier.testTag("eq_master_switch"),
                )
            }

            if (!eqState.isSupported) {
                EditorialGroupCard(cornerRadius = 12.dp) {
                    Text(
                        text = eqState.unavailableReason
                            ?: "Start playing a track to bind the live hardware equalizer to your playback session.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(14.dp),
                    )
                }
            }

            // Presets
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                eqState.availablePresets.forEach { preset ->
                    EditorialChip(
                        label = preset,
                        selected = eqState.currentPresetName == preset,
                        onClick = { onSelectPreset(preset) },
                        enabled = eqState.isEnabled,
                        modifier = Modifier.testTag("eq_preset_${preset.lowercase()}"),
                    )
                }
            }

            // Vertical Graphic EQ Panel (matches showcase)
            val displayBands = eqState.bands.ifEmpty { FallbackDisplayBands }
            val liveSlidersEnabled = eqState.isEnabled && eqState.isSupported && eqState.bands.isNotEmpty()
            val maxDbText = "+${(eqState.maxLevelMb / 100f).roundToInt()}dB"
            val minDbText = "${(eqState.minLevelMb / 100f).roundToInt()}dB"

            EditorialGroupCard(cornerRadius = 16.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Left dB Scale Column
                    Column(
                        modifier = Modifier
                            .height(200.dp)
                            .padding(bottom = 26.dp, end = 6.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                        horizontalAlignment = Alignment.End,
                    ) {
                        Text(maxDbText, style = MonoBadgeStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("0dB", style = MonoBadgeStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(minDbText, style = MonoBadgeStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    // Band Sliders
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        displayBands.forEach { band ->
                            val freqLabel = if (band.centerFreqHz >= 1000) {
                                val khz = band.centerFreqHz / 1000f
                                if (khz % 1f == 0f) "${khz.toInt()}kHz"
                                else String.format(Locale.US, "%.1fkHz", khz)
                            } else {
                                "${band.centerFreqHz}Hz"
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                VerticalEqBandSlider(
                                    valueMb = band.levelMillibels,
                                    minMb = eqState.minLevelMb,
                                    maxMb = eqState.maxLevelMb,
                                    enabled = liveSlidersEnabled,
                                    onValueChange = { newMb ->
                                        onBandLevelChange(band.index, newMb)
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(174.dp),
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = freqLabel,
                                    style = MonoBadgeStyle.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }

            // Bass Boost Card
            EditorialGroupCard(cornerRadius = 14.dp) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                text = "Bass Boost",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            )
                            Text(
                                text = if (eqState.isBassBoostSupported) "${eqState.bassBoostStrength / 10}%" else "Unavailable on device",
                                style = MonoBadgeStyle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        MaxmizeSwitch(
                            checked = eqState.bassBoostStrength > 0,
                            enabled = eqState.isEnabled && eqState.isBassBoostSupported,
                            onCheckedChange = { on ->
                                onBassBoostChange(if (on) lastNonZeroBass.coerceAtLeast(350) else 0)
                            },
                        )
                    }
                    if (eqState.bassBoostStrength > 0 || eqState.isBassBoostSupported) {
                        Spacer(modifier = Modifier.height(6.dp))
                        MaxmizeSlider(
                            value = eqState.bassBoostStrength.toFloat(),
                            onValueChange = { onBassBoostChange(it.toInt()) },
                            valueRange = 0f..1000f,
                            enabled = eqState.isEnabled && eqState.isBassBoostSupported,
                        )
                    }
                }
            }

            // Virtualizer Card
            EditorialGroupCard(cornerRadius = 14.dp) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                text = "Virtualizer",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            )
                            Text(
                                text = if (eqState.isVirtualizerSupported) "${eqState.virtualizerStrength / 10}%" else "Unavailable on device",
                                style = MonoBadgeStyle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        MaxmizeSwitch(
                            checked = eqState.virtualizerStrength > 0,
                            enabled = eqState.isEnabled && eqState.isVirtualizerSupported,
                            onCheckedChange = { on ->
                                onVirtualizerChange(if (on) lastNonZeroVirtualizer.coerceAtLeast(350) else 0)
                            },
                        )
                    }
                    if (eqState.virtualizerStrength > 0 || eqState.isVirtualizerSupported) {
                        Spacer(modifier = Modifier.height(6.dp))
                        MaxmizeSlider(
                            value = eqState.virtualizerStrength.toFloat(),
                            onValueChange = { onVirtualizerChange(it.toInt()) },
                            valueRange = 0f..1000f,
                            enabled = eqState.isEnabled && eqState.isVirtualizerSupported,
                        )
                    }
                }
            }

            // Speed & Stereo Balance Card
            EditorialGroupCard(cornerRadius = 14.dp) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Speed & Channel Balance",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        )
                        TextButton(onClick = { onSpeedChange(1.0f); onBalanceChange(0.0f) }) {
                            Text("Reset")
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Playback Speed", style = MaterialTheme.typography.bodyMedium)
                        Text(String.format(Locale.US, "%.2fx", playbackSpeed), style = MonoTimecodeStyle)
                    }
                    MaxmizeSlider(
                        value = playbackSpeed,
                        onValueChange = onSpeedChange,
                        valueRange = 0.5f..2.0f,
                    )

                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Stereo Balance", style = MaterialTheme.typography.bodyMedium)
                        val balText = when {
                            audioBalance < -0.05f -> "L ${(-audioBalance * 100).toInt()}%"
                            audioBalance > 0.05f -> "R ${(audioBalance * 100).toInt()}%"
                            else -> "Center"
                        }
                        Text(balText, style = MonoTimecodeStyle)
                    }
                    MaxmizeSlider(
                        value = audioBalance,
                        onValueChange = onBalanceChange,
                        valueRange = -1.0f..1.0f,
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun VerticalEqBandSlider(
    valueMb: Int,
    minMb: Int,
    maxMb: Int,
    enabled: Boolean,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val span = (maxMb - minMb).coerceAtLeast(1)
    val fraction = ((valueMb - minMb).toFloat() / span.toFloat()).coerceIn(0f, 1f)
    val activeColor = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val trackColor = MaterialTheme.colorScheme.outline

    val latestOnValueChange by rememberUpdatedState(onValueChange)

    Box(
        modifier = modifier
            .let { base ->
                if (!enabled) base else {
                    base
                        .pointerInput(minMb, maxMb, enabled) {
                            detectTapGestures { offset ->
                                val pad = 22f
                                val usable = (size.height - pad * 2f).coerceAtLeast(1f)
                                val frac = (1f - ((offset.y - pad) / usable)).coerceIn(0f, 1f)
                                latestOnValueChange((minMb + frac * span).roundToInt())
                            }
                        }
                        .pointerInput(minMb, maxMb, enabled) {
                            detectVerticalDragGestures { change, _ ->
                                change.consume()
                                val pad = 22f
                                val usable = (size.height - pad * 2f).coerceAtLeast(1f)
                                val frac = (1f - ((change.position.y - pad) / usable)).coerceIn(0f, 1f)
                                latestOnValueChange((minMb + frac * span).roundToInt())
                            }
                        }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val thumbRadius = 9.dp.toPx()
            val trackWidth = 3.dp.toPx()
            val centerX = size.width / 2f
            val topY = thumbRadius + 4.dp.toPx()
            val bottomY = size.height - thumbRadius - 4.dp.toPx()
            val usableH = (bottomY - topY).coerceAtLeast(1f)
            val centerY = topY + usableH / 2f
            val thumbY = bottomY - fraction * usableH

            // Background vertical track
            drawRoundRect(
                color = trackColor,
                topLeft = Offset(centerX - trackWidth / 2f, topY),
                size = Size(trackWidth, usableH),
                cornerRadius = CornerRadius(trackWidth / 2f, trackWidth / 2f),
            )

            // Active fill from 0dB center line to thumb
            val segTop = minOf(centerY, thumbY)
            val segHeight = kotlin.math.abs(centerY - thumbY)
            if (segHeight > 1f) {
                drawRoundRect(
                    color = activeColor,
                    topLeft = Offset(centerX - trackWidth / 2f, segTop),
                    size = Size(trackWidth, segHeight),
                    cornerRadius = CornerRadius(trackWidth / 2f, trackWidth / 2f),
                )
            }

            // Thumb
            drawCircle(
                color = activeColor,
                radius = thumbRadius,
                center = Offset(centerX, thumbY),
            )
        }
    }
}
