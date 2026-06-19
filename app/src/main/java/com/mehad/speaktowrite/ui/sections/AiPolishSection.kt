package com.mehad.speaktowrite.ui.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mehad.speaktowrite.theme.AuroraError
import com.mehad.speaktowrite.theme.AuroraSurface
import com.mehad.speaktowrite.theme.Emerald
import com.mehad.speaktowrite.ui.components.AuroraDivider
import com.mehad.speaktowrite.ui.components.AuroraSwitch
import com.mehad.speaktowrite.ui.components.GlassCard
import com.mehad.speaktowrite.ui.components.IconBadge
import com.mehad.speaktowrite.ui.components.SectionEyebrow
import com.mehad.speaktowrite.ui.main.PromptPreset

/**
 * Step 3 — optional AI polish. Off by default; when on, the chosen prompt
 * cleans up transcripts before they're pasted.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiPolishSection(
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    isValidKey: Boolean?,
    isCheckingKey: Boolean,
    onRefreshModels: () -> Unit,
    selectedAiModel: String,
    aiModels: List<String>,
    expandedDropdown: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelectAiModel: (String) -> Unit,
    cleanupEnabled: Boolean,
    onCleanupToggle: (Boolean) -> Unit,
    prompts: List<PromptPreset>,
    selectedPromptId: String,
    onSelectPrompt: (String) -> Unit,
    onAddPrompt: () -> Unit,
    onEditPrompt: (PromptPreset) -> Unit,
    onDeletePrompt: (PromptPreset) -> Unit,
    onImportConfig: () -> Unit,
    onExportConfig: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SectionEyebrow("AI Polish", step = 3)
        AuroraSwitch(checked = cleanupEnabled, onCheckedChange = onCleanupToggle)
    }

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        // Intro line describing the feature.
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(icon = Icons.Default.AutoAwesome, tint = Emerald)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    "Smart cleanup with Gemini",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    if (cleanupEnabled) "On — transcripts are polished before pasting"
                    else "Off — raw transcripts are pasted as-is",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        AuroraDivider()

        // API key + model picker (only meaningful when cleanup is on, but always editable).
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Google AI Studio API Key", style = MaterialTheme.typography.labelMedium, color = Emerald)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = apiKey,
                onValueChange = onApiKeyChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Enter API key", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                trailingIcon = {
                    when {
                        isCheckingKey -> CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Emerald, strokeWidth = 2.dp)
                        isValidKey == true -> Icon(Icons.Default.Check, contentDescription = "Valid key", tint = Emerald)
                        isValidKey == false -> Icon(Icons.Default.Warning, contentDescription = "Invalid key", tint = AuroraError)
                    }
                },
                colors = auroraFieldColors(),
            )

            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Model", style = MaterialTheme.typography.labelMedium, color = Emerald)
                IconButton(onClick = onRefreshModels, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh models", tint = Emerald)
                }
            }
            Spacer(Modifier.height(8.dp))
            ExposedDropdownMenuBox(
                expanded = expandedDropdown,
                onExpandedChange = onExpandedChange,
            ) {
                OutlinedTextField(
                    value = selectedAiModel,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedDropdown) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                    colors = auroraFieldColors(),
                )
                ExposedDropdownMenu(
                    expanded = expandedDropdown,
                    onDismissRequest = { onExpandedChange(false) },
                ) {
                    aiModels.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option, color = MaterialTheme.colorScheme.onSurface) },
                            onClick = { onSelectAiModel(option); onExpandedChange(false) },
                        )
                    }
                }
            }
        }

        AuroraDivider()

        // Prompts.
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Prompts", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                IconButton(onClick = onAddPrompt) {
                    Icon(Icons.Default.Add, contentDescription = "Add prompt", tint = Emerald)
                }
            }
            if (prompts.isEmpty()) {
                Text(
                    "No prompts yet — tap + to create one.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            prompts.forEach { prompt ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelectPrompt(prompt.id) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = selectedPromptId == prompt.id,
                        onClick = { onSelectPrompt(prompt.id) },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = Emerald,
                            unselectedColor = Emerald.copy(alpha = 0.5f),
                        ),
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(prompt.title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                        Text(
                            prompt.content,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Row {
                        IconButton(onClick = { onEditPrompt(prompt) }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit prompt", tint = Emerald)
                        }
                        IconButton(onClick = { onDeletePrompt(prompt) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete prompt", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }

        AuroraDivider()

        // Configuration actions (Export / Import)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Configuration",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onImportConfig) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Import",
                        tint = Emerald,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Import", color = Emerald, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onExportConfig) {
                    Icon(
                        imageVector = Icons.Default.Upload,
                        contentDescription = "Export",
                        tint = Emerald,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Export", color = Emerald, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun auroraFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    focusedBorderColor = Emerald,
    unfocusedBorderColor = Emerald.copy(alpha = 0.4f),
    cursorColor = Emerald,
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    focusedTrailingIconColor = Emerald,
    unfocusedTrailingIconColor = Emerald.copy(alpha = 0.6f),
    focusedLabelColor = Emerald,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
)
