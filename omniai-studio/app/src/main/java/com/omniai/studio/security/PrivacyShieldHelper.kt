package com.omniai.studio.security

import android.app.Activity
import android.view.WindowManager

object PrivacyShieldHelper {

    fun applyFlagSecure(activity: Activity, enabled: Boolean) {
        if (enabled) {
            activity.window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}
