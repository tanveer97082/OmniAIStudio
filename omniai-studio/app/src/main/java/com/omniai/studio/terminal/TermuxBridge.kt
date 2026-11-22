package com.omniai.studio.terminal

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

data class TermuxDispatchResult(
    val dispatched: Boolean,
    val message: String
)

/**
 * Termux Bridge Integration dispatching commands via explicit `com.termux.RUN_COMMAND` intent.
 * Requires `<uses-permission android:name="com.termux.permission.RUN_COMMAND" />` and `<queries>`
 * for `com.termux`, plus `allow-external-apps=true` inside `~/.termux/termux.properties`.
 */
class TermuxBridge(private val context: Context) {

    companion object {
        const val TERMUX_PACKAGE = "com.termux"
        const val TERMUX_SERVICE_CLASS = "com.termux.app.RunCommandService"
        const val ACTION_RUN_COMMAND = "com.termux.RUN_COMMAND"
        const val EXTRA_COMMAND_PATH = "com.termux.RUN_COMMAND_PATH"
        const val EXTRA_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS"
        const val EXTRA_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
        const val EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"
        const val TERMUX_SH_PATH = "/data/data/com.termux/files/usr/bin/sh"
        const val TERMUX_HOME_DIR = "/data/data/com.termux/files/home"
    }

    fun isTermuxInstalled(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    TERMUX_PACKAGE,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(TERMUX_PACKAGE, 0)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun dispatchCommand(
        shellCommand: String,
        workDir: String = TERMUX_HOME_DIR,
        runInBackground: Boolean = false
    ): TermuxDispatchResult {
        if (!isTermuxInstalled()) {
            return TermuxDispatchResult(
                dispatched = false,
                message = "Termux ($TERMUX_PACKAGE) is not installed. Install Termux from F-Droid/GitHub and enable allow-external-apps=true in ~/.termux/termux.properties."
            )
        }

        val intent = Intent(ACTION_RUN_COMMAND).apply {
            setClassName(TERMUX_PACKAGE, TERMUX_SERVICE_CLASS)
            putExtra(EXTRA_COMMAND_PATH, TERMUX_SH_PATH)
            putExtra(EXTRA_ARGUMENTS, arrayOf("-lc", shellCommand))
            putExtra(EXTRA_WORKDIR, workDir)
            putExtra(EXTRA_BACKGROUND, runInBackground)
        }

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            TermuxDispatchResult(
                dispatched = true,
                message = "Dispatched to Termux Bridge ($workDir). If Termux blocks external commands, run: echo 'allow-external-apps=true' >> ~/.termux/termux.properties"
            )
        } catch (sec: SecurityException) {
            TermuxDispatchResult(
                dispatched = false,
                message = "Termux RUN_COMMAND permission denied. Grant permission and set allow-external-apps=true in ~/.termux/termux.properties."
            )
        } catch (ex: Exception) {
            TermuxDispatchResult(
                dispatched = false,
                message = "Termux Bridge failed: ${ex.message}. Ensure Termux is installed and allow-external-apps=true is enabled in ~/.termux/termux.properties."
            )
        }
    }
}
