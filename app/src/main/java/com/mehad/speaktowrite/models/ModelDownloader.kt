package com.mehad.speaktowrite.models

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.*
import java.util.concurrent.TimeUnit

data class Model(
    val name: String,
    val archive: String,
    val sizeMb: Int
)

val MODEL_CATALOG = listOf(
    Model("Parakeet 110M", "sherpa-onnx-nemo-parakeet_tdt_ctc_110m-en-36000-int8", 100),
    Model("Whisper Base", "sherpa-onnx-whisper-base.en", 199),
    Model("Parakeet 0.6B", "sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8", 465),
    Model("Moonshine Tiny", "sherpa-onnx-moonshine-tiny-en-int8", 103),
    Model("Zipformer Bangla", "sherpa-onnx-streaming-zipformer-bn-vosk-2026-02-09", 83)
)

sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(val progress: Float, val speedKbps: Int) : DownloadState()
    data class Paused(val progress: Float) : DownloadState()
    data class Extracting(val progress: Float) : DownloadState()
    object Done : DownloadState()
    data class Error(val message: String) : DownloadState()
}

class ModelDownloader(private val context: Context, val model: Model) {
    private val _state = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val state: StateFlow<DownloadState> = _state

    private var downloadJob: Job? = null
    private var isPaused = false

    companion object {
        private const val BASE_URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models"
        private val client = OkHttpClient.Builder()
            .readTimeout(60, TimeUnit.SECONDS)
            .build()

        fun modelDir(ctx: Context, model: Model) = File(ctx.filesDir, "models/${model.archive}")
        fun isInstalled(ctx: Context, model: Model) = modelDir(ctx, model).exists()
        fun delete(ctx: Context, model: Model) = modelDir(ctx, model).deleteRecursively()
    }

    private val tmpFile = File(context.cacheDir, "${model.archive}.tar.bz2")
    private val outDir = File(context.filesDir, "models")

    init {
        if (isInstalled(context, model)) {
            _state.value = DownloadState.Done
        } else if (tmpFile.exists()) {
            _state.value = DownloadState.Paused(0f)
        }
    }

    fun startOrResume(coroutineScope: CoroutineScope) {
        if (downloadJob?.isActive == true) return
        isPaused = false
        
        downloadJob = coroutineScope.launch(Dispatchers.IO) {
            try {
                var downloaded = if (tmpFile.exists()) tmpFile.length() else 0L
                val url = "$BASE_URL/${model.archive}.tar.bz2"
                
                val requestBuilder = Request.Builder().url(url)
                if (downloaded > 0) {
                    requestBuilder.addHeader("Range", "bytes=$downloaded-")
                }

                val response = client.newCall(requestBuilder.build()).execute()
                if (!response.isSuccessful) {
                    if (response.code == 416) {
                        tmpFile.delete()
                        downloaded = 0L
                        startOrResume(coroutineScope)
                        return@launch
                    }
                    throw IOException("HTTP ${response.code}")
                }

                val body = response.body ?: throw IOException("Empty response")
                val contentLength = body.contentLength()
                val total = if (response.code == 206) downloaded + contentLength else contentLength

                var lastTime = System.currentTimeMillis()
                var bytesSinceLastTime = 0L

                body.byteStream().use { src ->
                    FileOutputStream(tmpFile, downloaded > 0).use { dst ->
                        val buf = ByteArray(64 * 1024)
                        var n: Int
                        while (src.read(buf).also { n = it } != -1) {
                            if (!isActive || isPaused) break
                            
                            dst.write(buf, 0, n)
                            downloaded += n
                            bytesSinceLastTime += n
                            
                            val now = System.currentTimeMillis()
                            if (now - lastTime >= 500) {
                                val speedKbps = ((bytesSinceLastTime / 1024f) / ((now - lastTime) / 1000f)).toInt()
                                _state.value = DownloadState.Downloading(
                                    if (total > 0) downloaded.toFloat() / total else 0f, 
                                    speedKbps
                                )
                                lastTime = now
                                bytesSinceLastTime = 0L
                            }
                        }
                    }
                }

                if (isPaused) {
                    _state.value = DownloadState.Paused(if (total > 0) downloaded.toFloat() / total else 0f)
                    return@launch
                }

                if (!isActive) return@launch

                _state.value = DownloadState.Extracting(0f)
                extractTarBz2(tmpFile, outDir, tmpFile.length())
                tmpFile.delete()
                _state.value = DownloadState.Done
                
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    _state.value = DownloadState.Error(e.message ?: "Unknown error")
                }
            }
        }
    }

    fun pause() {
        isPaused = true
        downloadJob?.cancel()
    }

    fun cancel() {
        isPaused = false
        downloadJob?.cancel()
        tmpFile.delete()
        _state.value = DownloadState.Idle
    }

    fun deleteModel() {
        cancel()
        delete(context, model)
        _state.value = DownloadState.Idle
    }

    private fun extractTarBz2(archive: File, outDir: File, totalArchiveSize: Long) {
        outDir.mkdirs()
        var bytesRead = 0L
        var lastTime = System.currentTimeMillis()

        val fis = FileInputStream(archive)
        val countingFis = object : FilterInputStream(fis) {
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                val res = super.read(b, off, len)
                if (res != -1) bytesRead += res
                return res
            }
        }

        val bzIn = BZip2CompressorInputStream(BufferedInputStream(countingFis))
        TarArchiveInputStream(bzIn).use { tar ->
            generateSequence { tar.nextEntry }.forEach { entry ->
                val dest = File(outDir, entry.name)
                require(dest.canonicalPath.startsWith(outDir.canonicalPath)) {
                    "Path traversal: ${entry.name}"
                }
                if (entry.isDirectory) dest.mkdirs()
                else {
                    dest.parentFile?.mkdirs()
                    FileOutputStream(dest).use { tar.copyTo(it) }
                }

                val now = System.currentTimeMillis()
                if (now - lastTime >= 500) {
                    _state.value = DownloadState.Extracting(bytesRead.toFloat() / totalArchiveSize)
                    lastTime = now
                }
            }
        }
        _state.value = DownloadState.Extracting(1f)
    }
}
