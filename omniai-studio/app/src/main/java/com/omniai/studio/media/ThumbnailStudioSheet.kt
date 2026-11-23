package com.omniai.studio.media

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omniai.studio.ui.theme.AmoledBlack
import com.omniai.studio.ui.theme.AmoledBorder
import com.omniai.studio.ui.theme.AmoledSurface
import com.omniai.studio.ui.theme.AmoledSurfaceVariant
import com.omniai.studio.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ThumbnailTextPosition(val label: String) {
    TOP("Top"),
    CENTER("Center"),
    BOTTOM("Bottom")
}

data class ThumbnailColorPreset(
    val label: String,
    val argb: Int,
    val composeColor: Color
)

private val THUMBNAIL_COLORS = listOf(
    ThumbnailColorPreset("Yellow", 0xFFFFE600.toInt(), Color(0xFFFFE600)),
    ThumbnailColorPreset("White", 0xFFFFFFFF.toInt(), Color(0xFFFFFFFF)),
    ThumbnailColorPreset("Cyber Cyan", 0xFF00E5FF.toInt(), Color(0xFF00E5FF)),
    ThumbnailColorPreset("Crimson", 0xFFFF3B30.toInt(), Color(0xFFFF3B30))
)

/**
 * On-Device Viral Thumbnail Studio (v5.3).
 * Uses native Android MediaMetadataRetriever + Bitmap + Canvas + Paint (zero external libs).
 * Exports crisp 1280x720 (16:9) or 1080x1920 (9:16) JPEG to Pictures/OmniAIStudio/ and binds
 * it as the active YouTube Direct Upload thumbnail.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThumbnailStudioSheet(
    videoFilePath: String?,
    defaultTitle: String,
    defaultAspectRatio: String = "16:9",
    adobeExpressStatus: AdobeResolutionResult? = null,
    adobeFireflyStatus: AdobeResolutionResult? = null,
    isLaunchingAdobe: Boolean = false,
    onRequestAiHook: (String, (String) -> Unit) -> Unit,
    onThumbnailExported: (String) -> Unit,
    onLaunchAdobeFirefly: ((String) -> Unit)? = null,
    onLaunchAdobeExpress: ((Bitmap?, Long, String) -> Unit)? = null,
    onBindAdobeApp: ((AdobeTargetApp, String) -> Unit)? = null,
    onResetAdobeApp: ((AdobeTargetApp) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var aspectRatio by remember { mutableStateOf(if (defaultAspectRatio == "9:16") "9:16" else "16:9") }
    var line1 by remember { mutableStateOf(defaultTitle.split(" ").take(3).joinToString(" ").uppercase(Locale.US).ifBlank { "WAIT FOR IT" }) }
    var line2 by remember { mutableStateOf("") }
    var isExtraBold by remember { mutableStateOf(true) }
    var selectedColor by remember { mutableStateOf(THUMBNAIL_COLORS.first()) }
    var textPosition by remember { mutableStateOf(ThumbnailTextPosition.BOTTOM) }
    var vignetteStrength by remember { mutableFloatStateOf(0.72f) }
    var strokeWidthPx by remember { mutableFloatStateOf(16f) }

    var videoDurationMs by remember { mutableLongStateOf(0L) }
    var framePositionMs by remember { mutableLongStateOf(1000L) }
    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isGeneratingHook by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }
    var exportStatus by remember { mutableStateOf("") }
    var pickerTargetAdobeApp by remember { mutableStateOf<AdobeTargetApp?>(null) }
    var localExpressStatus by remember(adobeExpressStatus) {
        mutableStateOf(adobeExpressStatus ?: AdobeAppBridge.resolveInstalledAdobeApp(context, AdobeTargetApp.ADOBE_EXPRESS))
    }
    var localFireflyStatus by remember(adobeFireflyStatus) {
        mutableStateOf(adobeFireflyStatus ?: AdobeAppBridge.resolveInstalledAdobeApp(context, AdobeTargetApp.ADOBE_FIREFLY))
    }

    // Probe MP4 duration and initial frame if a video file is provided
    LaunchedEffect(videoFilePath) {
        if (!videoFilePath.isNullOrBlank() && File(videoFilePath).exists()) {
            withContext(Dispatchers.IO) {
                val retriever = MediaMetadataRetriever()
                runCatching {
                    retriever.setDataSource(videoFilePath)
                    val dur = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                    videoDurationMs = dur.coerceAtLeast(1000L)
                    val initialMs = (videoDurationMs / 3L).coerceAtMost(videoDurationMs)
                    framePositionMs = initialMs
                    sourceBitmap = retriever.getFrameAtTime(initialMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                }
                runCatching { retriever.release() }
            }
        }
    }

    // Re-extract frame when scrubber slider changes
    LaunchedEffect(videoFilePath, framePositionMs) {
        if (!videoFilePath.isNullOrBlank() && File(videoFilePath).exists() && videoDurationMs > 0L) {
            withContext(Dispatchers.IO) {
                val retriever = MediaMetadataRetriever()
                runCatching {
                    retriever.setDataSource(videoFilePath)
                    retriever.getFrameAtTime(framePositionMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        ?.let { sourceBitmap = it }
                }
                runCatching { retriever.release() }
            }
        }
    }

    // Re-render live preview bitmap whenever controls change
    LaunchedEffect(sourceBitmap, aspectRatio, line1, line2, isExtraBold, selectedColor, textPosition, vignetteStrength, strokeWidthPx) {
        previewBitmap = withContext(Dispatchers.Default) {
            val previewWidth = if (aspectRatio == "9:16") 540 else 640
            val previewHeight = if (aspectRatio == "9:16") 960 else 360
            renderViralThumbnailBitmap(
                sourceBitmap = sourceBitmap,
                width = previewWidth,
                height = previewHeight,
                line1 = line1,
                line2 = line2,
                textColorArgb = selectedColor.argb,
                isExtraBold = isExtraBold,
                position = textPosition,
                vignetteStrength = vignetteStrength,
                strokeWidthScale = strokeWidthPx / 16f
            )
        }
    }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        BitmapFactory.decodeStream(stream)?.let { bmp ->
                            sourceBitmap = bmp
                        }
                    }
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AmoledSurface,
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Viral Thumbnail Studio",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "On-device Canvas renderer · Exports crisp ${if (aspectRatio == "9:16") "1080×1920" else "1280×720"} JPEG",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                }
            }

            // v5.4 Pro Adobe Workflow (Native Installed Apps — Zero Browser / Zero Custom Tabs)
            ProAdobeWorkflowCard(
                expressStatus = localExpressStatus,
                fireflyStatus = localFireflyStatus,
                isBusy = isLaunchingAdobe,
                onGenerateInFirefly = {
                    if (onLaunchAdobeFirefly != null) {
                        onLaunchAdobeFirefly(aspectRatio)
                    } else {
                        val fallbackPrompt = "Ultra-high contrast cinematic thumbnail key visual inspired by \"$defaultTitle\", dramatic rim lighting, expressive focal subject, $aspectRatio composition, strictly no text, no letters, no watermark."
                        AdobeAppBridge.launchAdobeFireflyWithPrompt(context, fallbackPrompt)
                    }
                },
                onEditFrameInExpress = {
                    val combinedHook = listOf(line1.trim(), line2.trim()).filter { it.isNotBlank() }.joinToString(" ")
                    if (onLaunchAdobeExpress != null) {
                        onLaunchAdobeExpress(sourceBitmap, framePositionMs, combinedHook)
                    } else {
                        val frameFile = sourceBitmap?.let { AdobeAppBridge.saveBitmapForAdobeHandoff(context, it) }
                            ?: videoFilePath?.let { AdobeAppBridge.extractVideoFrameToFile(context, it, framePositionMs) }
                        AdobeAppBridge.launchAdobeExpressWithFrame(context, frameFile, combinedHook)
                    }
                },
                onChangeBoundApp = { target -> pickerTargetAdobeApp = target }
            )

            // Live Preview Canvas
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = AmoledBlack,
                border = BorderStroke(1.dp, AmoledBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val bmp = previewBitmap
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Thumbnail preview",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth(if (aspectRatio == "9:16") 0.56f else 1f)
                                .aspectRatio(if (aspectRatio == "9:16") 9f / 16f else 16f / 9f)
                                .clip(RoundedCornerShape(12.dp))
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Rendering canvas preview...", color = TextSecondary)
                        }
                    }
                }
            }

            // Source controls: Frame Scrubber or Gallery Import
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = AmoledSurfaceVariant,
                border = BorderStroke(1.dp, AmoledBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Background Frame",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                        OutlinedButton(
                            onClick = {
                                photoPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                        ) {
                            Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Pick Image", fontSize = 12.sp)
                        }
                    }

                    if (videoDurationMs > 0L) {
                        Text(
                            text = "Video Frame Timestamp: ${String.format(Locale.US, "%.1fs", framePositionMs / 1000f)} / ${String.format(Locale.US, "%.1fs", videoDurationMs / 1000f)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        Slider(
                            value = framePositionMs.toFloat().coerceIn(0f, videoDurationMs.toFloat()),
                            onValueChange = { framePositionMs = it.toLong() },
                            valueRange = 0f..videoDurationMs.toFloat()
                        )
                    } else {
                        Text(
                            text = "No MP4 loaded — pick an image from Gallery or use the dark gradient backdrop.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = aspectRatio == "16:9",
                            onClick = { aspectRatio = "16:9" },
                            label = { Text("16:9 (1280×720)") }
                        )
                        FilterChip(
                            selected = aspectRatio == "9:16",
                            onClick = { aspectRatio = "9:16" },
                            label = { Text("9:16 Shorts (1080×1920)") }
                        )
                    }
                }
            }

            // Viral Text + 1-Tap AI Suggest 3-Word Hook
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Viral Overlay Text",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                    OutlinedButton(
                        enabled = !isGeneratingHook,
                        onClick = {
                            isGeneratingHook = true
                            onRequestAiHook(defaultTitle.ifBlank { line1 }) { hook ->
                                val words = hook.trim().replace("\"", "").split(Regex("\\s+")).filter { it.isNotBlank() }
                                if (words.size <= 3) {
                                    line1 = words.joinToString(" ").uppercase(Locale.US)
                                    line2 = ""
                                } else {
                                    val mid = (words.size + 1) / 2
                                    line1 = words.take(mid).joinToString(" ").uppercase(Locale.US)
                                    line2 = words.drop(mid).joinToString(" ").uppercase(Locale.US)
                                }
                                isGeneratingHook = false
                            }
                        }
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (isGeneratingHook) "Thinking..." else "AI Suggest 3-Word Thumbnail Hook",
                            fontSize = 11.sp
                        )
                    }
                }

                OutlinedTextField(
                    value = line1,
                    onValueChange = { line1 = it.take(36) },
                    label = { Text("Line 1 (Primary Hook)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = line2,
                    onValueChange = { line2 = it.take(36) },
                    label = { Text("Line 2 (Optional Punchline)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Color Chips + Font Weight + Position
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = AmoledSurfaceVariant,
                border = BorderStroke(1.dp, AmoledBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Text Color & Heavy Stroke", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        THUMBNAIL_COLORS.forEach { preset ->
                            val isSelected = selectedColor == preset
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(preset.composeColor)
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.35f),
                                        shape = CircleShape
                                    )
                                    .clickable { selectedColor = preset }
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        FilterChip(
                            selected = isExtraBold,
                            onClick = { isExtraBold = !isExtraBold },
                            label = { Text(if (isExtraBold) "ExtraBold" else "Bold") }
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThumbnailTextPosition.entries.forEach { pos ->
                            FilterChip(
                                selected = textPosition == pos,
                                onClick = { textPosition = pos },
                                label = { Text(pos.label) }
                            )
                        }
                    }

                    Text(
                        text = "Dark Bottom Vignette: ${(vignetteStrength * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Slider(
                        value = vignetteStrength,
                        onValueChange = { vignetteStrength = it },
                        valueRange = 0f..1f
                    )

                    Text(
                        text = "Black Outline Stroke: ${strokeWidthPx.toInt()} px",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Slider(
                        value = strokeWidthPx,
                        onValueChange = { strokeWidthPx = it },
                        valueRange = 6f..28f
                    )
                }
            }

            if (exportStatus.isNotBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF00E676), modifier = Modifier.size(18.dp))
                    Text(exportStatus, style = MaterialTheme.typography.bodySmall, color = Color(0xFF00E676))
                }
            }

            Button(
                enabled = !isExporting,
                onClick = {
                    isExporting = true
                    exportStatus = ""
                    scope.launch {
                        val savedPath = withContext(Dispatchers.IO) {
                            val outW = if (aspectRatio == "9:16") 1080 else 1280
                            val outH = if (aspectRatio == "9:16") 1920 else 720
                            val fullBitmap = renderViralThumbnailBitmap(
                                sourceBitmap = sourceBitmap,
                                width = outW,
                                height = outH,
                                line1 = line1,
                                line2 = line2,
                                textColorArgb = selectedColor.argb,
                                isExtraBold = isExtraBold,
                                position = textPosition,
                                vignetteStrength = vignetteStrength,
                                strokeWidthScale = strokeWidthPx / 16f
                            )
                            exportThumbnailJpeg(context, fullBitmap)
                        }
                        isExporting = false
                        if (savedPath != null) {
                            exportStatus = "Saved & bound for YouTube Direct Upload!"
                            onThumbnailExported(savedPath)
                        } else {
                            exportStatus = "Failed to export JPEG thumbnail."
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (isExporting) "Exporting HD JPEG..." else "Export JPEG & Bind to YouTube Upload",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    pickerTargetAdobeApp?.let { target ->
        val currentPkg = if (target == AdobeTargetApp.ADOBE_EXPRESS) {
            localExpressStatus.configuredPackage
        } else {
            localFireflyStatus.configuredPackage
        }
        AdobeAppPickerDialog(
            targetApp = target,
            currentPackage = currentPkg,
            onSelectPackage = { pkg ->
                if (onBindAdobeApp != null) {
                    onBindAdobeApp(target, pkg)
                } else {
                    AdobeAppBridge.setConfiguredPackage(context, target, pkg)
                }
                localExpressStatus = AdobeAppBridge.resolveInstalledAdobeApp(context, AdobeTargetApp.ADOBE_EXPRESS)
                localFireflyStatus = AdobeAppBridge.resolveInstalledAdobeApp(context, AdobeTargetApp.ADOBE_FIREFLY)
                pickerTargetAdobeApp = null
            },
            onResetDefault = {
                if (onResetAdobeApp != null) {
                    onResetAdobeApp(target)
                } else {
                    AdobeAppBridge.resetConfiguredPackage(context, target)
                }
                localExpressStatus = AdobeAppBridge.resolveInstalledAdobeApp(context, AdobeTargetApp.ADOBE_EXPRESS)
                localFireflyStatus = AdobeAppBridge.resolveInstalledAdobeApp(context, AdobeTargetApp.ADOBE_FIREFLY)
                pickerTargetAdobeApp = null
            },
            onDismiss = { pickerTargetAdobeApp = null }
        )
    }
}

/**
 * Pure native Android Bitmap/Canvas compositor for high-CTR YouTube & Shorts thumbnails.
 */
