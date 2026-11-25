package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Shared visual tokens for the Maxmize Music neon-lime redesign.
 * Keep these values centralized so every screen can share one visual language.
 */
object MaxmizeDesign {
    val Background = Color(0xFF09090D)
    val Surface = Color(0xFF13131A)
    val ElevatedSurface = Color(0xFF1B1B24)
    val GlassSurface = Color(0xB81A1A22)
    val Hairline = Color(0x26FFFFFF)
    val TextPrimary = Color(0xFFF7F7F8)
    val TextSecondary = Color(0xFFAAAAB5)
    val Accent = Color(0xFFC8E64C)
    val AccentSoft = Color(0x26C8E64C)
    val Scrim = Color(0xE609090D)

    val CardShape = RoundedCornerShape(22.dp)
    val CompactCardShape = RoundedCornerShape(16.dp)
    val PillShape = RoundedCornerShape(100.dp)
}
