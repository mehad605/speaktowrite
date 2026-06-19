package com.mhm.speaktowrite.models

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream

object ModelImporter {

    sealed class ImportResult {
        object Success : ImportResult()
        data class Error(val message: String) : ImportResult()
    }

    suspend fun importModel(context: Context, uri: Uri): ImportResult {
        return withContext(Dispatchers.IO) {
            try {
                val cr = context.contentResolver
                val outDir = File(context.filesDir, "models")
                outDir.mkdirs()

                // Check file extension from name if possible
                var isZip = false
                var isGzip = false
                var isBzip2 = false
                
                val cursor = cr.query(uri, null, null, null, null)
                var displayName: String? = null
                cursor?.use {
                    if (it.moveToFirst()) {
                        val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIndex >= 0) {
                            displayName = it.getString(nameIndex)
                        }
                    }
                }
                
                displayName = displayName?.lowercase() ?: ""
                if (displayName.endsWith(".zip")) isZip = true
                else if (displayName.endsWith(".tar.gz") || displayName.endsWith(".tgz")) isGzip = true
                else if (displayName.endsWith(".tar.bz2")) isBzip2 = true
                else if (displayName.endsWith(".tar")) {
                    // plain tar
                } else {
                    // Try to guess or default to bzip2 which is typical for sherpa
                    isBzip2 = true
                }

                cr.openInputStream(uri)?.use { fis ->
                    val bis = BufferedInputStream(fis)
                    
                    if (isZip) {
                        ZipInputStream(bis).use { zip ->
                            var entry = zip.nextEntry
                            while (entry != null) {
                                val dest = File(outDir, entry.name)
                                require(dest.canonicalPath.startsWith(outDir.canonicalPath)) { "Path traversal" }
                                if (entry.isDirectory) dest.mkdirs()
                                else {
                                    dest.parentFile?.mkdirs()
                                    FileOutputStream(dest).use { zip.copyTo(it) }
                                }
                                entry = zip.nextEntry
                            }
                        }
                    } else {
                        val compressorIn: InputStream = when {
                            isGzip -> GZIPInputStream(bis)
                            isBzip2 -> try { BZip2CompressorInputStream(bis) } catch(e: Exception) { bis } // fallback if not actually bzip2
                            else -> bis
                        }
                        
                        TarArchiveInputStream(compressorIn).use { tar ->
                            var entry = tar.nextEntry
                            while (entry != null) {
                                val dest = File(outDir, entry.name)
                                require(dest.canonicalPath.startsWith(outDir.canonicalPath)) { "Path traversal" }
                                if (entry.isDirectory) dest.mkdirs()
                                else {
                                    dest.parentFile?.mkdirs()
                                    FileOutputStream(dest).use { tar.copyTo(it) }
                                }
                                entry = tar.nextEntry
                            }
                        }
                    }
                }

                // Verify if it's a valid model by checking for common sherpa files
                // Typically a sherpa-onnx model folder contains tokens.txt
                val extractedDirs = outDir.listFiles()?.filter { it.isDirectory } ?: emptyList()
                var isValid = false
                for (dir in extractedDirs) {
                    if (File(dir, "tokens.txt").exists()) {
                        isValid = true
                        break
                    }
                }
                
                if (!isValid && extractedDirs.isNotEmpty()) {
                    // Wait, sometimes models are deeper or tokens.txt might be named differently
                    // but tokens.txt is almost always required.
                    // Let's be lenient or specifically check for .onnx
                    for (dir in extractedDirs) {
                        val hasOnnx = dir.walkTopDown().any { it.name.endsWith(".onnx") }
                        if (hasOnnx) {
                            isValid = true
                            break
                        }
                    }
                }
                
                if (isValid) {
                    ImportResult.Success
                } else {
                    ImportResult.Error("No valid Sherpa-ONNX model found in archive. Ensure it contains .onnx files.")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                ImportResult.Error("Failed to extract: ${e.message}")
            }
        }
    }
}
