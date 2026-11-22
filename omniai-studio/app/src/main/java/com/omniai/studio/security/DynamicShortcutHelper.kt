package com.omniai.studio.security

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import com.omniai.studio.MainActivity
import com.omniai.studio.R

/**
 * Registers 4 native Android dynamic home-screen long-press shortcuts via ShortcutManager:
 * 1) Voice Chat
 * 2) Video Director
 * 3) Scan OCR
 * 4) Backup Now
 */
object DynamicShortcutHelper {

    const val SHORTCUT_VOICE_CHAT = "omniai_shortcut_voice_chat"
    const val SHORTCUT_VIDEO_DIRECTOR = "omniai_shortcut_video_director"
    const val SHORTCUT_SCAN_OCR = "omniai_shortcut_scan_ocr"
    const val SHORTCUT_BACKUP_NOW = "omniai_shortcut_backup_now"

    fun registerShortcuts(context: Context) {
        val manager = context.getSystemService(ShortcutManager::class.java) ?: return
        val pkg = context.packageName

        val voiceIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra(MainActivity.EXTRA_LAUNCH_TAB, 0)
            putExtra(MainActivity.EXTRA_LAUNCH_VOICE_CHAT, true)
        }

        val directorIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra(MainActivity.EXTRA_LAUNCH_TAB, 1)
        }

        val ocrIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra(MainActivity.EXTRA_LAUNCH_TAB, 0)
            putExtra(MainActivity.EXTRA_LAUNCH_OCR, true)
        }

        val backupIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra(MainActivity.EXTRA_LAUNCH_BACKUP_CENTER, true)
        }

        val shortcuts = listOf(
            ShortcutInfo.Builder(context, SHORTCUT_VOICE_CHAT)
                .setShortLabel("Voice Chat")
                .setLongLabel("Launch OmniAI Voice Chat")
                .setIcon(Icon.createWithResource(pkg, R.drawable.ic_fg_default))
                .setIntent(voiceIntent)
                .build(),
            ShortcutInfo.Builder(context, SHORTCUT_VIDEO_DIRECTOR)
                .setShortLabel("Video Director")
                .setLongLabel("Open AI Video Director Pro")
                .setIcon(Icon.createWithResource(pkg, R.drawable.ic_fg_cyber))
                .setIntent(directorIntent)
                .build(),
            ShortcutInfo.Builder(context, SHORTCUT_SCAN_OCR)
                .setShortLabel("Scan OCR")
                .setLongLabel("Offline Hindi/English Image OCR")
                .setIcon(Icon.createWithResource(pkg, R.drawable.ic_fg_notes))
                .setIntent(ocrIntent)
                .build(),
            ShortcutInfo.Builder(context, SHORTCUT_BACKUP_NOW)
                .setShortLabel("Backup Now")
                .setLongLabel("Open 4-Layer Encrypted Backup Center")
                .setIcon(Icon.createWithResource(pkg, R.drawable.ic_fg_calc))
                .setIntent(backupIntent)
                .build()
        )

        runCatching {
            manager.dynamicShortcuts = shortcuts
        }
    }
}
