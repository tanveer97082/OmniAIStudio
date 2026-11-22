package com.omniai.studio.settings

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream

@Serializable
enum class UiThemePreset(val label: String, val tagline: String, val isLight: Boolean = false) {
    AMOLED_BLACK("AMOLED Black", "True #000000 OLED deep contrast"),
    CYBERPUNK_NEON("Cyberpunk Neon", "Electric cyan & magenta synthwave"),
    MATERIAL_YOU("Material You", "Android 12+ dynamic wallpaper palette"),
    MATRIX_TERMINAL("Matrix Terminal", "Classic phosphor green on obsidian"),
    NORD_SLATE("Nord Slate", "Arctic cool slate & frost blue"),
    MINIMAL_LIGHT("Minimal Light", "Crisp daylight studio surface", isLight = true)
}

@Serializable
enum class ChatBubbleStyle(val label: String) {
    ROUNDED("Rounded"),
    SHARP_CYBER("Sharp Cyber"),
    MINIMAL_FLAT("Minimal Flat"),
    TERMINAL_MONO("Terminal Mono")
}

@Serializable
enum class UiDensity(val label: String, val cardPaddingDp: Int, val itemSpacingDp: Int) {
    COMPACT("Compact", 8, 6),
    NORMAL("Normal", 12, 10),
    SPACIOUS("Spacious", 16, 14)
}

@Serializable
enum class AppFontFamily(val label: String) {
    DEFAULT_SANS("Default Sans"),
    MONOSPACE("Monospace"),
    SERIF("Editorial Serif")
}

@Serializable
enum class QuickFabAction(val label: String) {
    VOICE_CHAT("Voice Chat"),
    QUICK_TERMINAL("Quick Terminal"),
    OCR_SCAN("OCR Lens Scan"),
    NEW_VIDEO("New Video"),
    GIT_AUTO_PUSH("Git Auto-Push"),
    DISABLED("Hidden")
}

@Serializable
enum class StudioTabId(val label: String) {
    HOME("Home"),
    CHAT("Chat"),
    DIRECTOR("Studio"),
    PROJECTS("Projects"),
    MERGER("Media"),
    TERMINAL("Terminal"),
    GALLERY("Gallery"),
    CONTENT_HUB("Content"),
    VAULT("Vault")
}

@Serializable
data class UiCustomizationConfig(
    val themePreset: UiThemePreset = UiThemePreset.AMOLED_BLACK,
    val customAccentHex: String = "#00F0FF",
    val useCustomAccent: Boolean = false,
    val chatBubbleStyle: ChatBubbleStyle = ChatBubbleStyle.ROUNDED,
    val cardCornerRadiusDp: Int = 14,
    val uiDensity: UiDensity = UiDensity.NORMAL,
    val appFontFamily: AppFontFamily = AppFontFamily.DEFAULT_SANS,
    val terminalFontSizeSp: Int = 13,
    val chatWallpaperPath: String = "",
    val wallpaperDimOpacity: Float = 0.65f,
    val bottomNavTabs: List<StudioTabId> = listOf(
        StudioTabId.HOME,
        StudioTabId.CHAT,
        StudioTabId.DIRECTOR,
        StudioTabId.PROJECTS
    ),
    val defaultStartupTab: StudioTabId = StudioTabId.HOME,
    val quickFabAction: QuickFabAction = QuickFabAction.QUICK_TERMINAL,
    val hapticFeedbackEnabled: Boolean = true,
    val reducedAnimations: Boolean = false,
    val pinnedSettingIds: List<String> = listOf(
        "theme_studio",
        "backup_center",
        "network_inspector",
        "haptics_toggle"
    )
)

class UiConfigRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    private val _config = MutableStateFlow(loadConfig())
    val config: StateFlow<UiCustomizationConfig> = _config.asStateFlow()

    private fun loadConfig(): UiCustomizationConfig {
        val raw = prefs.getString(KEY_CONFIG_JSON, null) ?: return UiCustomizationConfig()
        return runCatching {
            sanitizeConfig(json.decodeFromString(UiCustomizationConfig.serializer(), raw))
        }.getOrElse {
            UiCustomizationConfig()
        }
    }

    private fun saveConfig(updated: UiCustomizationConfig) {
        val clean = sanitizeConfig(updated)
        prefs.edit().putString(KEY_CONFIG_JSON, json.encodeToString(clean)).apply()
        _config.value = clean
    }

    fun update(transform: (UiCustomizationConfig) -> UiCustomizationConfig) {
        saveConfig(transform(_config.value))
    }

    fun resetToDefaults() {
        clearChatWallpaper()
        saveConfig(UiCustomizationConfig())
    }

    fun exportThemeJson(): String = json.encodeToString(_config.value)

    fun importThemeJson(rawJson: String): Result<UiCustomizationConfig> = runCatching {
        val parsed = json.decodeFromString(UiCustomizationConfig.serializer(), rawJson.trim())
        val sanitized = sanitizeConfig(parsed).copy(
            // Preserve local device wallpaper path if imported theme references a non-existent path
            chatWallpaperPath = parsed.chatWallpaperPath.takeIf { path ->
                path.isNotBlank() && File(path).exists()
            } ?: _config.value.chatWallpaperPath
        )
        saveConfig(sanitized)
        sanitized
    }

    fun saveChatWallpaperFromUri(uri: Uri): Result<String> = runCatching {
        val outFile = File(context.filesDir, WALLPAPER_FILENAME)
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(outFile).use { output ->
                input.copyTo(output)
            }
        } ?: error("Unable to open selected wallpaper image")
        val path = outFile.absolutePath
        update { it.copy(chatWallpaperPath = path) }
        path
    }

    fun clearChatWallpaper() {
        runCatching {
            val outFile = File(context.filesDir, WALLPAPER_FILENAME)
            if (outFile.exists()) outFile.delete()
        }
        update { it.copy(chatWallpaperPath = "") }
    }

    fun toggleBottomNavTab(tab: StudioTabId): Boolean {
        val current = _config.value.bottomNavTabs.toMutableList()
        if (tab in current) {
            if (current.size <= MIN_NAV_TABS) return false
            current.remove(tab)
        } else {
            if (current.size >= MAX_NAV_TABS) return false
            current.add(tab)
        }
        update { it.copy(bottomNavTabs = current) }
        return true
    }

    fun moveBottomNavTab(tab: StudioTabId, direction: Int): Boolean {
        val current = _config.value.bottomNavTabs.toMutableList()
        val index = current.indexOf(tab)
        if (index < 0) return false
        val target = index + direction
        if (target !in current.indices) return false
        val removed = current.removeAt(index)
        current.add(target, removed)
        update { it.copy(bottomNavTabs = current) }
        return true
    }

    fun togglePinnedSetting(settingId: String) {
        update { cfg ->
            val current = cfg.pinnedSettingIds.toMutableList()
            if (settingId in current) {
                current.remove(settingId)
            } else {
                current.add(0, settingId)
            }
            cfg.copy(pinnedSettingIds = current.distinct())
        }
    }

    private fun sanitizeConfig(cfg: UiCustomizationConfig): UiCustomizationConfig {
        val validHex = if (HEX_REGEX.matches(cfg.customAccentHex.trim())) {
            cfg.customAccentHex.trim().uppercase()
        } else {
            "#00F0FF"
        }
        val distinctTabs = cfg.bottomNavTabs.distinct().take(MAX_NAV_TABS).let { list ->
            if (list.size >= MIN_NAV_TABS) list
            else listOf(StudioTabId.HOME, StudioTabId.CHAT, StudioTabId.DIRECTOR, StudioTabId.PROJECTS)
        }
        return cfg.copy(
            customAccentHex = validHex,
            cardCornerRadiusDp = cfg.cardCornerRadiusDp.coerceIn(0, 24),
            terminalFontSizeSp = cfg.terminalFontSizeSp.coerceIn(10, 18),
            wallpaperDimOpacity = cfg.wallpaperDimOpacity.coerceIn(0.2f, 0.95f),
            bottomNavTabs = distinctTabs,
            pinnedSettingIds = cfg.pinnedSettingIds.distinct()
        )
    }

    companion object {
        private const val PREFS_NAME = "omniai_ui_customization_v3"
        private const val KEY_CONFIG_JSON = "ui_config_json"
        private const val WALLPAPER_FILENAME = "wallpaper.jpg"
        const val MIN_NAV_TABS = 2
        const val MAX_NAV_TABS = 5
        private val HEX_REGEX = Regex("^#[0-9A-Fa-f]{6}$")
    }
}
