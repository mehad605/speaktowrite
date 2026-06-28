package com.mhm.speaktowrite.ui.sections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mhm.speaktowrite.theme.AuroraError
import com.mhm.speaktowrite.theme.AuroraSuccess
import com.mhm.speaktowrite.theme.Emerald
import com.mhm.speaktowrite.ui.components.AuroraDivider
import com.mhm.speaktowrite.ui.components.AuroraRow
import com.mhm.speaktowrite.ui.components.AuroraSwitch
import com.mhm.speaktowrite.ui.components.DoneBadge
import com.mhm.speaktowrite.ui.components.GlassCard
import com.mhm.speaktowrite.ui.components.IconBadge
import com.mhm.speaktowrite.ui.components.SectionEyebrow

// Amber — used for the "recommended but optional" battery warning state.
private val AuroraAmber = Color(0xFFF59E0B)

/**
 * Step 1 — grant the two permissions the floating mic needs.
 * Each row is actionable: tap to fix what's missing.
 */
@Composable
fun SetupSection(
    hasAudio: Boolean,
    hasAccessibility: Boolean,
    isBatteryOptimized: Boolean,      // true = OS can still kill us; false = exempted (good)
    showOnLockScreen: Boolean,
    onShowOnLockScreenToggle: (Boolean) -> Unit,
    sliderIsLeftEdge: Boolean,
    onSliderIsLeftEdgeToggle: (Boolean) -> Unit,
    sliderOpacity: Float,
    onSliderOpacityChange: (Float) -> Unit,
    onAudioClick: () -> Unit,
    onAccessibilityClick: () -> Unit,
    onBatteryClick: () -> Unit,
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
        AuroraDivider()
        // Battery optimisation row — shown as a soft warning (amber) when the OS
        // still has the app in its kill list, and green once exempted.
        AuroraRow(
            icon = if (isBatteryOptimized) Icons.Default.BatteryAlert
                   else Icons.Default.BatteryChargingFull,
            iconTint = if (isBatteryOptimized) AuroraAmber else AuroraSuccess,
            title = "Battery Optimisation",
            subtitle = if (isBatteryOptimized)
                "Tap to exempt — prevents OS from killing the overlay"
            else
                "Exempted — overlay will stay running",
            trailing = { if (!isBatteryOptimized) DoneBadge() },
            onClick = onBatteryClick,
            modifier = Modifier.clickable { onBatteryClick() },
        )
        AuroraDivider()
        AuroraRow(
            icon = Icons.Default.Lock,
            iconTint = Emerald,
            title = "Show on Lock Screen",
            subtitle = "Enable overlay when device is locked",
            trailing = {
                AuroraSwitch(
                    checked = showOnLockScreen,
                    onCheckedChange = onShowOnLockScreenToggle
                )
            },
            onClick = { onShowOnLockScreenToggle(!showOnLockScreen) },
            modifier = Modifier.clickable { onShowOnLockScreenToggle(!showOnLockScreen) },
        )
        AuroraDivider()
        AuroraRow(
            icon = Icons.Default.SwapHoriz,
            iconTint = Emerald,
            title = "Dock to Left Screen Edge",
            subtitle = "Toggle slider handle side position",
            trailing = {
                AuroraSwitch(
                    checked = sliderIsLeftEdge,
                    onCheckedChange = onSliderIsLeftEdgeToggle
                )
            },
            onClick = { onSliderIsLeftEdgeToggle(!sliderIsLeftEdge) },
            modifier = Modifier.clickable { onSliderIsLeftEdgeToggle(!sliderIsLeftEdge) },
        )
        AuroraDivider()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconBadge(icon = Icons.Default.Opacity, tint = Emerald)
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Handle Transparency",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "Set unrevealed transparency: ${(sliderOpacity * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            androidx.compose.material3.Slider(
                value = sliderOpacity,
                onValueChange = onSliderOpacityChange,
                valueRange = 0.0f..1.0f,
                colors = androidx.compose.material3.SliderDefaults.colors(
                    activeTrackColor = Emerald,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                    thumbColor = Emerald
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            )
        }
    }
    Spacer(Modifier.height(4.dp))
}
