package com.mehad.speaktowrite.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.Dialog
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mehad.speaktowrite.SpeakToWriteAccessibilityService
import com.mehad.speaktowrite.theme.SpeakToWriteTheme
import com.mehad.speaktowrite.models.MODEL_CATALOG
import com.mehad.speaktowrite.models.ModelDownloader
import com.mehad.speaktowrite.models.DownloadState
import com.mehad.speaktowrite.models.TranscriberManager
import kotlinx.coroutines.launch

val DarkBackground = Color(0xFF0A0A0A)
val SolidGreen = Color(0xFF388E3C) // Luscious forest green
val DarkSurface = Color(0xFF141414)

data class PromptPreset(
    val id: String,
    var title: String,
    var content: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasAudioPermission by remember { 
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) 
    }
    var hasAccessibilityPermission by remember { 
        mutableStateOf(SpeakToWriteAccessibilityService.isEnabled(context)) 
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasAudioPermission = isGranted
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasAudioPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                hasAccessibilityPermission = SpeakToWriteAccessibilityService.isEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val coroutineScope = rememberCoroutineScope()
    val downloaders = remember {
        MODEL_CATALOG.associateWith { ModelDownloader(context, it) }
    }

    val isLoadingModel by TranscriberManager.isLoading.collectAsState()
    val selectedLocalModel by TranscriberManager.currentModel.collectAsState()

    val settingsManager = remember { com.mehad.speaktowrite.models.SettingsManager(context) }
    var apiKey by remember { mutableStateOf(settingsManager.apiKey) }
    var expandedModelDropdown by remember { mutableStateOf(false) }
    var selectedAiModel by remember { mutableStateOf(settingsManager.selectedAiModel) }
    var cleanupEnabled by remember { mutableStateOf(settingsManager.cleanupEnabled) }
    
    var aiModels by remember { mutableStateOf(listOf("gemini-1.5-flash", "gemini-1.5-pro", "gemini-1.0-pro")) }
    var isCheckingKey by remember { mutableStateOf(false) }
    var isValidKey by remember { mutableStateOf<Boolean?>(null) }
    
    fun verifyKeyAndFetchModels(key: String) {
        if (key.isBlank()) {
            isValidKey = null
            return
        }
        isCheckingKey = true
        coroutineScope.launch {
            val result = com.mehad.speaktowrite.models.GeminiClient.getAvailableModels(key)
            if (result.isSuccess) {
                isValidKey = true
                val models = result.getOrNull()
                if (!models.isNullOrEmpty()) {
                    aiModels = models
                    if (!models.contains(selectedAiModel)) {
                        selectedAiModel = models.first()
                        settingsManager.selectedAiModel = selectedAiModel
                    }
                }
            } else {
                isValidKey = false
            }
            isCheckingKey = false
        }
    }

    LaunchedEffect(Unit) {
        if (apiKey.isNotBlank()) {
            verifyKeyAndFetchModels(apiKey)
        }
    }

    val prompts = remember {
        mutableStateListOf(*settingsManager.prompts.toTypedArray())
    }
    var selectedPromptId by remember { mutableStateOf(settingsManager.selectedPromptId) }

    var showPromptDialog by remember { mutableStateOf(false) }
    var editingPrompt by remember { mutableStateOf<PromptPreset?>(null) }
    var showHelpDialog by remember { mutableStateOf(false) }
    var isImportingModel by remember { mutableStateOf(false) }
    
    var customModels by remember { mutableStateOf(emptyList<com.mehad.speaktowrite.models.Model>()) }
    fun refreshCustomModels() {
        val outDir = java.io.File(context.filesDir, "models")
        if (outDir.exists()) {
            val folders = outDir.listFiles()?.filter { it.isDirectory } ?: emptyList()
            val catalogArchives = MODEL_CATALOG.map { it.archive }
            val custom = folders.filter { it.name !in catalogArchives }.map {
                com.mehad.speaktowrite.models.Model(it.name, it.name, 0)
            }
            customModels = custom
        }
    }
    
    LaunchedEffect(Unit) {
        refreshCustomModels()
    }
    
    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            isImportingModel = true
            coroutineScope.launch {
                val res = com.mehad.speaktowrite.models.ModelImporter.importModel(context, uri)
                isImportingModel = false
                if (res is com.mehad.speaktowrite.models.ModelImporter.ImportResult.Success) {
                    android.widget.Toast.makeText(context, "Model imported successfully", android.widget.Toast.LENGTH_SHORT).show()
                    refreshCustomModels()
                } else if (res is com.mehad.speaktowrite.models.ModelImporter.ImportResult.Error) {
                    android.widget.Toast.makeText(context, res.message, android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val scrollState = rememberScrollState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = DarkBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
        ) {
            HeaderSection()
            
            StatusSection(
                ready = hasAudioPermission && hasAccessibilityPermission,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Setup Section
            SectionTitle("Setup", Modifier.padding(horizontal = 24.dp))
            SolidCard {
                SettingsRow(
                    title = "Audio Permission",
                    subtitle = if (hasAudioPermission) "Granted" else "Tap to grant permission",
                    icon = if (hasAudioPermission) Icons.Default.Check else Icons.Default.Warning,
                    iconTint = if (hasAudioPermission) SolidGreen else Color(0xFFFF3B30),
                    onClick = { 
                        if (!hasAudioPermission) {
                            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                )
                HorizontalDivider(color = SolidGreen.copy(alpha = 0.2f), modifier = Modifier.padding(horizontal = 16.dp))
                SettingsRow(
                    title = "Accessibility Service",
                    subtitle = if (hasAccessibilityPermission) "Enabled" else "Tap to enable in settings",
                    icon = if (hasAccessibilityPermission) Icons.Default.Check else Icons.Default.Warning,
                    iconTint = if (hasAccessibilityPermission) SolidGreen else Color(0xFFFF3B30),
                    onClick = { 
                        if (!hasAccessibilityPermission) {
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        }
                    }
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Engine Section
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle("Models", Modifier)
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = { showHelpDialog = true }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Info, contentDescription = "Help", tint = SolidGreen.copy(alpha = 0.7f))
                    }
                }
                if (isImportingModel) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = SolidGreen, strokeWidth = 2.dp)
                } else {
                    IconButton(onClick = { importLauncher.launch("*/*") }, modifier = Modifier.size(24.dp)) {
                        Icon(androidx.compose.material.icons.Icons.Default.Folder, contentDescription = "Import from storage", tint = SolidGreen)
                    }
                }
            }
            SolidCard {
                val allModels = MODEL_CATALOG + customModels
                allModels.forEachIndexed { index, model ->
                    val isCustom = model in customModels
                    val state = if (isCustom) {
                        com.mehad.speaktowrite.models.DownloadState.Done
                    } else {
                        downloaders[model]?.state?.collectAsState()?.value ?: com.mehad.speaktowrite.models.DownloadState.Idle
                    }
                    
                    ModelRowItem(
                        modelName = if (isCustom) "Custom: ${model.name.take(15)}..." else model.name,
                        modelSize = if (isCustom) "Local" else "${model.sizeMb} MB",
                        state = state,
                        isSelected = selectedLocalModel == model.archive,
                        onRowClick = { TranscriberManager.loadModel(context, model.archive) },
                        onDownloadClick = { downloaders[model]?.startOrResume(coroutineScope) },
                        onCancelClick = { downloaders[model]?.cancel() },
                        onPauseClick = { downloaders[model]?.pause() },
                        onResumeClick = { downloaders[model]?.startOrResume(coroutineScope) },
                        onDeleteClick = {
                            if (isCustom) {
                                java.io.File(context.filesDir, "models/${model.archive}").deleteRecursively()
                                refreshCustomModels()
                            } else {
                                downloaders[model]?.deleteModel()
                            }
                        }
                    )
                    if (index < allModels.size - 1) {
                        HorizontalDivider(color = SolidGreen.copy(alpha = 0.2f), modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Cleanup Section
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionTitle("Cleanup", Modifier)
                androidx.compose.material3.Switch(
                    checked = cleanupEnabled,
                    onCheckedChange = { 
                        cleanupEnabled = it
                        settingsManager.cleanupEnabled = it
                    },
                    colors = androidx.compose.material3.SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = SolidGreen,
                        uncheckedThumbColor = Color.Gray,
                        uncheckedTrackColor = DarkSurface
                    )
                )
            }
            SolidCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Google AI Studio API Key", color = SolidGreen, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { 
                            apiKey = it
                            settingsManager.apiKey = it
                            if (it.isBlank()) isValidKey = null 
                        },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Enter API Key", color = Color.Gray) },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SolidGreen,
                            unfocusedBorderColor = SolidGreen.copy(alpha = 0.5f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = SolidGreen
                        ),
                        singleLine = true,
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isValidKey == true) {
                                    Icon(Icons.Default.Check, contentDescription = "Valid Key", tint = SolidGreen)
                                    Spacer(modifier = Modifier.width(12.dp))
                                } else if (isValidKey == false) {
                                    Icon(Icons.Default.Warning, contentDescription = "Invalid Key", tint = Color(0xFFFF3B30))
                                    Spacer(modifier = Modifier.width(12.dp))
                                }
                            }
                        }
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Select AI Model", color = SolidGreen, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isCheckingKey) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = SolidGreen, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            IconButton(onClick = { verifyKeyAndFetchModels(apiKey) }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh Models", tint = SolidGreen)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    ExposedDropdownMenuBox(
                        expanded = expandedModelDropdown,
                        onExpandedChange = { expandedModelDropdown = !expandedModelDropdown }
                    ) {
                        OutlinedTextField(
                            value = selectedAiModel,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedModelDropdown) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = SolidGreen,
                                unfocusedBorderColor = SolidGreen.copy(alpha = 0.5f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedTrailingIconColor = SolidGreen,
                                unfocusedTrailingIconColor = SolidGreen.copy(alpha = 0.5f)
                            )
                        )
                        ExposedDropdownMenu(
                            expanded = expandedModelDropdown,
                            onDismissRequest = { expandedModelDropdown = false },
                            modifier = Modifier.background(DarkSurface)
                        ) {
                            aiModels.forEach { selectionOption ->
                                DropdownMenuItem(
                                    text = { Text(selectionOption, color = Color.White) },
                                    onClick = {
                                        selectedAiModel = selectionOption
                                        settingsManager.selectedAiModel = selectionOption
                                        expandedModelDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }
                
                HorizontalDivider(color = SolidGreen.copy(alpha = 0.2f), modifier = Modifier.padding(horizontal = 16.dp))
                
                // Prompts List
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Prompts", color = SolidGreen, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        IconButton(onClick = { 
                            editingPrompt = null
                            showPromptDialog = true 
                        }) {
                            Icon(Icons.Default.Add, contentDescription = "Add Prompt", tint = SolidGreen)
                        }
                    }
                    
                    prompts.forEach { prompt ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedPromptId = prompt.id }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedPromptId == prompt.id,
                                onClick = { 
                                    selectedPromptId = prompt.id
                                    settingsManager.selectedPromptId = prompt.id
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = SolidGreen, unselectedColor = SolidGreen.copy(alpha = 0.5f))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(prompt.title, color = Color.White, fontWeight = FontWeight.Medium)
                                Text(
                                    prompt.content, 
                                    color = Color.Gray, 
                                    fontSize = 12.sp, 
                                    maxLines = 1, 
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(onClick = { 
                                editingPrompt = prompt
                                showPromptDialog = true
                            }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = SolidGreen)
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(100.dp))
        }
    }

    if (isLoadingModel) {
        Dialog(onDismissRequest = { }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, SolidGreen)
            ) {
                Row(modifier = Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = SolidGreen)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Loading Model into Memory...", color = Color.White, fontWeight = FontWeight.Medium)
                }
            }
        }
    }

    if (showHelpDialog) {
        AlertDialog(
                onDismissRequest = { showHelpDialog = false },
                containerColor = DarkSurface,
                title = { Text("Custom Models Guide", color = SolidGreen, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text("You can import custom offline speech-to-text models for Sherpa-ONNX.", color = Color.White)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Requirements:", color = SolidGreen, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("• Must be a compressed archive (.zip, .tar.bz2, .tar.gz).", color = Color.LightGray, fontSize = 14.sp)
                        Text("• Must contain the model's .onnx files and tokens.txt.", color = Color.LightGray, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Note: Download a compatible Android model directly from the k2-fsa/sherpa-onnx repository.", color = Color.Gray, fontSize = 12.sp)
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showHelpDialog = false }) {
                        Text("Got it", color = SolidGreen)
                    }
                }
            )
        }

        if (showPromptDialog) {
        var pTitle by remember { mutableStateOf(editingPrompt?.title ?: "") }
        var pContent by remember { mutableStateOf(editingPrompt?.content ?: "") }

        Dialog(onDismissRequest = { showPromptDialog = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, SolidGreen)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(if (editingPrompt == null) "New Prompt" else "Edit Prompt", color = SolidGreen, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = pTitle,
                        onValueChange = { pTitle = it },
                        label = { Text("Title", color = Color.Gray) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SolidGreen,
                            unfocusedBorderColor = SolidGreen.copy(alpha = 0.5f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = SolidGreen
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pContent,
                        onValueChange = { pContent = it },
                        label = { Text("Content", color = Color.Gray) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SolidGreen,
                            unfocusedBorderColor = SolidGreen.copy(alpha = 0.5f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = SolidGreen
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showPromptDialog = false }) {
                            Text("Cancel", color = SolidGreen)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (editingPrompt == null) {
                                    prompts.add(PromptPreset(System.currentTimeMillis().toString(), pTitle, pContent))
                                } else {
                                    val idx = prompts.indexOfFirst { it.id == editingPrompt?.id }
                                    if (idx != -1) {
                                        prompts[idx] = prompts[idx].copy(title = pTitle, content = pContent)
                                    }
                                }
                                settingsManager.prompts = prompts.toList()
                                showPromptDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SolidGreen, contentColor = Color.Black)
                        ) {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun borderStroke() = BorderStroke(1.dp, SolidGreen.copy(alpha = 0.4f))

@Composable
fun SolidCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = borderStroke()
    ) {
        Column(content = content)
    }
}

@Composable
fun HeaderSection() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 64.dp, bottom = 16.dp, start = 24.dp, end = 24.dp)
    ) {
        Text(
            text = "Speak to Write",
            fontSize = 36.sp,
            fontWeight = FontWeight.Black,
            color = SolidGreen
        )
        Text(
            text = "Private, local voice dictation",
            fontSize = 14.sp,
            color = SolidGreen.copy(alpha = 0.7f),
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
fun StatusSection(ready: Boolean, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (ready) SolidGreen.copy(alpha = 0.1f) else Color(0xFF2A0808)
        ),
        border = BorderStroke(1.dp, if (ready) SolidGreen else Color(0xFFFF3B30))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (ready) Icons.Default.Check else Icons.Default.Info,
                contentDescription = null,
                tint = if (ready) SolidGreen else Color(0xFFFF3B30),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = if (ready) "Ready to dictate" else "Setup Required",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.White
                )
                Text(
                    text = if (ready) "Tap the play button to start" else "Please complete the setup steps below",
                    fontSize = 12.sp,
                    color = Color.LightGray
                )
            }
        }
    }
}

