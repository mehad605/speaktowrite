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

    private const val MAX_CACHED_MODELS = 2
    private val transcriberCache = object : java.util.LinkedHashMap<String, LocalTranscriber>(MAX_CACHED_MODELS, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, LocalTranscriber>): Boolean {
            if (size > MAX_CACHED_MODELS) {
                ServiceLogger.i(TAG, "Evicting model from cache to save RAM: ${eldest.key}")
                eldest.value.release()
                return true
            }
            return false
        }
    }

    val transcriber: LocalTranscriber?
        get() = synchronized(transcriberCache) { currentModel.value?.let { transcriberCache[it] } }
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
        val alreadyLoaded = synchronized(transcriberCache) { transcriberCache.containsKey(modelName) }
        
        if (currentModel.value == modelName && alreadyLoaded) {
            ServiceLogger.d(TAG, "loadModel($modelName) skipped — already active")
            return
        }

        // Save to prefs immediately so UI knows what's selected
        context.getSharedPreferences("speaktowrite_prefs", Context.MODE_PRIVATE)
            .edit().putString("selected_model", modelName).apply()
        currentModel.value = modelName

        if (alreadyLoaded) {
            // Model is already in RAM (LRU cache hit). Instant switch.
            ServiceLogger.i(TAG, "loadModel($modelName) — INSTANT SWITCH from cache")
            // A quick read to update LRU access order
            synchronized(transcriberCache) { transcriberCache[modelName] }
            return
        }

        ServiceLogger.i(TAG, "loadModel($modelName) starting")

        isLoading.value = true
        _loadError.value = null

        // NOTE: This scope is intentionally independent — model loading should
        // continue even if the AccessibilityService restarts mid-load.
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val newTranscriber = LocalTranscriber.create(context, modelName)
                if (newTranscriber != null) {
                    synchronized(transcriberCache) {
                        transcriberCache.put(modelName, newTranscriber)
                    }
                    ServiceLogger.i(TAG, "loadModel($modelName) SUCCESS")
                } else {
                    throw Exception("Failed to instantiate model")
                }
            } catch (e: Exception) {
                val reason = "${e.javaClass.simpleName}: ${e.message}"
                ServiceLogger.e(TAG, "loadModel($modelName) FAILED — $reason", e)
                _loadError.value = reason
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
