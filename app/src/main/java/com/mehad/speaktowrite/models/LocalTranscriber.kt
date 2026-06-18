package com.mehad.speaktowrite.models

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.*
import java.io.File

class LocalTranscriber private constructor(private val recognizer: OfflineRecognizer) {

    fun transcribe(samples: FloatArray, sampleRate: Int = 16000): String {
        val stream = recognizer.createStream()
        stream.acceptWaveform(samples, sampleRate)
        recognizer.decode(stream)
        val result = recognizer.getResult(stream)
        stream.release()
        return result.text.trim()
    }

    fun release() {
        try {
            recognizer.release()
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

            val config = detectModelConfig(modelDir) ?: run {
                Log.e(TAG, "Could not detect model type in $modelDir")
                return null
            }

            return try {
                val recognizer = OfflineRecognizer(assetManager = null, config = config)
                Log.i(TAG, "Loaded model: $modelName")
                LocalTranscriber(recognizer)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load model: ${e.message}")
                null
            }
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
                        numThreads = 2,
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
                        numThreads = 2,
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
                        numThreads = 2,
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
                        numThreads = 2,
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
