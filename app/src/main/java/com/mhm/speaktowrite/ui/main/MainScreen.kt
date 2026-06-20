package com.mhm.speaktowrite.ui.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mhm.speaktowrite.SpeakToWriteAccessibilityService
import com.mhm.speaktowrite.models.MODEL_CATALOG
import com.mhm.speaktowrite.models.Model
import com.mhm.speaktowrite.models.ModelDownloader
import com.mhm.speaktowrite.models.SettingsManager
import com.mhm.speaktowrite.models.TranscriberManager
import com.mhm.speaktowrite.theme.AuroraBackgroundEnd
import com.mhm.speaktowrite.theme.Emerald
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import com.mhm.speaktowrite.ui.components.AuroraStatus
import com.mhm.speaktowrite.ui.components.BrandBrushSoft
import com.mhm.speaktowrite.ui.components.GlassCard
import com.mhm.speaktowrite.ui.components.GradientText
import com.mhm.speaktowrite.ui.components.PulsingGlow
import com.mhm.speaktowrite.ui.components.StatusChip
import com.mhm.speaktowrite.ui.dialogs.HelpDialog
import com.mhm.speaktowrite.ui.dialogs.LoadingModelDialog
import com.mhm.speaktowrite.ui.dialogs.PromptEditorDialog
import com.mhm.speaktowrite.ui.dialogs.DeletePromptConfirmDialog
import com.mhm.speaktowrite.ui.dialogs.ExportConfigDialog
import com.mhm.speaktowrite.ui.sections.AiPolishSection
import com.mhm.speaktowrite.ui.sections.ModelSection
import com.mhm.speaktowrite.ui.sections.SetupSection
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

data class PromptPreset(
    val id: String,
    var title: String,
    var content: String,
)

