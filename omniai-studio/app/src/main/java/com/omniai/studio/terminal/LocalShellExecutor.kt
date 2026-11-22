package com.omniai.studio.terminal

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicReference

enum class TerminalLineType {
    COMMAND,
    STDOUT,
    STDERR,
    SYSTEM,
    AI_STREAM,
    NETLOG,
    SUCCESS
}

data class TerminalLine(
    val id: Long,
    val text: String,
    val type: TerminalLineType = TerminalLineType.STDOUT
)

/**
 * Native Android Sandbox Shell Engine executing `/system/bin/sh -c <command>` inside `filesDir/workspace`.
 * Persists working directory across `cd` invocations and supports forcible process cancellation (CTRL+C).
 */
class LocalShellExecutor(context: Context) {

    private val workspaceRoot: File = File(context.filesDir, "workspace").apply { mkdirs() }
    private val currentDirRef = AtomicReference(workspaceRoot)
    private val runningProcessRef = AtomicReference<Process?>(null)

    fun currentWorkingDir(): File {
        val dir = currentDirRef.get()
        if (!dir.exists()) {
            workspaceRoot.mkdirs()
            currentDirRef.set(workspaceRoot)
            return workspaceRoot
        }
        return dir
    }

    fun currentWorkingDirLabel(): String {
        val cwd = currentWorkingDir().canonicalPath
        val root = workspaceRoot.canonicalPath
        return when {
            cwd == root -> "~/workspace"
            cwd.startsWith("$root/") -> "~/workspace/${cwd.removePrefix("$root/")}"
            else -> cwd
        }
    }

    fun isRunning(): Boolean = runningProcessRef.get()?.isAlive == true

    fun stopRunningProcess(): Boolean {
        val proc = runningProcessRef.getAndSet(null) ?: return false
        return try {
            proc.destroyForcibly()
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun execute(
        command: String,
        onLine: (String, TerminalLineType) -> Unit
    ): Int = withContext(Dispatchers.IO) {
        val trimmed = command.trim()
        if (trimmed.isEmpty()) return@withContext 0

        val cwd = currentWorkingDir()
        val marker = "__OMNIAI_CWD_MARKER_${System.nanoTime()}__"
        val wrappedCommand = "$trimmed\n__OMNIAI_RC=\$?\nprintf '\\n$marker:%s\\n' \"\$PWD\"\nexit \$__OMNIAI_RC"

        val process = try {
            ProcessBuilder("/system/bin/sh", "-c", wrappedCommand)
                .directory(cwd)
                .apply {
                    environment()["HOME"] = workspaceRoot.absolutePath
                    environment()["PWD"] = cwd.absolutePath
                    environment()["TERM"] = "xterm-256color"
                }
                .start()
        } catch (ex: Exception) {
            onLine("Shell launch error: ${ex.message}", TerminalLineType.STDERR)
            return@withContext -1
        }

        runningProcessRef.set(process)

        try {
            coroutineScope {
                val stdoutJob = async(Dispatchers.IO) {
                    BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            val current = line ?: continue
                            if (current.startsWith("$marker:")) {
                                val nextPath = current.removePrefix("$marker:").trim()
                                if (nextPath.isNotBlank()) {
                                    val candidate = File(nextPath)
                                    if (candidate.exists() && candidate.isDirectory) {
                                        currentDirRef.set(candidate)
                                    }
                                }
                            } else {
                                onLine(current, TerminalLineType.STDOUT)
                            }
                        }
                    }
                }
                val stderrJob = async(Dispatchers.IO) {
                    BufferedReader(InputStreamReader(process.errorStream)).use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            val current = line ?: continue
                            onLine(current, TerminalLineType.STDERR)
                        }
                    }
                }
                stdoutJob.await()
                stderrJob.await()
            }
            process.waitFor()
        } catch (ex: InterruptedException) {
            process.destroyForcibly()
            onLine("^C Process interrupted.", TerminalLineType.STDERR)
            130
        } catch (ex: Exception) {
            onLine("Process error: ${ex.message}", TerminalLineType.STDERR)
            -1
        } finally {
            runningProcessRef.compareAndSet(process, null)
        }
    }
}
