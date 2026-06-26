package com.mhm.speaktowrite

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Persistent, file-backed logger for the Accessibility Service.
 *
 * Entries are appended to `files/service_log.txt`.  When the file exceeds
 * MAX_BYTES the oldest half is trimmed so the file never grows unbounded.
 *
 * Usage:
 *   ServiceLogger.init(context)          // call once, e.g. in onServiceConnected
 *   ServiceLogger.i("TAG", "message")
 *   ServiceLogger.e("TAG", "oh no", throwable)
 */
object ServiceLogger {

    private const val LOG_FILE = "service_log.txt"
    private const val MAX_BYTES = 200_000L   // ~200 KB before rotation
    private const val TAG = "ServiceLogger"

    private val fmt = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
    private val lock = Any()

    @Volatile private var logFile: File? = null

    // ── Init ──────────────────────────────────────────────────────────────

    fun init(context: Context) {
        synchronized(lock) {
            logFile = File(context.filesDir, LOG_FILE)
        }
        i(TAG, "=== ServiceLogger initialised (PID ${android.os.Process.myPid()}) ===")
    }

    // ── Public API ────────────────────────────────────────────────────────

    fun d(tag: String, msg: String) = write("D", tag, msg, null)
    fun i(tag: String, msg: String) = write("I", tag, msg, null)
    fun w(tag: String, msg: String, t: Throwable? = null) = write("W", tag, msg, t)
    fun e(tag: String, msg: String, t: Throwable? = null) = write("E", tag, msg, t)

    // ── Internal ──────────────────────────────────────────────────────────

    private fun write(level: String, tag: String, msg: String, t: Throwable?) {
        val time = fmt.format(Date())
        val thread = Thread.currentThread().name
        val line = "$time $level/$tag [$thread]: $msg"

        // Always mirror to Android logcat
        when (level) {
            "D" -> Log.d(tag, msg, t)
            "I" -> Log.i(tag, msg, t)
            "W" -> Log.w(tag, msg, t)
            "E" -> Log.e(tag, msg, t)
        }

        val file = logFile ?: return   // not yet initialised — logcat only

        synchronized(lock) {
            try {
                rotateIfNeeded(file)
                FileWriter(file, /* append= */ true).use { fw ->
                    fw.appendLine(line)
                    if (t != null) {
                        val sw = java.io.StringWriter()
                        t.printStackTrace(PrintWriter(sw))
                        // Indent each line of the stack trace
                        sw.toString().lines().forEach { stackLine ->
                            fw.appendLine("    $stackLine")
                        }
                    }
                }
            } catch (ex: Exception) {
                Log.e(TAG, "Failed to write to log file", ex)
            }
        }
    }

    /**
     * If the file is larger than MAX_BYTES, discard the first half of lines
     * so the newest entries are always preserved.
     */
    private fun rotateIfNeeded(file: File) {
        if (!file.exists() || file.length() < MAX_BYTES) return
        try {
            val lines = file.readLines()
            val half = lines.drop(lines.size / 2)
            file.writeText("--- log rotated ---\n" + half.joinToString("\n") + "\n")
        } catch (ex: Exception) {
            Log.e(TAG, "Log rotation failed", ex)
        }
    }
}