fun renderViralThumbnailBitmap(
    sourceBitmap: Bitmap?,
    width: Int,
    height: Int,
    line1: String,
    line2: String,
    textColorArgb: Int,
    isExtraBold: Boolean,
    position: ThumbnailTextPosition,
    vignetteStrength: Float,
    strokeWidthScale: Float = 1f
): Bitmap {
    val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)

    // 1. Background image (center-crop) or rich dark cyber gradient
    if (sourceBitmap != null) {
        val scale = maxOf(width.toFloat() / sourceBitmap.width, height.toFloat() / sourceBitmap.height)
        val dx = (width - sourceBitmap.width * scale) / 2f
        val dy = (height - sourceBitmap.height * scale) / 2f
        val matrix = Matrix().apply {
            postScale(scale, scale)
            postTranslate(dx, dy)
        }
        val imgPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(sourceBitmap, matrix, imgPaint)
    } else {
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, width.toFloat(), height.toFloat(),
                intArrayOf(0xFF0D1021.toInt(), 0xFF1A1033.toInt(), 0xFF09090C.toInt()),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
    }

    // 2. Dark Bottom Gradient Vignette for instant text contrast
    if (vignetteStrength > 0.01f) {
        val alpha = (vignetteStrength.coerceIn(0f, 1f) * 230).toInt()
        val vignetteStartY = when (position) {
            ThumbnailTextPosition.TOP -> 0f
            ThumbnailTextPosition.CENTER -> height * 0.2f
            ThumbnailTextPosition.BOTTOM -> height * 0.35f
        }
        val gradientColors = if (position == ThumbnailTextPosition.TOP) {
            intArrayOf((alpha shl 24) or 0x000000, 0x00000000)
        } else {
            intArrayOf(0x00000000, (alpha shl 24) or 0x000000)
        }
        val vignettePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f,
                vignetteStartY,
                0f,
                if (position == ThumbnailTextPosition.TOP) height * 0.55f else height.toFloat(),
                gradientColors,
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), vignettePaint)
    }

    // 3. Draw 1 or 2 viral overlay lines with heavy black outline stroke + drop shadow
    val lines = listOf(line1.trim(), line2.trim()).filter { it.isNotBlank() }
    if (lines.isNotEmpty()) {
        val baseTextSize = (minOf(width, height) * (if (isExtraBold) 0.145f else 0.125f))
        val typeface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Typeface.create(Typeface.SANS_SERIF, if (isExtraBold) 900 else 700, false)
        } else {
            Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }

        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = 0xFF000000.toInt()
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
            textAlign = Paint.Align.CENTER
            this.typeface = typeface
            setShadowLayer(baseTextSize * 0.16f, 0f, baseTextSize * 0.09f, 0xDD000000.toInt())
        }

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = textColorArgb
            textAlign = Paint.Align.CENTER
            this.typeface = typeface
        }

        // Auto-fit text size so widest line fits inside 90% width
        var fittedSize = baseTextSize
        val maxAllowedWidth = width * 0.90f
        while (fittedSize > 24f) {
            fillPaint.textSize = fittedSize
            val maxMeasured = lines.maxOf { fillPaint.measureText(it) }
            if (maxMeasured <= maxAllowedWidth) break
            fittedSize -= 4f
        }

        fillPaint.textSize = fittedSize
        strokePaint.textSize = fittedSize
        strokePaint.strokeWidth = (fittedSize * 0.17f * strokeWidthScale).coerceAtLeast(6f)

        val lineHeight = fittedSize * 1.15f
        val totalBlockHeight = lineHeight * lines.size
        val centerX = width / 2f
        val startY = when (position) {
            ThumbnailTextPosition.TOP -> height * 0.14f + fittedSize
            ThumbnailTextPosition.CENTER -> (height - totalBlockHeight) / 2f + fittedSize
            ThumbnailTextPosition.BOTTOM -> height * 0.91f - totalBlockHeight + fittedSize
        }

        lines.forEachIndexed { idx, text ->
            val y = startY + (idx * lineHeight)
            canvas.drawText(text, centerX, y, strokePaint)
            canvas.drawText(text, centerX, y, fillPaint)
        }
    }

    return output
}

/**
 * Exports the rendered thumbnail bitmap as a high-quality JPEG into Pictures/OmniAIStudio/
 * (via MediaStore on Android 10+) and keeps a direct app-accessible copy for YouTube API upload.
 */
fun exportThumbnailJpeg(context: Context, bitmap: Bitmap): String? {
    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val fileName = "OmniAI_Thumb_$stamp.jpg"

    // 1. Always save an app-accessible JPEG copy so OkHttp3 can upload directly via File path
    val localDir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "OmniAIStudio").apply {
        if (!exists()) mkdirs()
    }
    val localFile = File(localDir, fileName)
    runCatching {
        FileOutputStream(localFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 94, out)
        }
    }.onFailure { return null }

    // 2. Also export to shared Pictures/OmniAIStudio gallery via MediaStore on API 29+
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        runCatching {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/OmniAIStudio")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 94, out)
                }
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
        }
    }

    return localFile.absolutePath
}
