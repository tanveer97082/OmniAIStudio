package com.omniai.studio.media

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.omniai.studio.ui.theme.AmoledBorder
import com.omniai.studio.ui.theme.AmoledSurface
import com.omniai.studio.ui.theme.AmoledSurfaceVariant
import com.omniai.studio.ui.theme.CrimsonDanger
import com.omniai.studio.ui.theme.EmeraldAccent
import com.omniai.studio.ui.theme.TextSecondary
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class AdobeTargetApp(
    val prefKey: String,
    val defaultPackage: String,
    val displayName: String,
    val matchKeywords: List<String>
) {
    ADOBE_EXPRESS(
        prefKey = "PKG_ADOBE_EXPRESS",
        defaultPackage = "com.adobe.spark.post",
        displayName = "Adobe Express",
        matchKeywords = listOf("express", "spark")
    ),
    ADOBE_FIREFLY(
        prefKey = "PKG_ADOBE_FIREFLY",
        defaultPackage = "com.adobe.firefly",
        displayName = "Adobe Firefly",
        matchKeywords = listOf("firefly")
    )
}

data class InstalledAppCandidate(
    val packageName: String,
    val appLabel: String,
    val isAdobeMatch: Boolean
)

data class AdobeResolutionResult(
    val target: AdobeTargetApp,
    val configuredPackage: String,
    val resolvedPackage: String?,
    val appLabel: String?,
    val isInstalled: Boolean
)

data class AdobeLaunchResult(
    val launched: Boolean,
    val message: String,
    val usedFallbackPackageLaunch: Boolean = false,
    val missingApp: Boolean = false
)

/**
 * Native Installed-App Redirection Bridge for Adobe Express & Adobe Firefly (v5.4).
 *
 * STRICT POLICY:
 * - Strictly native installed-app redirection only (zero browser tabs, zero embedded web renderers, zero fallback browser URLs).
 * - Uses only Android PackageManager, Intent, FileProvider, ClipboardManager, and MediaMetadataRetriever.
 */
object AdobeAppBridge {

    private const val PREFS_NAME = "omniai_adobe_bridge_prefs"
    const val KEY_PKG_ADOBE_EXPRESS = "PKG_ADOBE_EXPRESS"
    const val KEY_PKG_ADOBE_FIREFLY = "PKG_ADOBE_FIREFLY"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getConfiguredPackage(context: Context, targetType: AdobeTargetApp): String {
        val stored = prefs(context).getString(targetType.prefKey, null)?.trim().orEmpty()
        return stored.ifBlank { targetType.defaultPackage }
    }

    fun setConfiguredPackage(context: Context, targetType: AdobeTargetApp, packageName: String) {
        prefs(context).edit()
            .putString(targetType.prefKey, packageName.trim())
            .apply()
    }

    fun resetConfiguredPackage(context: Context, targetType: AdobeTargetApp) {
        prefs(context).edit()
            .putString(targetType.prefKey, targetType.defaultPackage)
            .apply()
    }