@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    // ── Permissions ───────────────────────────────────────────────────────
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var hasAccessibilityPermission by remember {
        mutableStateOf(SpeakToWriteAccessibilityService.isEnabled(context))
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted -> hasAudioPermission = isGranted }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasAudioPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                hasAccessibilityPermission = SpeakToWriteAccessibilityService.isEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val ready = hasAudioPermission && hasAccessibilityPermission

    // ── Models & transcription ───────────────────────────────────────────
    val downloaders = remember { MODEL_CATALOG.associateWith { ModelDownloader(context, it) } }
    val isLoadingModel by TranscriberManager.isLoading.collectAsState()
    val selectedLocalModel by TranscriberManager.currentModel.collectAsState()

    // ── Settings ─────────────────────────────────────────────────────────
    val settingsManager = remember { SettingsManager(context) }
    var apiKey by remember { mutableStateOf(settingsManager.apiKey) }
    var selectedAiModel by remember { mutableStateOf(settingsManager.selectedAiModel) }
    var cleanupEnabled by remember { mutableStateOf(settingsManager.cleanupEnabled) }
    var showOnLockScreen by remember { mutableStateOf(settingsManager.showOnLockScreen) }
    var aiModels by remember { mutableStateOf(listOf("gemini-1.5-flash", "gemini-1.5-pro", "gemini-1.0-pro")) }
    var isCheckingKey by remember { mutableStateOf(false) }
    var isValidKey by remember { mutableStateOf<Boolean?>(null) }
    var expandedModelDropdown by remember { mutableStateOf(false) }

    fun verifyKeyAndFetchModels(key: String) {
        if (key.isBlank()) {
            isValidKey = null
            return
        }
        isCheckingKey = true
        coroutineScope.launch {
            val result = com.mhm.speaktowrite.models.GeminiClient.getAvailableModels(key)
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
        if (apiKey.isNotBlank()) verifyKeyAndFetchModels(apiKey)
    }

    // ── Prompts ──────────────────────────────────────────────────────────
    val prompts = remember { mutableStateListOf(*settingsManager.prompts.toTypedArray()) }
    var selectedPromptId by remember { mutableStateOf(settingsManager.selectedPromptId) }
    var showPromptDialog by remember { mutableStateOf(false) }
    var editingPrompt by remember { mutableStateOf<PromptPreset?>(null) }

    // ── Export / Import Config ───────────────────────────────────────────
    var showExportDialog by remember { mutableStateOf(false) }
    var shouldExportWithApiKey by remember { mutableStateOf(true) }
    var showDeletePromptDialog by remember { mutableStateOf(false) }
    var promptToDelete by remember { mutableStateOf<PromptPreset?>(null) }

    val exportConfigLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        val configObject = org.json.JSONObject()
                        if (shouldExportWithApiKey) {
                            configObject.put("api_key", settingsManager.apiKey)
                        }
                        
                        val promptsArray = org.json.JSONArray()
                        for (p in prompts) {
                            val obj = org.json.JSONObject()
                            obj.put("id", p.id)
                            obj.put("title", p.title)
                            obj.put("content", p.content)
                            promptsArray.put(obj)
                        }
                        configObject.put("prompts", promptsArray)
                        
                        outputStream.write(configObject.toString(2).toByteArray())
                    }
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "Configuration exported successfully", android.widget.Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "Export failed: ${e.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    val importConfigLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        val jsonString = inputStream.bufferedReader().readText()
                        
                        var importedApiKey = ""
                        val newPrompts = mutableListOf<PromptPreset>()
                        
                        try {
                            val jsonObject = org.json.JSONObject(jsonString)
                            importedApiKey = jsonObject.optString("api_key", "")
                            val importedPromptsArray = jsonObject.optJSONArray("prompts")
                            if (importedPromptsArray != null) {
                                for (i in 0 until importedPromptsArray.length()) {
                                    val obj = importedPromptsArray.getJSONObject(i)
                                    newPrompts.add(
                                        PromptPreset(
                                            id = obj.getString("id"),
                                            title = obj.getString("title"),
                                            content = obj.getString("content")
                                        )
                                    )
                                }
                            }
                        } catch (e: Exception) {
                            // Try parsing as array of prompts directly
                            val arr = org.json.JSONArray(jsonString)
                            for (i in 0 until arr.length()) {
                                val obj = arr.getJSONObject(i)
                                newPrompts.add(
                                    PromptPreset(
                                        id = obj.getString("id"),
                                        title = obj.getString("title"),
                                        content = obj.getString("content")
                                    )
                                )
                            }
                        }
                        
                        withContext(Dispatchers.Main) {
                            if (importedApiKey.isNotBlank()) {
                                apiKey = importedApiKey
                                settingsManager.apiKey = importedApiKey
                                verifyKeyAndFetchModels(importedApiKey)
                            }
                            
                            for (ip in newPrompts) {
                                val existingIdx = prompts.indexOfFirst { it.id == ip.id }
                                if (existingIdx != -1) {
                                    prompts[existingIdx] = ip
                                } else {
                                    prompts.add(ip)
                                }
                            }
                            settingsManager.prompts = prompts.toList()
                            android.widget.Toast.makeText(context, "Configuration imported successfully", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "Import failed: Invalid file format", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    // ── Custom (imported) models ─────────────────────────────────────────
    var customModels by remember { mutableStateOf(emptyList<Model>()) }
    var isImportingModel by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }

    fun refreshCustomModels() {
        val outDir = java.io.File(context.filesDir, "models")
        if (outDir.exists()) {
            val folders = outDir.listFiles()?.filter { it.isDirectory } ?: emptyList()
            val catalogArchives = MODEL_CATALOG.map { it.archive }
            customModels = folders.filter { it.name !in catalogArchives }.map {
                Model(it.name, it.name, 0)
            }
        }
    }
    LaunchedEffect(Unit) { refreshCustomModels() }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            isImportingModel = true
            coroutineScope.launch {
                val res = com.mhm.speaktowrite.models.ModelImporter.importModel(context, uri)
                isImportingModel = false
                if (res is com.mhm.speaktowrite.models.ModelImporter.ImportResult.Success) {
                    android.widget.Toast.makeText(context, "Model imported", android.widget.Toast.LENGTH_SHORT).show()
                    refreshCustomModels()
                } else if (res is com.mhm.speaktowrite.models.ModelImporter.ImportResult.Error) {
                    android.widget.Toast.makeText(context, res.message, android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val scrollState = rememberScrollState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(scrollState)
            ) {
                // ── Hero ─────────────────────────────────────────────────
                Spacer(Modifier.height(56.dp))
                HeroHeader(ready = ready)

                // ── Step 1: Setup ───────────────────────────────────────
                SetupSection(
                    hasAudio = hasAudioPermission,
                    hasAccessibility = hasAccessibilityPermission,
                    showOnLockScreen = showOnLockScreen,
                    onShowOnLockScreenToggle = {
                        showOnLockScreen = it
                        settingsManager.showOnLockScreen = it
                    },
                    onAudioClick = {
                        if (!hasAudioPermission) audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    onAccessibilityClick = {
                        if (!hasAccessibilityPermission) context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                )

                Spacer(Modifier.height(28.dp))

                // ── Step 2: Model ───────────────────────────────────────
                ModelSection(
                    catalogModels = MODEL_CATALOG,
                    customModels = customModels,
                    downloaders = downloaders,
                    selectedLocalModel = selectedLocalModel,
                    isImporting = isImportingModel,
                    onImportClick = { importLauncher.launch("*/*") },
                    onHelpClick = { showHelpDialog = true },
                    onSelect = { TranscriberManager.loadModel(context, it) },
                    onDownload = { downloaders[it]?.startOrResume(coroutineScope) },
                    onCancel = { downloaders[it]?.cancel() },
                    onPause = { downloaders[it]?.pause() },
                    onResume = { downloaders[it]?.startOrResume(coroutineScope) },
                    onDelete = { model, isCustom ->
                        if (isCustom) {
                            java.io.File(context.filesDir, "models/${model.archive}").deleteRecursively()
                            refreshCustomModels()
                        } else {
                            downloaders[model]?.deleteModel()
                        }
                    },
                )

                Spacer(Modifier.height(28.dp))

                // ── Step 3: AI Polish ───────────────────────────────────
                AiPolishSection(
                    apiKey = apiKey,
                    onApiKeyChange = {
                        apiKey = it
                        settingsManager.apiKey = it
                        if (it.isBlank()) isValidKey = null
                    },
                    isValidKey = isValidKey,
                    isCheckingKey = isCheckingKey,
                    onRefreshModels = { verifyKeyAndFetchModels(apiKey) },
                    selectedAiModel = selectedAiModel,
                    aiModels = aiModels,
                    expandedDropdown = expandedModelDropdown,
                    onExpandedChange = { expandedModelDropdown = it },
                    onSelectAiModel = {
                        selectedAiModel = it
                        settingsManager.selectedAiModel = it
                    },
                    cleanupEnabled = cleanupEnabled,
                    onCleanupToggle = {
                        cleanupEnabled = it
                        settingsManager.cleanupEnabled = it
                    },
                    prompts = prompts,
                    selectedPromptId = selectedPromptId,
                    onSelectPrompt = {
                        selectedPromptId = it
                        settingsManager.selectedPromptId = it
                    },
                    onAddPrompt = {
                        editingPrompt = null
                        showPromptDialog = true
                    },
                    onEditPrompt = {
                        editingPrompt = it
                        showPromptDialog = true
                    },
                    onDeletePrompt = {
                        promptToDelete = it
                        showDeletePromptDialog = true
                    },
                    onImportConfig = {
                        importConfigLauncher.launch("*/*")
                    },
                    onExportConfig = {
                        showExportDialog = true
                    },
                )

                // Footer hint.
                Spacer(Modifier.height(36.dp))
                Text(
                    text = "Everything runs on-device. Tap the floating mic anywhere to dictate.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
                )
                Spacer(Modifier.height(64.dp))
            }
        }
    }

    // ── Overlays & dialogs ───────────────────────────────────────────────
    if (isLoadingModel) LoadingModelDialog()

    if (showHelpDialog) {
        HelpDialog(onDismiss = { showHelpDialog = false })
    }

    if (showPromptDialog) {
        PromptEditorDialog(
            editing = editingPrompt,
            onDismiss = { showPromptDialog = false },
            onSave = { title, content ->
                if (editingPrompt == null) {
                    prompts.add(PromptPreset(System.currentTimeMillis().toString(), title, content))
                } else {
                    val idx = prompts.indexOfFirst { it.id == editingPrompt?.id }
                    if (idx != -1) prompts[idx] = prompts[idx].copy(title = title, content = content)
                }
                settingsManager.prompts = prompts.toList()
                showPromptDialog = false
            },
        )
    }

    if (showDeletePromptDialog && promptToDelete != null) {
        DeletePromptConfirmDialog(
            promptTitle = promptToDelete!!.title,
            onDismiss = {
                showDeletePromptDialog = false
                promptToDelete = null
            },
            onConfirm = {
                val p = promptToDelete!!
                prompts.remove(p)
                settingsManager.prompts = prompts.toList()
                if (selectedPromptId == p.id) {
                    val nextPrompt = prompts.firstOrNull()
                    selectedPromptId = nextPrompt?.id ?: ""
                    settingsManager.selectedPromptId = selectedPromptId
                }
                if (p.id in listOf("1", "2", "3")) {
                    val deletedSet = settingsManager.deletedPromptIds.toMutableSet()
                    deletedSet.add(p.id)
                    settingsManager.deletedPromptIds = deletedSet
                }
                showDeletePromptDialog = false
                promptToDelete = null
                android.widget.Toast.makeText(context, "Prompt deleted", android.widget.Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showExportDialog) {
        ExportConfigDialog(
            onDismiss = { showExportDialog = false },
            onConfirm = { includeApiKey ->
                shouldExportWithApiKey = includeApiKey
                showExportDialog = false
                exportConfigLauncher.launch("speak_to_write_config.json")
            }
        )
    }
}

// ── Hero ──────────────────────────────────────────────────────────────────
@Composable
private fun HeroHeader(ready: Boolean) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Animated solid status dot (Matte redone).
            PulsingGlow(modifier = Modifier.padding(end = 4.dp)) {
                Box(
                    modifier =
                        Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .border(BorderStroke(2.dp, Emerald), CircleShape)
                            .padding(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = null,
                        tint = Emerald,
                    )
                }
            }
            Column(modifier = Modifier.padding(start = 8.dp)) {
                Text(
                    text = androidx.compose.ui.text.buildAnnotatedString {
                        append("Speak to ")
                        withStyle(style = androidx.compose.ui.text.SpanStyle(color = Emerald)) {
                            append("Write")
                        }
                    },
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Private, on-device voice dictation",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }

        Spacer(Modifier.height(22.dp))

        // Status banner.
        val (title, subtitle) =
            if (ready) "Ready to dictate" to "Tap the floating mic anywhere to start."
            else "Let's get you set up" to "Two quick permissions below — about a minute."
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            highlighted = ready,
            contentPadding = PaddingValues(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusChip(
                    label = if (ready) "READY" else "SETUP",
                    status = if (ready) AuroraStatus.SUCCESS else AuroraStatus.WARNING,
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
