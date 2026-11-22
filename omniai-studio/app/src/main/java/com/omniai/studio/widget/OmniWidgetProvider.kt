package com.omniai.studio.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.omniai.studio.MainActivity
import com.omniai.studio.R
import com.omniai.studio.data.local.OmniAiDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Android Home Screen Widget (`OmniWidgetProvider`).
 * Displays active Kaggle render queue status, weekly GPU hours remaining,
 * and quick-launch buttons for Voice Chat (`LAUNCH_VOICE_CHAT=true`) and Video Director (`LAUNCH_TAB=1`).
 */
class OmniWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val db = OmniAiDatabase.getInstance(context)
                val weekStart = System.currentTimeMillis() - (7L * 24L * 3600L * 1000L)
                val usedSeconds = db.renderLogDao().getTotalDurationSecondsSinceOnce(weekStart)
                val remainingHours = (30.0 - (usedSeconds / 3600.0)).coerceIn(0.0, 30.0)
                val latestJob = db.renderJobDao().getLatestJobOnce()

                val statusText = if (latestJob != null) {
                    "${latestJob.title}: ${latestJob.status} (${latestJob.progressPercent}%)"
                } else {
                    "Render Queue: Idle — Ready for Dual-GPU"
                }
                val gpuText = String.format(Locale.US, "%.1fh GPU left", remainingHours)

                appWidgetIds.forEach { widgetId ->
                    val views = buildRemoteViews(context, statusText, gpuText)
                    appWidgetManager.updateAppWidget(widgetId, views)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        fun requestRefresh(context: Context) {
            val appContext = context.applicationContext
            val manager = AppWidgetManager.getInstance(appContext)
            val component = ComponentName(appContext, OmniWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            if (ids.isNotEmpty()) {
                val intent = Intent(appContext, OmniWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
                appContext.sendBroadcast(intent)
            }
        }

        private fun buildRemoteViews(
            context: Context,
            statusText: String,
            gpuText: String
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.omni_widget_layout)
            views.setTextViewText(R.id.widget_render_status, statusText)
            views.setTextViewText(R.id.widget_gpu_hours, gpuText)

            val voiceIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(MainActivity.EXTRA_LAUNCH_VOICE_CHAT, true)
                putExtra(MainActivity.EXTRA_LAUNCH_TAB, 0)
            }
            val voicePi = PendingIntent.getActivity(
                context,
                8101,
                voiceIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_btn_voice_chat, voicePi)

            val directorIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(MainActivity.EXTRA_LAUNCH_TAB, 1)
            }
            val directorPi = PendingIntent.getActivity(
                context,
                8102,
                directorIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_btn_director, directorPi)

            val projectsIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(MainActivity.EXTRA_LAUNCH_TAB, 7)
            }
            val projectsPi = PendingIntent.getActivity(
                context,
                8103,
                projectsIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_btn_projects, projectsPi)

            return views
        }
    }
}