    /**
     * Smart 2-step resolver:
     * 1. Checks saved/default package via PackageManager.getLaunchIntentForPackage(pkg).
     * 2. Scans launcher activities for installed apps whose package or label matches
     *    "express"/"spark" (Express) or "firefly" (Firefly) and caches the matched package automatically.
     */
    fun resolveInstalledAdobeApp(
        context: Context,
        targetType: AdobeTargetApp
    ): AdobeResolutionResult {
        val pm = context.packageManager
        val configuredPkg = getConfiguredPackage(context, targetType)

        // Step 1: Direct package check
        val directIntent = pm.getLaunchIntentForPackage(configuredPkg)
        if (directIntent != null) {
            val label = resolveAppLabel(pm, configuredPkg) ?: targetType.displayName
            return AdobeResolutionResult(
                target = targetType,
                configuredPackage = configuredPkg,
                resolvedPackage = configuredPkg,
                appLabel = label,
                isInstalled = true
            )
        }

        // Step 2: Scan launcher activities for matching Adobe app
        val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val candidates = queryLauncherActivitiesCompat(pm, launcherIntent)
        val matched = candidates.firstOrNull { info ->
            val pkg = info.activityInfo?.packageName?.lowercase(Locale.US).orEmpty()
            val label = info.loadLabel(pm)?.toString()?.lowercase(Locale.US).orEmpty()
            val matchesKeyword = targetType.matchKeywords.any { kw ->
                pkg.contains(kw) || label.contains(kw)
            }
            val prefersAdobe = pkg.contains("adobe") || label.contains("adobe")
            matchesKeyword && prefersAdobe
        } ?: candidates.firstOrNull { info ->
            val pkg = info.activityInfo?.packageName?.lowercase(Locale.US).orEmpty()
            val label = info.loadLabel(pm)?.toString()?.lowercase(Locale.US).orEmpty()
            targetType.matchKeywords.any { kw -> pkg.contains(kw) || label.contains(kw) }
        }

        if (matched != null) {
            val matchedPkg = matched.activityInfo.packageName
            if (pm.getLaunchIntentForPackage(matchedPkg) != null) {
                setConfiguredPackage(context, targetType, matchedPkg)
                val label = matched.loadLabel(pm)?.toString()?.ifBlank { targetType.displayName }
                    ?: targetType.displayName
                return AdobeResolutionResult(
                    target = targetType,
                    configuredPackage = matchedPkg,
                    resolvedPackage = matchedPkg,
                    appLabel = label,
                    isInstalled = true
                )
            }
        }

        return AdobeResolutionResult(
            target = targetType,
            configuredPackage = configuredPkg,
            resolvedPackage = null,
            appLabel = null,
            isInstalled = false
        )
    }

    /**
     * Lists installed launcher apps for the manual "Select Installed Adobe App" picker dialog.
     * Adobe / Express / Firefly apps are sorted to the very top.
     */
    fun listInstalledLauncherApps(context: Context): List<InstalledAppCandidate> {
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val ownPackage = context.packageName
        val seen = LinkedHashSet<String>()
        val result = mutableListOf<InstalledAppCandidate>()

        queryLauncherActivitiesCompat(pm, launcherIntent).forEach { info ->
            val pkg = info.activityInfo?.packageName ?: return@forEach
            if (pkg == ownPackage || !seen.add(pkg)) return@forEach
            val label = info.loadLabel(pm)?.toString()?.trim()?.ifBlank { pkg } ?: pkg
            val lowerPkg = pkg.lowercase(Locale.US)
            val lowerLabel = label.lowercase(Locale.US)
            val isAdobe = lowerPkg.contains("adobe") ||
                lowerLabel.contains("adobe") ||
                lowerPkg.contains("firefly") ||
                lowerLabel.contains("firefly") ||
                lowerLabel.contains("express") ||
                lowerPkg.contains("spark")
            result += InstalledAppCandidate(
                packageName = pkg,
                appLabel = label,
                isAdobeMatch = isAdobe
            )
        }

        return result.sortedWith(
            compareByDescending<InstalledAppCandidate> { it.isAdobeMatch }
                .thenBy { it.appLabel.lowercase(Locale.US) }
        )
    }

