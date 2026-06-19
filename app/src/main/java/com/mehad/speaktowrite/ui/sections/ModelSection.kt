package com.mehad.speaktowrite.ui.sections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mehad.speaktowrite.models.DownloadState
import com.mehad.speaktowrite.models.Model
import com.mehad.speaktowrite.models.ModelDownloader
import com.mehad.speaktowrite.theme.AuroraBorder
import com.mehad.speaktowrite.theme.AuroraError
import com.mehad.speaktowrite.theme.Emerald
import com.mehad.speaktowrite.ui.components.AuroraDivider
import com.mehad.speaktowrite.ui.components.BrandBrush
import com.mehad.speaktowrite.ui.components.GlassCard
import com.mehad.speaktowrite.ui.components.IconBadge
import com.mehad.speaktowrite.ui.components.SectionEyebrow

/**
 * Step 2 — pick & download the on-device speech model.
 */
@Composable
fun ModelSection(
    catalogModels: List<Model>,
    customModels: List<Model>,
    downloaders: Map<Model, ModelDownloader>,
    selectedLocalModel: String?,
    isImporting: Boolean,
    onImportClick: () -> Unit,
    onHelpClick: () -> Unit,
    onSelect: (archive: String) -> Unit,
    onDownload: (Model) -> Unit,
    onCancel: (Model) -> Unit,
    onPause: (Model) -> Unit,
    onResume: (Model) -> Unit,
    onDelete: (Model, isCustom: Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SectionEyebrow("Speech Model", step = 2)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isImporting) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Emerald, strokeWidth = 2.dp)
            } else {
                IconButton(onClick = onImportClick, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.Folder, contentDescription = "Import model from storage", tint = Emerald)
                }
            }
            IconButton(onClick = onHelpClick, modifier = Modifier.size(34.dp)) {
                Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = "Help", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        val allModels = catalogModels + customModels
        allModels.forEachIndexed { index, model ->
            val isCustom = model in customModels
            val state =
                if (isCustom) DownloadState.Done
                else downloaders[model]?.state?.collectAsState()?.value ?: DownloadState.Idle

            ModelRow(
                name = if (isCustom) "Custom · ${model.name.take(14)}" else model.name,
                sizeText = if (isCustom) "Imported" else "${model.sizeMb} MB",
                state = state,
                selected = selectedLocalModel == model.archive,
                onSelect = { onSelect(model.archive) },
                onDownload = { onDownload(model) },
                onCancel = { onCancel(model) },
                onPause = { onPause(model) },
                onResume = { onResume(model) },
                onDelete = { onDelete(model, isCustom) },
            )
            if (index < allModels.size - 1) AuroraDivider()
        }
    }
}

@Composable
private fun ModelRow(
    name: String,
    sizeText: String,
    state: DownloadState,
    selected: Boolean,
    onSelect: () -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = state is DownloadState.Done, onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (state is DownloadState.Done) {
                RadioButton(
                    selected = selected,
                    onClick = onSelect,
                    colors = RadioButtonDefaults.colors(
                        selectedColor = Emerald,
                        unselectedColor = Emerald.copy(alpha = 0.5f),
                    ),
                )
                Spacer(Modifier.width(6.dp))
            } else {
                IconBadge(icon = Icons.Default.CloudDownload, tint = Emerald, size = 34, iconSize = 18)
                Spacer(Modifier.width(10.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(sizeText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            when (state) {
                is DownloadState.Done -> {
                    if (selected) {
                        Icon(
                            Icons.Default.Verified,
                            contentDescription = "Selected",
                            tint = Emerald,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete model",
                            tint = AuroraError.copy(alpha = 0.85f),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                is DownloadState.Idle, is DownloadState.Error -> ActionIcon(Icons.Default.CloudDownload, "Download", Emerald, onDownload)
                is DownloadState.Downloading -> {
                    ActionIcon(Icons.Default.Pause, "Pause", Emerald, onPause)
                    ActionIcon(Icons.Default.Close, "Cancel", AuroraError, onCancel)
                }
                is DownloadState.Paused -> {
                    ActionIcon(Icons.Default.PlayArrow, "Resume", Emerald, onResume)
                    ActionIcon(Icons.Default.Close, "Cancel", AuroraError, onCancel)
                }
                is DownloadState.Extracting -> {
                    ActionIcon(Icons.Default.Close, "Cancel", AuroraError, onCancel)
                }
            }
        }

        // Progress / status line.
        if (state is DownloadState.Downloading || state is DownloadState.Paused || state is DownloadState.Extracting) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state is DownloadState.Extracting) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Emerald, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Extracting…", style = MaterialTheme.typography.bodySmall, color = Emerald)
                } else {
                    val progress =
                        if (state is DownloadState.Downloading) state.progress
                        else (state as DownloadState.Paused).progress
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .weight(1f)
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = Emerald,
                        trackColor = Emerald.copy(alpha = 0.18f),
                        gapSize = 0.dp,
                        drawStopIndicator = {},
                    )
                    Spacer(Modifier.width(10.dp))
                    when (state) {
                        is DownloadState.Downloading -> {
                            val speed =
                                if (state.speedKbps > 1024) "${state.speedKbps / 1024} MB/s"
                                else "${state.speedKbps} KB/s"
                            Text(
                                "${(progress * 100).toInt()}% · $speed",
                                style = MaterialTheme.typography.bodySmall,
                                color = Emerald,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        is DownloadState.Paused ->
                            Text("${(progress * 100).toInt()}% · Paused", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        if (state is DownloadState.Error) {
            Spacer(Modifier.height(6.dp))
            Text(state.message, style = MaterialTheme.typography.bodySmall, color = AuroraError, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ActionIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    tint: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(32.dp)) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(20.dp))
    }
}
