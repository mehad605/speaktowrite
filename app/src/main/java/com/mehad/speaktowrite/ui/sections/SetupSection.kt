package com.mehad.speaktowrite.ui.sections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mehad.speaktowrite.theme.AuroraError
import com.mehad.speaktowrite.theme.AuroraSuccess
import com.mehad.speaktowrite.ui.components.AuroraDivider
import com.mehad.speaktowrite.ui.components.AuroraRow
import com.mehad.speaktowrite.ui.components.DoneBadge
import com.mehad.speaktowrite.ui.components.GlassCard
import com.mehad.speaktowrite.ui.components.SectionEyebrow

/**
 * Step 1 — grant the two permissions the floating mic needs.
 * Each row is actionable: tap to fix what's missing.
 */
@Composable
fun SetupSection(
    hasAudio: Boolean,
    hasAccessibility: Boolean,
    onAudioClick: () -> Unit,
    onAccessibilityClick: () -> Unit,
) {
    SectionEyebrow("Setup", step = 1, modifier = Modifier.padding(start = 24.dp, top = 8.dp))
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        AuroraRow(
            icon = Icons.Default.Mic,
            iconTint = if (hasAudio) AuroraSuccess else AuroraError,
            title = "Microphone",
            subtitle = if (hasAudio) "Permission granted" else "Tap to grant microphone access",
            trailing = { if (hasAudio) DoneBadge() },
            onClick = onAudioClick,
            modifier = Modifier.clickable { onAudioClick() },
        )
        AuroraDivider()
        AuroraRow(
            icon = Icons.Default.AccessibilityNew,
            iconTint = if (hasAccessibility) AuroraSuccess else AuroraError,
            title = "Accessibility Service",
            subtitle = if (hasAccessibility) "Enabled — floating mic active"
                       else "Tap to enable (opens Settings)",
            trailing = { if (hasAccessibility) DoneBadge() },
            onClick = onAccessibilityClick,
            modifier = Modifier.clickable { onAccessibilityClick() },
        )
    }
    Spacer(Modifier.height(4.dp))
}
