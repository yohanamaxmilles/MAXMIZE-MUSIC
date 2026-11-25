package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BadgeGoldBg
import com.example.ui.theme.BadgeGoldFg
import com.example.ui.theme.GoldAccentDark

data class NavDestinationItem(
    val id: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String,
    val accentHighlighted: Boolean = false,
)

@Composable
fun TopProfileSettingsButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .size(38.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(0.75.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .clickable(onClick = onClick)
            .testTag("top_settings_btn"),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Settings,
            contentDescription = "Settings and Profile",
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(19.dp),
        )
    }
}

@Composable
fun MaxmizeTopHeader(
    title: String,
    subtitle: String? = null,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.3.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(2.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = 27.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp,
                ),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            actions()
            TopProfileSettingsButton(onClick = onOpenSettings)
        }
    }
}

@Composable
fun MaxmizeBottomBar(
    items: List<NavDestinationItem>,
    selectedId: String?,
    onSelect: (NavDestinationItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding(),
    ) {
        HorizontalDivider(thickness = 0.75.dp, color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp)
                .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { item ->
                val selected = item.id == selectedId
                val djGold = GoldAccentDark
                val iconTint by animateColorAsState(
                    targetValue = when {
                        selected && item.accentHighlighted -> djGold
                        selected -> MaterialTheme.colorScheme.primary
                        item.accentHighlighted -> djGold.copy(alpha = 0.85f)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    label = "navTint",
                )
                val badgeBg by animateColorAsState(
                    targetValue = when {
                        selected && item.accentHighlighted -> BadgeGoldBg
                        selected -> MaterialTheme.colorScheme.primaryContainer
                        item.accentHighlighted -> djGold.copy(alpha = 0.08f)
                        else -> Color.Transparent
                    },
                    label = "navBg",
                )
                val interactionSource = remember { MutableInteractionSource() }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(
                            interactionSource = interactionSource,
                            indication = ripple(bounded = true),
                            onClick = { onSelect(item) },
                        )
                        .testTag(item.testTag),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 40.dp, height = 28.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(badgeBg)
                            .let { mod ->
                                if (item.accentHighlighted) {
                                    mod.border(
                                        width = if (selected) 1.dp else 0.75.dp,
                                        color = if (selected) djGold else djGold.copy(alpha = 0.32f),
                                        shape = RoundedCornerShape(10.dp),
                                    )
                                } else mod
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                            contentDescription = item.label,
                            tint = iconTint,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = if (selected || item.accentHighlighted) FontWeight.SemiBold else FontWeight.Medium,
                        ),
                        color = iconTint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
fun MaxmizeNavigationRail(
    items: List<NavDestinationItem>,
    selectedId: String?,
    onSelect: (NavDestinationItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxHeight()) {
        Column(
            modifier = Modifier
                .width(84.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.background)
                .padding(vertical = 16.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items.forEach { item ->
                val selected = item.id == selectedId
                val tint = when {
                    selected && item.accentHighlighted -> BadgeGoldFg
                    selected -> MaterialTheme.colorScheme.primary
                    item.accentHighlighted -> BadgeGoldFg.copy(alpha = 0.85f)
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                val bg = when {
                    selected && item.accentHighlighted -> BadgeGoldBg
                    selected -> MaterialTheme.colorScheme.primaryContainer
                    item.accentHighlighted -> BadgeGoldFg.copy(alpha = 0.08f)
                    else -> Color.Transparent
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(bg)
                        .let { mod ->
                            if (item.accentHighlighted) {
                                mod.border(
                                    width = if (selected) 1.dp else 0.75.dp,
                                    color = if (selected) BadgeGoldFg else BadgeGoldFg.copy(alpha = 0.30f),
                                    shape = RoundedCornerShape(12.dp),
                                )
                            } else mod
                        }
                        .clickable { onSelect(item) }
                        .padding(vertical = 10.dp)
                        .testTag(item.testTag),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                        contentDescription = item.label,
                        tint = tint,
                        modifier = Modifier.size(21.dp),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = if (selected || item.accentHighlighted) FontWeight.SemiBold else FontWeight.Medium,
                        ),
                        color = tint,
                        maxLines = 1,
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .width(0.75.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
    }
}

@Composable
fun MaxmizeSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val trackColor by animateColorAsState(
        targetValue = when {
            !enabled -> MaterialTheme.colorScheme.surfaceContainerHigh
            checked -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.outline
        },
        label = "switchTrack",
    )
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 20.dp else 3.dp,
        animationSpec = spring(stiffness = 600f),
        label = "switchThumb",
    )

    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onCheckedChange(!checked) },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 44.dp, height = 26.dp)
                .clip(CircleShape)
                .background(trackColor),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .offset(x = thumbOffset)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(
                        if (checked && MaterialTheme.colorScheme.primary == Color(0xFFFAFAFA)) {
                            Color(0xFF08080A)
                        } else {
                            Color.White
                        }
                    ),
            )
        }
    }
}

@Composable
fun MaxmizeSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit = {},
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    enabled: Boolean = true,
    trackHeight: Dp = 3.5.dp,
    thumbRadius: Dp = 7.dp,
    modifier: Modifier = Modifier,
) {
    val span = (valueRange.endInclusive - valueRange.start).coerceAtLeast(0.0001f)
    val fraction = ((value - valueRange.start) / span).coerceIn(0f, 1f)
    val activeColor = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val inactiveColor = MaterialTheme.colorScheme.outline

    val density = LocalDensity.current
    val thumbRadiusPx = with(density) { thumbRadius.toPx() }
    val trackHeightPx = with(density) { trackHeight.toPx() }

    val latestOnValueChange by rememberUpdatedState(onValueChange)
    val latestOnFinished by rememberUpdatedState(onValueChangeFinished)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
            .let { base ->
                if (!enabled) base else {
                    base
                        .pointerInput(valueRange, enabled) {
                            detectTapGestures { offset ->
                                val w = size.width.toFloat().coerceAtLeast(1f)
                                val frac = ((offset.x - thumbRadiusPx) / (w - thumbRadiusPx * 2f).coerceAtLeast(1f)).coerceIn(0f, 1f)
                                latestOnValueChange(valueRange.start + frac * span)
                                latestOnFinished()
                            }
                        }
                        .pointerInput(valueRange, enabled) {
                            detectHorizontalDragGestures(
                                onDragStart = { start ->
                                    val w = size.width.toFloat().coerceAtLeast(1f)
                                    val frac = ((start.x - thumbRadiusPx) / (w - thumbRadiusPx * 2f).coerceAtLeast(1f)).coerceIn(0f, 1f)
                                    latestOnValueChange(valueRange.start + frac * span)
                                },
                                onHorizontalDrag = { change, _ ->
                                    change.consume()
                                    val w = size.width.toFloat().coerceAtLeast(1f)
                                    val frac = ((change.position.x - thumbRadiusPx) / (w - thumbRadiusPx * 2f).coerceAtLeast(1f)).coerceIn(0f, 1f)
                                    latestOnValueChange(valueRange.start + frac * span)
                                },
                                onDragEnd = { latestOnFinished() },
                                onDragCancel = { latestOnFinished() },
                            )
                        }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(32.dp)) {
            val usableWidth = (size.width - thumbRadiusPx * 2f).coerceAtLeast(1f)
            val centerY = size.height / 2f
            val thumbX = thumbRadiusPx + fraction * usableWidth
            val corner = CornerRadius(trackHeightPx / 2f, trackHeightPx / 2f)

            drawRoundRect(
                color = inactiveColor,
                topLeft = Offset(thumbRadiusPx, centerY - trackHeightPx / 2f),
                size = Size(usableWidth, trackHeightPx),
                cornerRadius = corner,
            )
            drawRoundRect(
                color = activeColor,
                topLeft = Offset(thumbRadiusPx, centerY - trackHeightPx / 2f),
                size = Size((thumbX - thumbRadiusPx).coerceAtLeast(0f), trackHeightPx),
                cornerRadius = corner,
            )
            drawCircle(
                color = activeColor,
                radius = thumbRadiusPx,
                center = Offset(thumbX, centerY),
            )
        }
    }
}

@Composable
fun EditorialChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val bgColor = when {
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surface
    }
    val textColor = when {
        selected -> MaterialTheme.colorScheme.onPrimary
        enabled -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant

    Box(
        modifier = modifier
            .height(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .border(0.75.dp, borderColor, RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            ),
            color = textColor,
            maxLines = 1,
        )
    }
}

@Composable
fun EditorialGroupCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 14.dp,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(cornerRadius)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(0.75.dp, borderColor, shape),
        content = content,
    )
}

@Composable
fun EditorialSectionHeader(
    title: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 16.dp, top = 20.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (actionLabel != null && onAction != null) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onAction)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}
