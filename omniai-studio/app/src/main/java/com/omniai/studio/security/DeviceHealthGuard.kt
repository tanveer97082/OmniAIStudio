package com.omniai.studio.security

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.StatFs

enum class ThermalHealthLevel(val label: String) {
    NORMAL("Normal"),
    MODERATE("Warm"),
    SEVERE("Throttling"),
    CRITICAL("Overheating")
}

data class DeviceHealthSnapshot(
    val batteryPercent: Int = 100,
    val isCharging: Boolean = false,
    val freeStorageMb: Long = 0L,
    val availableRamMb: Long = 0L,
    val isLowMemory: Boolean = false,
    val thermalLevel: ThermalHealthLevel = ThermalHealthLevel.NORMAL,
    val warnings: List<String> = emptyList()
) {
    val isSafeForHeavyMediaJob: Boolean
        get() = thermalLevel != ThermalHealthLevel.CRITICAL &&
            freeStorageMb >= MIN_SAFE_STORAGE_MB &&
            (!isLowMemory) &&
            (batteryPercent >= MIN_SAFE_BATTERY_PERCENT || isCharging)

    companion object {
        const val MIN_SAFE_STORAGE_MB = 350L
        const val MIN_SAFE_BATTERY_PERCENT = 12
    }
}

/**
 * v5.0 Device Thermal, Storage, RAM & Battery Health Guard:
 * Protects on-device Media3 video merges/compressions and large downloads from crashing or
 * overheating. Strictly guards API 29+ `PowerManager.currentThermalStatus` behind
 * `Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q` for full `minSdk = 26` compatibility.
 */
class DeviceHealthGuard(
    private val context: Context
) {
    private val appContext = context.applicationContext

    fun inspect(): DeviceHealthSnapshot {
        val batteryIntent = appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100)?.coerceAtLeast(1) ?: 100
        val batteryPct = if (level >= 0) ((level * 100) / scale).coerceIn(0, 100) else 100
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        val statFs = StatFs(appContext.filesDir.absolutePath)
        val freeStorageMb = statFs.availableBytes / (1024L * 1024L)

        val am = appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(memInfo)
        val availRamMb = memInfo.availMem / (1024L * 1024L)

        val thermalLevel = readThermalLevelSafely()

        val warnings = buildList {
            if (thermalLevel == ThermalHealthLevel.CRITICAL) {
                add("Device thermal status is CRITICAL — pause heavy Media3 encoding until cool.")
            } else if (thermalLevel == ThermalHealthLevel.SEVERE) {
                add("Device is thermally throttling — on-device rendering may run slower.")
            }
            if (freeStorageMb < DeviceHealthSnapshot.MIN_SAFE_STORAGE_MB) {
                add("Low internal storage (${freeStorageMb} MB free). Clear temp cache before exporting HD video.")
            }
            if (memInfo.lowMemory) {
                add("Android reports low available RAM (${availRamMb} MB free).")
            }
            if (!isCharging && batteryPct < DeviceHealthSnapshot.MIN_SAFE_BATTERY_PERCENT) {
                add("Battery critically low ($batteryPct%). Connect charger before long render jobs.")
            }
        }

        return DeviceHealthSnapshot(
            batteryPercent = batteryPct,
            isCharging = isCharging,
            freeStorageMb = freeStorageMb,
            availableRamMb = availRamMb,
            isLowMemory = memInfo.lowMemory,
            thermalLevel = thermalLevel,
            warnings = warnings
        )
    }

    fun guardReasonForHeavyMediaJob(): String? {
        val snap = inspect()
        if (snap.isSafeForHeavyMediaJob) return null
        return snap.warnings.firstOrNull()
            ?: "Device health guard paused heavy media processing (Battery ${snap.batteryPercent}%, Storage ${snap.freeStorageMb} MB)."
    }

    private fun readThermalLevelSafely(): ThermalHealthLevel {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val pm = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
            return when (pm?.currentThermalStatus) {
                PowerManager.THERMAL_STATUS_CRITICAL,
                PowerManager.THERMAL_STATUS_EMERGENCY,
                PowerManager.THERMAL_STATUS_SHUTDOWN -> ThermalHealthLevel.CRITICAL
                PowerManager.THERMAL_STATUS_SEVERE -> ThermalHealthLevel.SEVERE
                PowerManager.THERMAL_STATUS_MODERATE -> ThermalHealthLevel.MODERATE
                else -> ThermalHealthLevel.NORMAL
            }
        }
        return ThermalHealthLevel.NORMAL
    }
}