@Composable
fun SectionTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title.uppercase(),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        color = SolidGreen,
        modifier = modifier.padding(bottom = 4.dp, start = 8.dp)
    )
}

@Composable
fun SettingsRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(iconTint.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
            Text(subtitle, fontSize = 13.sp, color = Color.Gray)
        }
    }
}

@Composable
fun ModelRowItem(
    modelName: String,
    modelSize: String,
    state: DownloadState,
    isSelected: Boolean,
    onRowClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onCancelClick: () -> Unit,
    onPauseClick: () -> Unit,
    onResumeClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = state is DownloadState.Done, onClick = onRowClick)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (state is DownloadState.Done) {
                RadioButton(
                    selected = isSelected,
                    onClick = onRowClick,
                    colors = RadioButtonDefaults.colors(selectedColor = SolidGreen, unselectedColor = SolidGreen.copy(alpha = 0.5f))
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(modelName, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                Text(modelSize, fontSize = 13.sp, color = Color.Gray)
            }
            when (state) {
                is DownloadState.Done -> {
                    IconButton(onClick = onDeleteClick) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFFF3B30).copy(alpha = 0.7f))
                    }
                }
                is DownloadState.Idle, is DownloadState.Error -> {
                    IconButton(onClick = onDownloadClick) {
                        Icon(Icons.Default.Download, contentDescription = "Download", tint = SolidGreen)
                    }
                }
                is DownloadState.Downloading -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onPauseClick, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Pause, contentDescription = "Pause", tint = SolidGreen)
                        }
                        IconButton(onClick = onCancelClick, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color(0xFFFF3B30))
                        }
                    }
                }
                is DownloadState.Paused -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onResumeClick, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Resume", tint = SolidGreen)
                        }
                        IconButton(onClick = onCancelClick, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color(0xFFFF3B30))
                        }
                    }
                }
                is DownloadState.Extracting -> {
                    IconButton(onClick = onCancelClick, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color(0xFFFF3B30))
                    }
                }
            }
        }
        
        if (state is DownloadState.Downloading || state is DownloadState.Paused || state is DownloadState.Extracting) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state is DownloadState.Extracting) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = SolidGreen, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Extracting model files...", fontSize = 12.sp, color = SolidGreen)
                } else {
                    val progress = if (state is DownloadState.Downloading) state.progress else (state as DownloadState.Paused).progress
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = SolidGreen,
                        trackColor = SolidGreen.copy(alpha = 0.2f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    if (state is DownloadState.Downloading) {
                        val speed = if (state.speedKbps > 1024) "${state.speedKbps / 1024} MB/s" else "${state.speedKbps} KB/s"
                        Text("${(progress * 100).toInt()}% • $speed", fontSize = 12.sp, color = SolidGreen)
                    } else if (state is DownloadState.Paused) {
                        Text("${(progress * 100).toInt()}% • Paused", fontSize = 12.sp, color = Color.Gray)
                    }
                }
            }
        }
        
        if (state is DownloadState.Error) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(state.message, color = Color(0xFFFF3B30), fontSize = 12.sp)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    SpeakToWriteTheme {
        MainScreen()
    }
}
