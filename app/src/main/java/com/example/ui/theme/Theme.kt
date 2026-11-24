package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.example.data.model.AccentPreset
import com.example.data.model.FontChoice
import com.example.data.model.ThemeMode
import com.example.data.model.UiDensity

data class DensityMetrics(
    val rowVerticalPadding: Dp,
    val cardPadding: Dp,
    val sectionSpacing: Dp,
    val listSpacing: Dp,
)

fun uiDensityMetrics(density: UiDensity): DensityMetrics = when (density) {
    UiDensity.COMFORTABLE -> DensityMetrics(
        rowVerticalPadding = 10.dp,
        cardPadding = 16.dp,
        sectionSpacing = 20.dp,
        listSpacing = 10.dp,
    )
    UiDensity.COMPACT -> DensityMetrics(
        rowVerticalPadding = 6.dp,
        cardPadding = 12.dp,
        sectionSpacing = 14.dp,
        listSpacing = 6.dp,
    )
}

val LocalUiDensity = staticCompositionLocalOf { UiDensity.COMFORTABLE }
val LocalDensityMetrics = staticCompositionLocalOf { uiDensityMetrics(UiDensity.COMFORTABLE) }

val MaxmizeShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

fun accentColorFor(preset: AccentPreset, isDark: Boolean): Color {
    return if (isDark) {
        when (preset) {
            AccentPreset.GOLD -> GoldAccentDark
            AccentPreset.CRIMSON -> CrimsonAccentDark
            AccentPreset.SLATE -> BlueAccentDark
            AccentPreset.VIOLET -> VioletAccentDark
            AccentPreset.EMERALD -> EmeraldAccentDark
            AccentPreset.ROSE -> RoseAccentDark
            AccentPreset.MONOCHROME -> MonochromeAccentDark
        }
    } else {
        when (preset) {
            AccentPreset.GOLD -> GoldAccentLight
            AccentPreset.CRIMSON -> CrimsonAccentLight
            AccentPreset.SLATE -> BlueAccentLight
            AccentPreset.VIOLET -> VioletAccentLight
            AccentPreset.EMERALD -> EmeraldAccentLight
            AccentPreset.ROSE -> RoseAccentLight
            AccentPreset.MONOCHROME -> MonochromeAccentLight
        }
    }
}

fun onAccentColorFor(preset: AccentPreset, isDark: Boolean): Color {
    return when {
        preset == AccentPreset.GOLD && isDark -> DeepBlack
        preset == AccentPreset.MONOCHROME && isDark -> DeepBlack
        else -> Color.White
    }
}

@Composable
fun MaxmizeTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    accentPreset: AccentPreset = AccentPreset.GOLD,
    fontChoice: FontChoice = FontChoice.INTER,
    fontScale: Float = 1.0f,
    uiDensity: UiDensity = UiDensity.COMFORTABLE,
    content: @Composable () -> Unit,
) {
    val isDark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val primary = accentColorFor(accentPreset, isDark)
    val onPrimary = onAccentColorFor(accentPreset, isDark)

    val colorScheme = if (isDark) {
        darkColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primary.copy(alpha = 0.16f),
            onPrimaryContainer = primary,
            secondary = DarkSecondaryText,
            onSecondary = DeepBlack,
            secondaryContainer = DarkSurfaceVariant,
            onSecondaryContainer = DarkPrimaryText,
            tertiary = primary,
            onTertiary = onPrimary,
            background = DeepBlack,
            onBackground = DarkPrimaryText,
            surface = DarkSurface,
            onSurface = DarkPrimaryText,
            surfaceVariant = DarkSurfaceVariant,
            onSurfaceVariant = DarkSecondaryText,
            surfaceContainerLowest = DeepBlack,
            surfaceContainerLow = DarkSurface,
            surfaceContainer = DarkSurface,
            surfaceContainerHigh = DarkElevatedSurface,
            surfaceContainerHighest = Color(0xFF222222),
            outline = DarkOutline,
            outlineVariant = DarkOutlineVariant,
            error = ErrorRedDark,
            onError = Color.White,
        )
    } else {
        lightColorScheme(
            primary = primary,
            onPrimary = Color.White,
            primaryContainer = primary.copy(alpha = 0.14f),
            onPrimaryContainer = primary,
            secondary = LightSecondaryText,
            onSecondary = Color.White,
            secondaryContainer = LightSurfaceVariant,
            onSecondaryContainer = LightPrimaryText,
            tertiary = primary,
            onTertiary = Color.White,
            background = LightBackground,
            onBackground = LightPrimaryText,
            surface = LightSurface,
            onSurface = LightPrimaryText,
            surfaceVariant = LightSurfaceVariant,
            onSurfaceVariant = LightSecondaryText,
            surfaceContainerLowest = LightSurface,
            surfaceContainerLow = LightBackground,
            surfaceContainer = LightSurface,
            surfaceContainerHigh = LightElevatedSurface,
            surfaceContainerHighest = Color(0xFFE4E4EB),
            outline = LightOutline,
            outlineVariant = LightOutlineVariant,
            error = ErrorRedLight,
            onError = Color.White,
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !isDark
            insetsController.isAppearanceLightNavigationBars = !isDark
        }
    }

    val typography = remember(fontChoice, fontScale) {
        typographyFor(fontChoice, fontScale)
    }
    val densityMetrics = remember(uiDensity) { uiDensityMetrics(uiDensity) }

    CompositionLocalProvider(
        LocalUiDensity provides uiDensity,
        LocalDensityMetrics provides densityMetrics,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = MaxmizeShapes,
            content = content,
        )
    }
}