    /**
     * Launches the installed Adobe Firefly native Android app with the AI visual prompt copied to clipboard.
     * Strictly NEVER falls back to a browser tab or embedded web renderer.
     */
    fun launchAdobeFireflyWithPrompt(
        context: Context,
        promptText: String
    ): AdobeLaunchResult {
        val cleanPrompt = promptText.trim()
        if (cleanPrompt.isNotBlank()) {
            copyToClipboard(context, "Adobe Firefly Thumbnail Prompt", cleanPrompt)
            Toast.makeText(
                context.applicationContext,
                "Thumbnail prompt copied! Paste in Adobe Firefly",
                Toast.LENGTH_LONG
            ).show()
        }

        val resolution = resolveInstalledAdobeApp(context, AdobeTargetApp.ADOBE_FIREFLY)
        val fireflyPkg = resolution.resolvedPackage
            ?: return AdobeLaunchResult(
                launched = false,
                missingApp = true,
                message = "Adobe Firefly app not found on this device. Install Adobe Firefly or tap 'Change Bound App' to select it."
            )

        // Optional non-browser deep link attempt strictly guarded by Android 11+ (API 30 / R)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val nonBrowserDeepLink = Intent(Intent.ACTION_VIEW, Uri.parse("firefly://generate")).apply {
                setPackage(fireflyPkg)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REQUIRE_NON_BROWSER)
            }
            val deepLinkWorked = try {
                context.startActivity(nonBrowserDeepLink)
                true
            } catch (_: ActivityNotFoundException) {
                false
            } catch (_: SecurityException) {
                false
            }
            if (deepLinkWorked) {
                return AdobeLaunchResult(
                    launched = true,
                    message = "Opened ${resolution.appLabel ?: "Adobe Firefly"} natively — paste prompt!"
                )
            }
        }

        // Direct native package launch (strictly never opens a browser)
        val launchIntent = context.packageManager.getLaunchIntentForPackage(fireflyPkg)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } ?: return AdobeLaunchResult(
            launched = false,
            missingApp = true,
            message = "Unable to create native launch intent for $fireflyPkg."
        )

        return try {
            context.startActivity(launchIntent)
            AdobeLaunchResult(
                launched = true,
                message = "Launched ${resolution.appLabel ?: "Adobe Firefly"} natively — paste your copied prompt!"
            )
        } catch (e: Exception) {
            AdobeLaunchResult(
                launched = false,
                message = "Failed to launch Adobe Firefly natively: ${e.message}"
            )
        }
    }

    /**
     * Shares an extracted HD video frame directly into the installed Adobe Express app via ACTION_SEND
     * with FileProvider URI + FLAG_GRANT_READ_URI_PERMISSION, copying optional viral hook text to clipboard.
     * If ACTION_SEND is rejected by a specific Express build, falls back strictly to direct native app launch
     * while preserving the HD frame in Pictures/OmniAIStudio/ for immediate picking from Recents.
     */
    fun launchAdobeExpressWithFrame(
        context: Context,
        frameFile: File?,
        optionalPromptText: String = ""
    ): AdobeLaunchResult {
        val cleanHook = optionalPromptText.trim()
        if (cleanHook.isNotBlank()) {
            copyToClipboard(context, "Adobe Express Thumbnail Hook", cleanHook)
        }

        val resolution = resolveInstalledAdobeApp(context, AdobeTargetApp.ADOBE_EXPRESS)
        val expressPkg = resolution.resolvedPackage
            ?: return AdobeLaunchResult(
                launched = false,
                missingApp = true,
                message = "Adobe Express app not found on this device. Install Adobe Express or tap 'Change Bound App' to select it."
            )

        if (frameFile != null && frameFile.exists()) {
            val shareableFile = ensureFileProviderAccessibleCopy(context, frameFile)
            // Always also persist a gallery copy in Pictures/OmniAIStudio/ so Recents works if needed
            saveFrameToPublicPictures(context, shareableFile)

            val contentUri = runCatching {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    shareableFile
                )
            }.getOrNull()

            if (contentUri != null) {
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/jpeg"
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    if (cleanHook.isNotBlank()) {
                        putExtra(Intent.EXTRA_TEXT, cleanHook)
                    }
                    setPackage(expressPkg)
                    clipData = ClipData.newRawUri("OmniAI HD Frame", contentUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                try {
                    context.grantUriPermission(
                        expressPkg,
                        contentUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                    context.startActivity(sendIntent)
                    val toastMsg = if (cleanHook.isNotBlank()) {
                        "Frame sent to Adobe Express! Hook text copied to clipboard."
                    } else {
                        "HD frame sent directly to Adobe Express!"
                    }
                    Toast.makeText(context.applicationContext, toastMsg, Toast.LENGTH_LONG).show()
                    return AdobeLaunchResult(
                        launched = true,
                        message = toastMsg
                    )
                } catch (_: ActivityNotFoundException) {
                    // Fall through to native package launch below (strictly no browser)
                } catch (_: SecurityException) {
                    // Fall through to native package launch below (strictly no browser)
                }
            }
        }

        // Direct native Adobe Express package launch fallback
        val launchIntent = context.packageManager.getLaunchIntentForPackage(expressPkg)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } ?: return AdobeLaunchResult(
            launched = false,
            missingApp = true,
            message = "Unable to launch $expressPkg natively."
        )

        return try {
            context.startActivity(launchIntent)
            val msg = if (frameFile != null && frameFile.exists()) {
                "Opened Adobe Express! Pick your HD frame from Recents (Pictures/OmniAIStudio) & paste hook text."
            } else {
                "Opened Adobe Express natively! Paste your copied hook text."
            }
            Toast.makeText(context.applicationContext, msg, Toast.LENGTH_LONG).show()
            AdobeLaunchResult(
                launched = true,
                usedFallbackPackageLaunch = true,
                message = msg
            )
        } catch (e: Exception) {
            AdobeLaunchResult(
                launched = false,
                message = "Failed to launch Adobe Express natively: ${e.message}"
            )
        }
    }

    /**
     * Extracts a single HD JPEG frame from an MP4 video via MediaMetadataRetriever
     * into filesDir/thumbnails/ (FileProvider-ready) and Pictures/OmniAIStudio/.
     */
    fun extractVideoFrameToFile(
        context: Context,
        videoFilePath: String,
        timestampMs: Long = 1000L
    ): File? {
        val videoFile = File(videoFilePath)
        if (!videoFile.exists()) return null
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(videoFilePath)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?: 0L
            val targetMs = if (timestampMs > 0L) {
                timestampMs.coerceAtMost(durationMs.coerceAtLeast(1000L))
            } else {
                (durationMs / 3L).coerceAtLeast(500L)
            }
            val bitmap = retriever.getFrameAtTime(
                targetMs * 1000L,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            ) ?: retriever.getFrameAtTime(0L)
                ?: return null
            saveBitmapForAdobeHandoff(context, bitmap)
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    /**
     * Saves an in-memory Bitmap (e.g. from ThumbnailStudioSheet) to a FileProvider-ready JPEG
     * inside filesDir/thumbnails/ so Adobe Express can open it via ACTION_SEND.
     */
    fun saveBitmapForAdobeHandoff(context: Context, bitmap: Bitmap): File? {
        val thumbDir = File(context.filesDir, "thumbnails").apply { if (!exists()) mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val outFile = File(thumbDir, "adobe_frame_$stamp.jpg")
        return runCatching {
            FileOutputStream(outFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
            saveFrameToPublicPictures(context, outFile)
            outFile
        }.getOrNull()
    }

    /**
     * Copies a thumbnail picked from Android PickVisualMedia after returning from Adobe Express / Firefly
     * into filesDir/thumbnails/script_{id}.jpg for persistent VideoScriptEntity & YouTubeApiClient binding.
     */
    fun copyPickedThumbnailToScriptStorage(
        context: Context,
        pickedUri: Uri,
        scriptId: Long?
    ): File? {
        val thumbDir = File(context.filesDir, "thumbnails").apply { if (!exists()) mkdirs() }
        val suffix = if (scriptId != null && scriptId > 0L) {
            "script_$scriptId"
        } else {
            "script_${System.currentTimeMillis()}"
        }
        val destFile = File(thumbDir, "$suffix.jpg")
        return runCatching {
            context.contentResolver.openInputStream(pickedUri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return null
            if (destFile.exists() && destFile.length() > 0L) destFile else null
        }.getOrNull()
    }

    private fun ensureFileProviderAccessibleCopy(context: Context, source: File): File {
        val thumbDir = File(context.filesDir, "thumbnails").apply { if (!exists()) mkdirs() }
        if (source.canonicalPath.startsWith(thumbDir.canonicalPath)) return source
        val dest = File(thumbDir, source.name.ifBlank { "adobe_frame_${System.currentTimeMillis()}.jpg" })
        return runCatching {
            source.copyTo(dest, overwrite = true)
            dest
        }.getOrDefault(source)
    }

    private fun saveFrameToPublicPictures(context: Context, sourceFile: File) {
        if (!sourceFile.exists()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, sourceFile.name)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/OmniAIStudio")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { out ->
                        sourceFile.inputStream().use { input -> input.copyTo(out) }
                    }
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                }
            }
        }
    }

    private fun copyToClipboard(context: Context, label: String, text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    private fun resolveAppLabel(pm: PackageManager, packageName: String): String? {
        return runCatching {
            val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(packageName, 0)
            }
            pm.getApplicationLabel(appInfo)?.toString()?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private fun queryLauncherActivitiesCompat(
        pm: PackageManager,
        intent: Intent
    ) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        pm.queryIntentActivities(intent, 0)
    }
}

