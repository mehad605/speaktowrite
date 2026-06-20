package com.mhm.speaktowrite.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mhm.speaktowrite.theme.AuroraBorder
import com.mhm.speaktowrite.theme.AuroraError
import com.mhm.speaktowrite.theme.AuroraInfo
import com.mhm.speaktowrite.theme.AuroraSuccess
import com.mhm.speaktowrite.theme.AuroraWarning
import com.mhm.speaktowrite.theme.Emerald
import com.mhm.speaktowrite.theme.EmeraldBright
import com.mhm.speaktowrite.theme.Teal

import androidx.compose.ui.graphics.SolidColor
import com.mhm.speaktowrite.theme.TextTertiary

/** The single brand gradient, redesigned to be a solid matte color. */
val BrandBrush = SolidColor(Emerald)

/** A soft brand gradient, redesigned to be a solid matte color. */
val BrandBrushSoft = SolidColor(Emerald)

enum class AuroraStatus { SUCCESS, WARNING, ERROR, INFO }

private fun AuroraStatus.color() =
    when (this) {
        AuroraStatus.SUCCESS -> AuroraSuccess
        AuroraStatus.WARNING -> AuroraWarning
        AuroraStatus.ERROR -> AuroraError
        AuroraStatus.INFO -> AuroraInfo
    }

/**
 * Matte card — the workhorse surface of the whole UI. A solid
 * surface (Zinc 900) with a sharp 18dp corner shape and flat solid border.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(18.dp) // Sharper matte card corners (HTML: 18px)
    val border = if (highlighted) Emerald else AuroraBorder
    Column(
        modifier =
            modifier
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface) // Solid matte background, no vertical gradient
                .border(BorderStroke(1.dp, border), shape)
                .padding(contentPadding),
        content = content,
    )
}

/** Animated pulsing ring — draws an expanding solid outline around status icon. */
@Composable
fun PulsingGlow(
    color: Color = Emerald,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val transition = rememberInfiniteTransition(label = "aurora-glow")
    val alpha by
        transition.animateFloat(
            initialValue = 0.15f,
            targetValue = 0.35f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(1400),
                    repeatMode = RepeatMode.Reverse,
                ),
            label = "glow-alpha",
        )
    Box(
        modifier =
            modifier
                .drawBehind {
                    drawCircle(
                        color = color.copy(alpha = alpha),
                        radius = size.minDimension / 2f,
                    )
                }
                .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** Brand-color text. */
@Composable
fun GradientText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.displayLarge,
    brush: Brush = BrandBrush,
) {
    Text(text = text, style = style.copy(brush = brush), modifier = modifier)
}

/**
 * Eyebrow label — small, uppercase, letter-spaced text that introduces a
 * section, prefixed with a solid matte step number chip.
 */
@Composable
fun SectionEyebrow(
    text: String,
    step: Int? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (step != null) {
            Box(
                modifier =
                    Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Emerald) // Solid matte background
                        .border(BorderStroke(1.dp, Emerald), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = step.toString(),
                    color = Color(0xFF09090B), // Zinc 950 contrast text
                    fontWeight = FontWeight.Black,
                    fontSize = 11.sp,
                )
            }
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = TextTertiary, // Zinc 500 label (HTML: var(--text-muted))
        )
    }
}

/** Pill-shaped status chip (Redesigned as sharper badge). */
@Composable
fun StatusChip(
    label: String,
    status: AuroraStatus,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val tint = status.color()
    Row(
        modifier =
            modifier
                .clip(RoundedCornerShape(8.dp)) // Sharper matte badge (HTML: 8px)
                .background(tint.copy(alpha = 0.15f)) // Matches HTML em-dim
                .border(BorderStroke(1.dp, tint), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(5.dp))
        }
        Text(label, color = tint, fontWeight = FontWeight.Bold, fontSize = 11.sp)
    }
}

/** Sharper icon badge used at the start of list rows (Redesigned to 10dp rounded square). */
@Composable
fun IconBadge(
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Int = 38,
    iconSize: Int = 20,
) {
    val shape = RoundedCornerShape(10.dp) // Flat border wrapper (HTML: 10px)
    Box(
        modifier =
            modifier
                .size(size.dp)
                .clip(shape)
                .background(tint.copy(alpha = 0.15f))
                .border(BorderStroke(1.dp, tint), shape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize.dp))
    }
}

/**
 * A two-line row with a leading badge, used as the template for permission
 * and similar interactive rows. Pass [onClick] to make the whole row tappable.
 */
@Composable
fun AuroraRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (RowScope.() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.padding(0.dp) else Modifier)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon = icon, tint = iconTint)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

/** Thin divider tuned for the Aurora surface. */
@Composable
fun AuroraDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier.padding(horizontal = 16.dp),
        thickness = 1.dp,
        color = AuroraBorder,
    )
}

/** A small "completed" check used in step rows. */
@Composable
fun DoneBadge() {
    Box(
        modifier =
            Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(Emerald), // Solid matte Emerald fill
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Default.Check,
            contentDescription = "Done",
            tint = Color(0xFF09090B), // Zinc 950
            modifier = Modifier.size(16.dp),
        )
    }
}
