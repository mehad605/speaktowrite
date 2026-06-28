package com.mhm.speaktowrite.models

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.*
import java.io.File

class LocalTranscriber private constructor(
    private val offlineRecognizer: OfflineRecognizer? = null,
    private val onlineRecognizer: OnlineRecognizer? = null
) {

    fun transcribe(samples: FloatArray, sampleRate: Int = 16000): String {
        return if (offlineRecognizer != null) {
            val stream = offlineRecognizer.createStream()
            stream.acceptWaveform(samples, sampleRate)
            offlineRecognizer.decode(stream)
            val result = offlineRecognizer.getResult(stream)
            stream.release()
            result.text.trim()
        } else if (onlineRecognizer != null) {
            val stream = onlineRecognizer.createStream()
            stream.acceptWaveform(samples, sampleRate)
            stream.inputFinished()
            while (onlineRecognizer.isReady(stream)) {
                onlineRecognizer.decode(stream)
            }
            val result = onlineRecognizer.getResult(stream)
            stream.release()
            result.text.trim()
        } else {
            ""
        }
    }

    fun release() {
        try {
            offlineRecognizer?.release()
            onlineRecognizer?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        private const val TAG = "LocalTranscriber"

        fun create(ctx: Context, modelName: String): LocalTranscriber? {
            val modelDir = File(ctx.filesDir, "models/$modelName")
            if (!modelDir.exists()) {
                Log.e(TAG, "Model dir not found: $modelDir")
                return null
            }

            return try {
                val onlineConfig = detectOnlineModelConfig(modelDir)
                if (onlineConfig != null) {
                    val recognizer = OnlineRecognizer(assetManager = null, config = onlineConfig)
                    Log.i(TAG, "Loaded online model: $modelName")
                    return LocalTranscriber(onlineRecognizer = recognizer)
                }

                val offlineConfig = detectModelConfig(modelDir)
                if (offlineConfig != null) {
                    val recognizer = OfflineRecognizer(assetManager = null, config = offlineConfig)
                    Log.i(TAG, "Loaded offline model: $modelName")
                    return LocalTranscriber(offlineRecognizer = recognizer)
                }

                Log.e(TAG, "Could not detect model type in $modelDir")
                null
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load model: ${e.message}")
                null
            }
        }

        private fun detectOnlineModelConfig(dir: File): OnlineRecognizerConfig? {
            val p = dir.absolutePath
            val tokens = "$p/tokens.txt"
            if (!File(tokens).exists()) return null

            val encoder = findFile(p, "encoder")
            val decoder = findFile(p, "decoder")
            val joiner = findFile(p, "joiner")
            
            val bpe = File(p, "bpe.model")
            
            // Check for streaming zipformer which has encoder, decoder, joiner but is meant for online
            // Usually identified by the folder name having 'streaming' or similar
            if (encoder != null && decoder != null && joiner != null && (p.contains("streaming") || p.contains("online"))) {
                return OnlineRecognizerConfig(
                    modelConfig = OnlineModelConfig(
                        transducer = OnlineTransducerModelConfig(
                            encoder = encoder,
                            decoder = decoder,
                            joiner = joiner,
                        ),
                        tokens = tokens,
                        bpeVocab = if (bpe.exists()) bpe.absolutePath else "",
                        numThreads = 4,
                        modelType = "",
                    )
                )
            }
            return null
        }

        private fun detectModelConfig(dir: File): OfflineRecognizerConfig? {
            val p = dir.absolutePath
            val tokens = "$p/tokens.txt"
            if (!File(tokens).exists()) return null

            if (File("$p/preprocess.onnx").exists()) {
                return OfflineRecognizerConfig(
                    modelConfig = OfflineModelConfig(
                        moonshine = OfflineMoonshineModelConfig(
                            preprocessor = "$p/preprocess.onnx",
                            encoder = findFile(p, "encode") ?: return null,
                            uncachedDecoder = findFile(p, "uncached_decode") ?: return null,
                            cachedDecoder = findFile(p, "cached_decode") ?: return null,
                        ),
                        tokens = tokens,
                        numThreads = 4,
                    )
                )
            }

            val whisperEncoder = findFile(p, "encoder")
            val whisperDecoder = findFile(p, "decoder")
            if (whisperEncoder != null && whisperDecoder != null && findFile(p, "joiner") == null) {
                return OfflineRecognizerConfig(
                    modelConfig = OfflineModelConfig(
                        whisper = OfflineWhisperModelConfig(
                            encoder = whisperEncoder,
                            decoder = whisperDecoder,
                        ),
                        tokens = tokens,
                        numThreads = 4,
                        modelType = "whisper",
                    )
                )
            }

            val encoder = findFile(p, "encoder")
            val decoder = findFile(p, "decoder")
            val joiner = findFile(p, "joiner")
            if (encoder != null && decoder != null && joiner != null) {
                return OfflineRecognizerConfig(
                    modelConfig = OfflineModelConfig(
                        transducer = OfflineTransducerModelConfig(
                            encoder = encoder,
                            decoder = decoder,
                            joiner = joiner,
                        ),
                        tokens = tokens,
                        numThreads = 4,
                        modelType = "nemo_transducer",
                    )
                )
            }

            val ctcModel = findFile(p, "model")
            if (ctcModel != null) {
                return OfflineRecognizerConfig(
                    modelConfig = OfflineModelConfig(
                        nemo = OfflineNemoEncDecCtcModelConfig(model = ctcModel),
                        tokens = tokens,
                        numThreads = 4,
                    )
                )
            }

            return null
        }

        private fun findFile(dir: String, prefix: String): String? {
            val d = File(dir)
            d.listFiles()?.firstOrNull { it.name.startsWith(prefix) && it.name.contains("int8") }
                ?.let { return it.absolutePath }
            return d.listFiles()?.firstOrNull {
                it.name.startsWith(prefix) && (it.name.endsWith(".onnx") || it.name.endsWith(".ort"))
            }?.absolutePath
        }
    }
}
