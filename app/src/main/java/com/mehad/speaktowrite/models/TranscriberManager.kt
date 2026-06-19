package com.mehad.speaktowrite.models

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.File

object TranscriberManager {
    var transcriber: LocalTranscriber? = null
    val isLoading = MutableStateFlow(false)
    val currentModel = MutableStateFlow<String?>(null)

    fun init(context: Context) {
        val saved = context.getSharedPreferences("speaktowrite_prefs", Context.MODE_PRIVATE).getString("selected_model", null)
        if (saved != null) {
            loadModel(context, saved)
        }
    }

    fun loadModel(context: Context, modelName: String) {
        if (currentModel.value == modelName && transcriber != null) return

        // Save to prefs
        context.getSharedPreferences("speaktowrite_prefs", Context.MODE_PRIVATE).edit().putString("selected_model", modelName).apply()
        currentModel.value = modelName
        isLoading.value = true

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val old = transcriber
                transcriber = LocalTranscriber.create(context, modelName)
                old?.release() // Ensure old model is freed
            } catch (e: Exception) {
                e.printStackTrace()
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
