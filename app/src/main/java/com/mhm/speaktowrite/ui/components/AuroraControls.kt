package com.mhm.speaktowrite.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mhm.speaktowrite.theme.AuroraBorder
import com.mhm.speaktowrite.theme.Emerald
import com.mhm.speaktowrite.theme.TextPrimary
import com.mhm.speaktowrite.theme.TextTertiary

/**
 * Primary action button — brand gradient fill, dark text, subtle.
 * Used sparingly (e.g. the "Save" action in dialogs).
 */
@Composable
fun BrandButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    trailingIcon: ImageVector? = null,
    onClick: () -> Unit,
) {
    val alpha = if (enabled) 1f else 0.4f
    Row(
        modifier =
            modifier
                .clip(RoundedCornerShape(14.dp))
                .background(BrandBrush, alpha = alpha)
                .border(BorderStroke(1.dp, Emerald.copy(alpha = 0.6f)), RoundedCornerShape(14.dp))
                .clickable(enabled = enabled) { onClick() }
                .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = Color(0xFF06120E), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontSize = 14.sp)
        if (trailingIcon != null) {
            Spacer(Modifier.width(6.dp))
            Icon(trailingIcon, contentDescription = null, tint = Color(0xFF06120E), modifier = Modifier.widthIn(max = 16.dp))
        }
    }
}

/**
 * Secondary (outline) button — transparent fill, brand border.
 */
@Composable
fun OutlineButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            modifier
                .clip(RoundedCornerShape(14.dp))
                .background(Emerald.copy(alpha = 0.06f))
                .border(BorderStroke(1.dp, Emerald.copy(alpha = 0.5f)), RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Emerald, modifier = Modifier.widthIn(max = 16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = Emerald, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, fontSize = 13.sp)
    }
}

/**
 * Rounded text-field styled to match Aurora — focus drives a brand ring.
 * A hand-rolled wrapper over BasicTextField so we avoid the Material
 * outlined default look.
 */
@Composable
fun AuroraTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = false,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: @Composable (() -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val borderColor = if (focused) Emerald.copy(alpha = 0.8f) else AuroraBorder
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(BorderStroke(1.dp, borderColor), RoundedCornerShape(14.dp)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                enabled = enabled,
                singleLine = singleLine,
                textStyle =
                    TextStyle(
                        color = TextPrimary,
                        fontSize = 15.sp,
                    ),
                cursorBrush = SolidColor(Emerald),
                visualTransformation = visualTransformation,
                keyboardOptions = keyboardOptions,
                interactionSource = interaction,
                decorationBox = { inner ->
                    if (value.isEmpty()) {
                        Text(placeholder, color = TextTertiary, fontSize = 15.sp)
                    }
                    inner()
                },
            )
            if (trailing != null) {
                Spacer(Modifier.width(8.dp))
                trailing()
            }
        }
    }
}

/** Aurora-styled toggle. */
@Composable
fun AuroraSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        colors =
            SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF06120E),
                checkedTrackColor = Emerald,
                checkedBorderColor = Emerald,
                uncheckedThumbColor = TextTertiary,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                uncheckedBorderColor = AuroraBorder,
            ),
    )
}

