package com.mehad.speaktowrite.theme

import androidx.compose.ui.graphics.Color

// ─────────────────────────────────────────────────────────────────────────────
// Aurora — a dark, gradient-led identity for Speak to Write.
// One brand gradient (Emerald → Teal) drives every accent; a calm dark
// surface hierarchy sits underneath.
// ─────────────────────────────────────────────────────────────────────────────

// Base canvas: near-black with the faintest cool tint so it never reads as flat.
val AuroraBackground = Color(0xFF070A0B)
val AuroraBackgroundEnd = Color(0xFF0C1112)

// Surface tiers — used to layer cards above the background subtly.
val AuroraSurface = Color(0xFF12181A)
val AuroraSurfaceHigh = Color(0xFF1A2123)
val AuroraSurfaceHighest = Color(0xFF222B2E)
val AuroraBorder = Color(0xFF2C373A)
val AuroraBorderStrong = Color(0xFF3A484C)

// Brand gradient stops: emerald → teal.
val Emerald = Color(0xFF22C28E)
val EmeraldBright = Color(0xFF3DDC97)
val Teal = Color(0xFF2BB7B3)
val TealDeep = Color(0xFF1E8C8C)

// Text.
val TextPrimary = Color(0xFFEAF2F0)
val TextSecondary = Color(0xFF9DB0AC)
val TextTertiary = Color(0xFF637974)

// Semantic.
val AuroraSuccess = Color(0xFF3DDC97)
val AuroraWarning = Color(0xFFF5B84B)
val AuroraError = Color(0xFFFF6B6B)
val AuroraInfo = Color(0xFF5BC0EB)

// Legacy aliases kept so existing call-sites compile during migration.
val SolidGreen = Emerald
val DarkBackground = AuroraBackground
val DarkSurface = AuroraSurface
