package com.omniai.studio.service

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.omniai.studio.MainActivity

/**
 * Android Quick Settings Tile ("OmniAI Voice") that opens MainActivity directly
 * into Tab 0 (AI Chat Studio) with voice dictation triggered automatically.
 */
class QuickVoiceTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            label = "OmniAI Voice"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                subtitle = "Instant Voice AI"
            }
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            putExtra(EXTRA_LAUNCH_VOICE_CHAT, true)
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                REQUEST_CODE_VOICE_TILE,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(launchIntent)
        }
    }

    companion object {
        const val EXTRA_LAUNCH_VOICE_CHAT = "LAUNCH_VOICE_CHAT"
        private const val REQUEST_CODE_VOICE_TILE = 4041
    }
}
