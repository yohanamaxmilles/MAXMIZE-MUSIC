package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.FontChoice

val FigtreeFontFamily = FontFamily(
    Font(R.font.figtree_regular, FontWeight.Normal),
    Font(R.font.figtree_medium, FontWeight.Medium),
    Font(R.font.figtree_semibold, FontWeight.SemiBold),
    Font(R.font.figtree_bold, FontWeight.Bold),
)

val InterFontFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

val ManropeFontFamily = FontFamily(
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold),
)

val PlusJakartaFontFamily = FontFamily(
    Font(R.font.plus_jakarta_regular, FontWeight.Normal),
    Font(R.font.plus_jakarta_medium, FontWeight.Medium),
    Font(R.font.plus_jakarta_semibold, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_bold, FontWeight.Bold),
)

val JetBrainsMonoFontFamily = FontFamily(
    Font(R.font.jetbrains_mono, FontWeight.Normal),
    Font(R.font.jetbrains_mono, FontWeight.Medium),
)

fun fontFamilyFor(choice: FontChoice): FontFamily = when (choice) {
    FontChoice.FIGTREE -> FigtreeFontFamily
    FontChoice.INTER -> InterFontFamily
    FontChoice.MANROPE -> ManropeFontFamily
    FontChoice.PLUS_JAKARTA -> PlusJakartaFontFamily
}

fun typographyFor(
    fontChoice: FontChoice = FontChoice.FIGTREE,
    fontScale: Float = 1.0f,
): Typography {
    val family = fontFamilyFor(fontChoice)
    val s = fontScale.coerceIn(0.88f, 1.18f)
    return Typography(
        displayMedium = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Bold,
            fontSize = (40f * s).sp,
            lineHeight = (46f * s).sp,
            letterSpacing = (-1.0).sp,
        ),
        displaySmall = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Bold,
            fontSize = (34f * s).sp,
            lineHeight = (40f * s).sp,
            letterSpacing = (-0.8).sp,
        ),
        headlineLarge = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Bold,
            fontSize = (30f * s).sp,
            lineHeight = (36f * s).sp,
            letterSpacing = (-0.6).sp,
        ),
        headlineMedium = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Bold,
            fontSize = (26f * s).sp,
            lineHeight = (32f * s).sp,
            letterSpacing = (-0.5).sp,
        ),
        headlineSmall = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Bold,
            fontSize = (22f * s).sp,
            lineHeight = (28f * s).sp,
            letterSpacing = (-0.4).sp,
        ),
        titleLarge = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Bold,
            fontSize = (20f * s).sp,
            lineHeight = (26f * s).sp,
            letterSpacing = (-0.3).sp,
        ),
        titleMedium = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.SemiBold,
            fontSize = (17f * s).sp,
            lineHeight = (23f * s).sp,
            letterSpacing = (-0.15).sp,
        ),
        titleSmall = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.SemiBold,
            fontSize = (15f * s).sp,
            lineHeight = (20f * s).sp,
            letterSpacing = (-0.05).sp,
        ),
        bodyLarge = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Normal,
            fontSize = (16f * s).sp,
            lineHeight = (24f * s).sp,
            letterSpacing = (0).sp,
        ),
        bodyMedium = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Normal,
            fontSize = (15f * s).sp,
            lineHeight = (21f * s).sp,
            letterSpacing = (0).sp,
        ),
        bodySmall = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Normal,
            fontSize = (13f * s).sp,
            lineHeight = (18f * s).sp,
            letterSpacing = (0.05).sp,
        ),
        labelLarge = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.SemiBold,
            fontSize = (15f * s).sp,
            lineHeight = (20f * s).sp,
            letterSpacing = (0).sp,
        ),
        labelMedium = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Medium,
            fontSize = (13f * s).sp,
            lineHeight = (17f * s).sp,
            letterSpacing = (0.1).sp,
        ),
        labelSmall = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Medium,
            fontSize = (11f * s).sp,
            lineHeight = (15f * s).sp,
            letterSpacing = (0.2).sp,
        ),
    )
}

val MaxmizeTypography = typographyFor(FontChoice.FIGTREE, 1.0f)

val MonoTimecodeStyle = TextStyle(
    fontFamily = JetBrainsMonoFontFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 13.sp,
    lineHeight = 18.sp,
    fontFeatureSettings = "tnum",
)

val MonoStatStyle = TextStyle(
    fontFamily = JetBrainsMonoFontFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 16.sp,
    lineHeight = 21.sp,
    fontFeatureSettings = "tnum",
)

val MonoBadgeStyle = TextStyle(
    fontFamily = JetBrainsMonoFontFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 11.sp,
    lineHeight = 15.sp,
    letterSpacing = 0.3.sp,
    fontFeatureSettings = "tnum",
)
