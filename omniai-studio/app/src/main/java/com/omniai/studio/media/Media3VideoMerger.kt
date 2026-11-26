package com.omniai.studio.media

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class MergeClipSpec(
    val uri: Uri,
    val startSec: Double? = null,
    val endSec: Double? = null
)

data class VideoMergeRequest(
    val clips: List<MergeClipSpec>,
    val outputFileName: String,
    val muteOriginalAudio: Boolean = false,
    val backgroundAudioUri: Uri? = null,
    val autoCleanTempAfterMerge: Boolean = true
)

data class VideoMergeOutcome(
    val internalFile: File,
    val galleryUri: Uri?,
    val sizeBytes: Long
)

@OptIn(UnstableApi::class)
class Media3VideoMerger(private val context: Context) {

    /**
     * Merges 2+ MP4 clips on-device using AndroidX Media3 Transformer + EditedMediaItemSequence.
     * Supports per-clip Start/End trimming via MediaItem.ClippingConfiguration.Builder(),
     * muting original audio, overlaying a custom `.mp3`/`.m4a` background track, and optional
     * post-merge temp cache cleanup.
     */
    suspend fun mergeClips(
        request: VideoMergeRequest,
        onProgress: (Int) -> Unit
    ): VideoMergeOutcome = withContext(Dispatchers.Main) {
        require(request.clips.size >= 2) { "Select at least 2 video clips to merge." }

        val sanitizedName = request.outputFileName
            .trim()
            .ifBlank { "OmniAI_Merged_${System.currentTimeMillis()}" }
            .removeSuffix(".mp4")
            .replace(Regex("[^a-zA-Z0-9._-]"), "_") + ".mp4"

        val outDir = File(context.filesDir, "merged").apply { mkdirs() }
        val outputFile = File(outDir, sanitizedName)
        if (outputFile.exists()) {
            outputFile.delete()
        }

        val videoEditedItems = request.clips.map { clipSpec ->
            val mediaItemBuilder = MediaItem.Builder().setUri(clipSpec.uri)
            val startMs = clipSpec.startSec?.takeIf { it > 0.0 }?.let { (it * 1000.0).toLong() }
            val endMs = clipSpec.endSec?.takeIf { it > 0.0 }?.let { (it * 1000.0).toLong() }
            if (startMs != null || endMs != null) {
                val clipBuilder = MediaItem.ClippingConfiguration.Builder()
                if (startMs != null) {
                    clipBuilder.setStartPositionMs(startMs)
                }
                if (endMs != null && (startMs == null || endMs > startMs)) {
                    clipBuilder.setEndPositionMs(endMs)
                }
                mediaItemBuilder.setClippingConfiguration(clipBuilder.build())
            }
            EditedMediaItem.Builder(mediaItemBuilder.build())
                .setRemoveAudio(request.muteOriginalAudio)
                .build()
        }

        val videoSequence = EditedMediaItemSequence(videoEditedItems)
        val sequences = mutableListOf(videoSequence)

        if (request.backgroundAudioUri != null) {
            val bgAudioItem = EditedMediaItem.Builder(MediaItem.fromUri(request.backgroundAudioUri))
                .setRemoveVideo(true)
                .build()
            sequences.add(EditedMediaItemSequence(listOf(bgAudioItem), true))
        }

        val composition = Composition.Builder(sequences).build()
        val progressHolder = ProgressHolder()

        suspendCancellableCoroutine<Unit> { continuation ->
            var transformer: Transformer? = null

            val progressJob = launch(Dispatchers.Main) {
                while (isActive) {
                    delay(200L)
                    val currentTransformer = transformer
                    if (currentTransformer != null) {
                        val state = currentTransformer.getProgress(progressHolder)
                        if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                            onProgress(progressHolder.progress.coerceIn(0, 99))
                        }
                    }
                }
            }

            val createdTransformer = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        progressJob.cancel()
                        onProgress(100)
                        if (continuation.isActive) {
                            continuation.resume(Unit)
                        }
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException
                    ) {
                        progressJob.cancel()
                        if (continuation.isActive) {
                            continuation.resumeWithException(exportException)
                        }
                    }
                })
                .build()

            transformer = createdTransformer

            continuation.invokeOnCancellation {
                progressJob.cancel()
                transformer?.cancel()
            }

            createdTransformer.start(composition, outputFile.absolutePath)
        }

        val galleryUri = withContext(Dispatchers.IO) {
            val saved = StorageGalleryHelper.saveVideoToPublicGallery(context, outputFile, sanitizedName)
            if (request.autoCleanTempAfterMerge) {
                StorageGalleryHelper.cleanTempCache(context)
            }
            saved
        }

        VideoMergeOutcome(
            internalFile = outputFile,
            galleryUri = galleryUri,
            sizeBytes = outputFile.length()
        )
    }
}
