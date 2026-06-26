package com.mhm.speaktowrite.ui.dialogs

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import com.mhm.speaktowrite.theme.AuroraError
import com.mhm.speaktowrite.theme.AuroraSurface
import com.mhm.speaktowrite.theme.Emerald
import com.mhm.speaktowrite.ui.components.AuroraDivider
import com.mhm.speaktowrite.ui.components.BrandButton
import com.mhm.speaktowrite.ui.main.PromptPreset
import java.io.File

/** Indeterminate "loading model into memory" dialog. */
@Composable
fun LoadingModelDialog() {
    Dialog(onDismissRequest = {}) {
        Card(
            shape = RoundedCornerShape(18.dp), // Sharper matte shape
            colors = CardDefaults.cardColors(containerColor = AuroraSurface),
            border = BorderStroke(1.dp, Emerald), // Solid border
        ) {
            Row(
                modifier = Modifier.padding(24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(color = Emerald)
                Spacer(Modifier.width(16.dp))
                Text(
                    "Loading model into memory…",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

/** Custom-model import help dialog. */
@Composable
fun HelpDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(18.dp), // Sharper matte shape
            colors = CardDefaults.cardColors(containerColor = AuroraSurface),
            border = BorderStroke(1.dp, Emerald), // Solid border
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    "Importing custom models",
                    style = MaterialTheme.typography.titleLarge,
                    color = Emerald,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "You can import offline speech-to-text models for sherpa-onnx.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(12.dp))
                Text("Requirements", style = MaterialTheme.typography.labelMedium, color = Emerald)
                Spacer(Modifier.height(4.dp))
                Text(
                    "• A compressed archive (.zip, .tar.bz2, .tar.gz)\n" +
                        "• Contains the model's .onnx files and tokens.txt",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Tip: grab a compatible Android model from the k2-fsa/sherpa-onnx releases.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(20.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Got it", color = Emerald, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

/** Add / edit prompt dialog. */
@Composable
fun PromptEditorDialog(
    editing: PromptPreset?,
    onDismiss: () -> Unit,
    onSave: (title: String, content: String) -> Unit,
) {
    var title by remember { mutableStateOf(editing?.title ?: "") }
    var content by remember { mutableStateOf(editing?.content ?: "") }
    // Guard against empty save.
    val canSave = title.isNotBlank() && content.isNotBlank()

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(18.dp), // Sharper matte shape
            colors = CardDefaults.cardColors(containerColor = AuroraSurface),
            border = BorderStroke(1.dp, Emerald), // Solid border
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    if (editing == null) "New prompt" else "Edit prompt",
                    style = MaterialTheme.typography.titleLarge,
                    color = Emerald,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp), // Sharper corners for matte look
                    colors = fieldColors(),
                )
                Spacer(Modifier.height(12.dp))
                // Multi-line content field — tall and internally scrollable so
                // longer prompts can be read and edited comfortably.
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Content") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp, max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    shape = RoundedCornerShape(8.dp), // Sharper corners for matte look
                    colors = fieldColors(),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "${content.length} characters",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.End,
                )
                Spacer(Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Spacer(Modifier.width(8.dp))
                    BrandButton(text = "Save", enabled = canSave, onClick = { onSave(title, content) })
                }
            }
        }
    }
}

/** Confirm deletion dialog. */
@Composable
fun DeletePromptConfirmDialog(
    promptTitle: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(18.dp), // Sharper matte shape
            colors = CardDefaults.cardColors(containerColor = AuroraSurface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error), // Solid border
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    "Delete Prompt",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Are you sure you want to delete the prompt \"$promptTitle\"? This cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        onClick = onConfirm,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Delete", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/** Export configuration with option to toggle API key. */
@Composable
fun ExportConfigDialog(
    onDismiss: () -> Unit,
    onConfirm: (includeApiKey: Boolean) -> Unit
) {
    var includeApiKey by remember { mutableStateOf(true) }
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(18.dp), // Sharper matte shape
            colors = CardDefaults.cardColors(containerColor = AuroraSurface),
            border = BorderStroke(1.dp, Emerald), // Solid border
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    "Export Configuration",
                    style = MaterialTheme.typography.titleLarge,
                    color = Emerald,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Export your custom prompts and application configuration.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = includeApiKey,
                        onCheckedChange = { includeApiKey = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = Emerald,
                            checkmarkColor = MaterialTheme.colorScheme.background
                        )
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            "Include API Key",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "Your Google AI Studio API key will be saved in the export.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.width(8.dp))
                    BrandButton(text = "Export", onClick = { onConfirm(includeApiKey) })
                }
            }
        }
    }
}

