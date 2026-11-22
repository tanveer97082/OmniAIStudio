package com.omniai.studio.security

import android.content.Context
import android.os.Build
import com.omniai.studio.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Uncaught exception diagnostic logger that persists crash stack traces to `filesDir/crash_log.txt`
 * so users can inspect or copy crash diagnostics directly from Settings -> Storage & Crash Diagnostics.
 */
class CrashHandler private constructor(
    private val appContext: Context,
    private val previousHandler: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            recordCrash(appContext, thread.name, throwable)
        } catch (_: Throwable) {
            // Never throw recursively from an uncaught exception handler
        } finally {
            previousHandler?.uncaughtException(thread, throwable)
        }
    }

    companion object {
        private const val CRASH_LOG_FILE = "crash_log.txt"
        private const val MAX_LOG_BYTES = 100 * 1024 // 100 KB rolling cap

        fun install(context: Context) {
            val appCtx = context.applicationContext
            val current = Thread.getDefaultUncaughtExceptionHandler()
            if (current is CrashHandler) return
            Thread.setDefaultUncaughtExceptionHandler(CrashHandler(appCtx, current))
        }

        fun getCrashLogFile(context: Context): File = File(context.filesDir, CRASH_LOG_FILE)

        fun recordCrash(context: Context, threadName: String, throwable: Throwable) {
            val file = getCrashLogFile(context)
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US).format(Date())
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))

            val entry = buildString {
                appendLine("====================================================")
                appendLine("Timestamp : $timestamp")
                appendLine("App Build : v${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE})")
                appendLine("Device    : ${Build.MANUFACTURER} ${Build.MODEL} (API ${Build.VERSION.SDK_INT})")
                appendLine("Thread    : $threadName")
                appendLine("Exception : ${throwable::class.java.name}: ${throwable.message}")
                appendLine("----------------------------------------------------")
                appendLine(sw.toString().trimEnd())
                appendLine()
            }

            val existing = if (file.exists()) file.readText(Charsets.UTF_8) else ""
            val combined = (entry + existing)
            val trimmed = if (combined.toByteArray(Charsets.UTF_8).size > MAX_LOG_BYTES) {
                combined.take(MAX_LOG_BYTES)
            } else {
                combined
            }
            file.writeText(trimmed, Charsets.UTF_8)
        }

        fun readCrashLog(context: Context): String {
            val file = getCrashLogFile(context)
            return if (file.exists()) file.readText(Charsets.UTF_8) else ""
        }

        fun clearCrashLog(context: Context) {
            val file = getCrashLogFile(context)
            if (file.exists()) {
                file.delete()
            }
        }
    }
}
