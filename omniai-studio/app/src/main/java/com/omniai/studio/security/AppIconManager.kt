package com.omniai.studio.security

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

enum class AppIconVariant(
    val aliasSuffix: String,
    val displayTitle: String,
    val launcherLabel: String,
    val subtitle: String,
    val previewAccentHex: Long,
    val isStealth: Boolean
) {
    DEFAULT(
        aliasSuffix = ".AliasDefault",
        displayTitle = "Default Core",
        launcherLabel = "OmniAI Studio",
        subtitle = "AMOLED Neural Prism",
        previewAccentHex = 0xFF00E676,
        isStealth = false
    ),
    CYBER(
        aliasSuffix = ".AliasCyber",
        displayTitle = "Cyber Neon",
        launcherLabel = "OmniAI",
        subtitle = "Minimal Cyan Emblem",
        previewAccentHex = 0xFF00E5FF,
        isStealth = false
    ),
    CALCULATOR(
        aliasSuffix = ".AliasCalc",
        displayTitle = "Stealth Calc",
        launcherLabel = "Calculator",
        subtitle = "Disguised Utility Grid",
        previewAccentHex = 0xFFF59E0B,
        isStealth = true
    ),
    NOTES(
        aliasSuffix = ".AliasNotes",
        displayTitle = "Stealth Notes",
        launcherLabel = "Quick Notes",
        subtitle = "Disguised Notepad",
        previewAccentHex = 0xFFFBBF24,
        isStealth = true
    );

    companion object {
        fun fromStoredName(raw: String?): AppIconVariant =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: DEFAULT
    }
}

object AppIconManager {

    /**
     * Dynamically switches the active launcher `<activity-alias>` and app label without killing
     * the running process. Enables the target alias before disabling others so the launcher always
     * retains an active entry point.
     */
    fun switchLauncherIcon(context: Context, target: AppIconVariant) {
        val pm = context.packageManager
        val pkg = context.packageName

        val targetComponent = ComponentName(pkg, "$pkg${target.aliasSuffix}")
        pm.setComponentEnabledSetting(
            targetComponent,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )

        AppIconVariant.entries
            .filter { it != target }
            .forEach { other ->
                val comp = ComponentName(pkg, "$pkg${other.aliasSuffix}")
                pm.setComponentEnabledSetting(
                    comp,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
    }

    fun getActiveIconVariant(context: Context): AppIconVariant {
        val pm = context.packageManager
        val pkg = context.packageName
        for (variant in AppIconVariant.entries) {
            val state = pm.getComponentEnabledSetting(ComponentName(pkg, "$pkg${variant.aliasSuffix}"))
            if (state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                return variant
            }
        }
        return AppIconVariant.DEFAULT
    }
}
