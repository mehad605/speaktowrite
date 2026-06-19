package com.mhm.speaktowrite.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * Aurora is always dark — the gradient accents only sing against a deep canvas,
 * and "private, local voice dictation" should feel calm and low-light. Dynamic
 * color is intentionally disabled so the brand identity stays consistent.
 */
private val Ink = androidx.compose.ui.graphics.Color(0xFF0A0A0A)

private val AuroraColorScheme =
    darkColorScheme(
        primary = Emerald,
        onPrimary = Ink,
        primaryContainer = Emerald.copy(alpha = 0.16f),
        onPrimaryContainer = EmeraldBright,
        secondary = Teal,
        onSecondary = Ink,
        tertiary = TealDeep,
        background = AuroraBackground,
        onBackground = TextPrimary,
        surface = AuroraSurface,
        onSurface = TextPrimary,
        surfaceVariant = AuroraSurfaceHigh,
        onSurfaceVariant = TextSecondary,
        surfaceContainer = AuroraSurface,
        surfaceContainerHigh = AuroraSurfaceHigh,
        surfaceContainerHighest = AuroraSurfaceHighest,
        outline = AuroraBorder,
        outlineVariant = AuroraBorderStrong,
        error = AuroraError,
        onError = Ink,
    )

@Composable
fun SpeakToWriteTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AuroraColorScheme,
        typography = AuroraTypography,
        content = content,
    )
}