/**
 * Reusable "Pro Adobe Workflow (Native Apps)" Compose card embedded across
 * ThumbnailStudioSheet, VideoDirectorScreen (Step 3), and DownloadsGalleryScreen.
 */
@Composable
fun ProAdobeWorkflowCard(
    expressStatus: AdobeResolutionResult,
    fireflyStatus: AdobeResolutionResult,
    isBusy: Boolean = false,
    onGenerateInFirefly: () -> Unit,
    onEditFrameInExpress: () -> Unit,
    onChangeBoundApp: (AdobeTargetApp) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = AmoledSurfaceVariant,
        border = BorderStroke(1.dp, AmoledBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Pro Adobe Workflow (Native Apps)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Direct installed-app handoff · Zero browser / Custom Tabs",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            // Live Installed / Not Found Badges + Manual Bind Triggers
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AdobeStatusBadgeChip(
                    label = "Firefly",
                    installed = fireflyStatus.isInstalled,
                    onClick = { onChangeBoundApp(AdobeTargetApp.ADOBE_FIREFLY) },
                    modifier = Modifier.weight(1f)
                )
                AdobeStatusBadgeChip(
                    label = "Express",
                    installed = expressStatus.isInstalled,
                    onClick = { onChangeBoundApp(AdobeTargetApp.ADOBE_EXPRESS) },
                    modifier = Modifier.weight(1f)
                )
            }

            // Primary Action 1: Generate in Adobe Firefly
            Button(
                enabled = !isBusy,
                onClick = onGenerateInFirefly,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4D4F))
            ) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (isBusy) "Crafting Firefly Prompt..." else "Generate in Adobe Firefly",
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Primary Action 2: Edit Frame in Adobe Express
            OutlinedButton(
                enabled = !isBusy,
                onClick = onEditFrameInExpress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
            ) {
                Icon(Icons.Filled.Brush, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Edit Frame in Adobe Express",
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun AdobeStatusBadgeChip(
    label: String,
    installed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = if (installed) EmeraldAccent else CrimsonDanger
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = accent.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = if (installed) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = "$label: ${if (installed) "Installed" else "Not Found"}",
                style = MaterialTheme.typography.labelSmall,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * In-app "Select Installed Adobe App" Picker Dialog.
 * Lets users manually bind any installed Android package for Adobe Express or Adobe Firefly.
 */
@Composable
fun AdobeAppPickerDialog(
    targetApp: AdobeTargetApp,
    currentPackage: String,
    onSelectPackage: (String) -> Unit,
    onResetDefault: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var search by remember { mutableStateOf("") }
    val allApps = remember(targetApp) { AdobeAppBridge.listInstalledLauncherApps(context) }
    val filtered = remember(allApps, search) {
        val q = search.trim()
        if (q.isBlank()) allApps
        else allApps.filter {
            it.appLabel.contains(q, ignoreCase = true) ||
                it.packageName.contains(q, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AmoledSurface,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Filled.Apps, contentDescription = null, tint = EmeraldAccent)
                Text("Select Installed ${targetApp.displayName}")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Bound package: $currentPackage",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    placeholder = { Text("Search installed apps...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (filtered.isEmpty()) {
                    Text(
                        text = "No matching installed launcher apps found. Make sure ${targetApp.displayName} is installed on this phone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filtered, key = { it.packageName }) { app ->
                            val isBound = app.packageName == currentPackage
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isBound) EmeraldAccent.copy(alpha = 0.14f) else AmoledSurfaceVariant,
                                border = BorderStroke(
                                    1.dp,
                                    if (isBound) EmeraldAccent else AmoledBorder
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectPackage(app.packageName) }
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = app.appLabel,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (app.isAdobeMatch) EmeraldAccent else Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (isBound) {
                                            Text(
                                                text = "BOUND",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = EmeraldAccent
                                            )
                                        }
                                    }
                                    Text(
                                        text = app.packageName,
                                        fontFamily = FontFamily.Monospace,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
                HorizontalDivider(color = AmoledBorder)
                Text(
                    text = "OmniAI launches only native installed apps — never a web browser.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onResetDefault) {
                Text("Reset Default")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
