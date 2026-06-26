package com.mhm.speaktowrite.models

import android.content.Context
import com.mhm.speaktowrite.ServiceLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

object TranscriberManager {
    private const val TAG = "TranscriberManager"

    var transcriber: LocalTranscriber? = null
    val isLoading = MutableStateFlow(false)
    val currentModel = MutableStateFlow<String?>(null)

    /** Exposes the last model-load failure reason, or null when healthy. */
    private val _loadError = MutableStateFlow<String?>(null)
    val loadError: StateFlow<String?> = _loadError

    fun init(context: Context) {
        val saved = context.getSharedPreferences("speaktowrite_prefs", Context.MODE_PRIVATE)
            .getString("selected_model", null)
        ServiceLogger.i(TAG, "init() — saved model = $saved")
        if (saved != null) {
            loadModel(context, saved)
        }
    }

    fun loadModel(context: Context, modelName: String) {
        if (currentModel.value == modelName && transcriber != null) {
            ServiceLogger.d(TAG, "loadModel($modelName) skipped — already loaded")
            return
        }

        ServiceLogger.i(TAG, "loadModel($modelName) starting")

        // Save to prefs
        context.getSharedPreferences("speaktowrite_prefs", Context.MODE_PRIVATE)
            .edit().putString("selected_model", modelName).apply()
        currentModel.value = modelName
        isLoading.value = true
        _loadError.value = null

        // NOTE: This scope is intentionally independent — model loading should
        // continue even if the AccessibilityService restarts mid-load.
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val old = transcriber
                transcriber = LocalTranscriber.create(context, modelName)
                old?.release() // Ensure old model is freed
                ServiceLogger.i(TAG, "loadModel($modelName) SUCCESS")
            } catch (e: Exception) {
                val reason = "${e.javaClass.simpleName}: ${e.message}"
                ServiceLogger.e(TAG, "loadModel($modelName) FAILED — $reason", e)
                _loadError.value = reason
                transcriber = null
            } finally {
                isLoading.value = false
            }
        }
    }

    /**
     * Returns all installed models as (archiveName, displayName) pairs.
     * Catalog models get their friendly name; custom/imported models get
     * their directory name (truncated).
     */
    fun getInstalledModels(context: Context): List<Pair<String, String>> {
        val modelsRoot = File(context.filesDir, "models")
        if (!modelsRoot.exists()) return emptyList()

        val catalogMap = MODEL_CATALOG.associateBy { it.archive }
        val dirs = modelsRoot.listFiles()?.filter { it.isDirectory } ?: return emptyList()

        return dirs.mapNotNull { dir ->
            val archive = dir.name
            val display = catalogMap[archive]?.name ?: archive.take(24)
            archive to display
        }
    }
}