@Composable
private fun fieldColors() = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    focusedBorderColor = Emerald,
    unfocusedBorderColor = Emerald.copy(alpha = 0.4f),
    cursorColor = Emerald,
    focusedLabelColor = Emerald,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
    unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
)

// ── Diagnostics ───────────────────────────────────────────────────────────────

/**
 * Shows the last N lines of the service log and offers a "Share" button so
 * any user — even a non-technical one who installed the APK from GitHub — can
 * send the log to the developer with one tap.
 */
@Composable
fun DiagnosticsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val logFile = remember { File(context.filesDir, "service_log.txt") }
    var logLines by remember { mutableStateOf("Loading…") }
    val previewLines = 80

    // Read last N lines off the main thread
    LaunchedEffect(Unit) {
        logLines = if (!logFile.exists()) {
            "No log file yet.\n\nThe service hasn't run since the logger was added, " +
                "or the accessibility service hasn't been enabled yet."
        } else {
            val all = logFile.readLines()
            if (all.isEmpty()) "Log file exists but is empty."
            else all.takeLast(previewLines).joinToString("\n")
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = AuroraSurface),
            border = BorderStroke(1.dp, AuroraError),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // ── Header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Diagnostics",
                            style = MaterialTheme.typography.titleLarge,
                            color = AuroraError,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "Last $previewLines log lines",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // ── Log content (scrollable, monospace)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp, max = 340.dp)
                        .background(
                            Color(0xFF0D0D0F),
                            RoundedCornerShape(10.dp)
                        )
                        .padding(10.dp)
                ) {
                    val vScroll = rememberScrollState(Int.MAX_VALUE)
                    Text(
                        text = logLines,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                        color = Color(0xFFB0C8B0),  // soft green terminal colour
                        modifier = Modifier
                            .verticalScroll(vScroll)
                            .horizontalScroll(rememberScrollState()),
                    )
                }

                Spacer(Modifier.height(4.dp))
                Text(
                    "Scroll up for older entries",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.End,
                )

                Spacer(Modifier.height(16.dp))
                AuroraDivider()
                Spacer(Modifier.height(12.dp))

                // ── Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Close", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.width(8.dp))
                    BrandButton(
                        text = "Share Log",
                        onClick = {
                            shareLogFile(context, logFile)
                        },
                    )
                }
            }
        }
    }
}

private fun shareLogFile(context: android.content.Context, logFile: File) {
    if (!logFile.exists()) {
        android.widget.Toast.makeText(
            context,
            "No log file found — enable the accessibility service first",
            android.widget.Toast.LENGTH_LONG,
        ).show()
        return
    }

    // FileProvider URI — safe to share across apps
    val uri = try {
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            logFile,
        )
    } catch (e: Exception) {
        // Fallback: share raw text if FileProvider isn't configured
        val text = try { logFile.readText() } catch (_: Exception) { "Could not read log." }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "SpeakToWrite diagnostic log")
            putExtra(Intent.EXTRA_TEXT, text.takeLast(8000)) // system clipboard limit
        }
        context.startActivity(Intent.createChooser(intent, "Share log via…"))
        return
    }

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "SpeakToWrite diagnostic log")
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share log via…"))
}
