package com.omniai.studio.media

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream

data class StorageUsageSummary(
    val videosMb: Double,
    val mergedMb: Double,
    val tempCacheMb: Double,
    val dbMb: Double = 0.0
) {
    val totalMb: Double get() = videosMb + mergedMb + tempCacheMb + dbMb
}

object StorageGalleryHelper {

    fun saveVideoToPublicGallery(
        context: Context,
        sourceFile: File,
        displayName: String
    ): Uri? {
        if (!sourceFile.exists()) return null

        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/OmniAIStudio")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val itemUri = resolver.insert(collection, values) ?: return null
        resolver.openOutputStream(itemUri)?.use { outStream ->
            FileInputStream(sourceFile).use { inStream ->
                inStream.copyTo(outStream)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val doneValues = ContentValues().apply {
                put(MediaStore.Video.Media.IS_PENDING, 0)
            }
            resolver.update(itemUri, doneValues, null, null)
        }
        return itemUri
    }

    /**
     * Extracts a crisp HD frame via MediaMetadataRetriever.getFrameAtTime(...) and saves it
     * as a high-quality JPEG directly into `Pictures/OmniAIStudio/` via MediaStore.
     */
    fun saveVideoThumbnailToPictures(
        context: Context,
        videoFile: File,
        frameTimeUs: Long = 1_000_000L
    ): Uri? {
        if (!videoFile.exists()) return null

        val retriever = MediaMetadataRetriever()
        val bitmap: Bitmap = try {
            retriever.setDataSource(videoFile.absolutePath)
            retriever.getFrameAtTime(frameTimeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: return null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }

        val baseName = videoFile.nameWithoutExtension.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val thumbName = "${baseName}_thumb_${System.currentTimeMillis()}.jpg"
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, thumbName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/OmniAIStudio")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val itemUri = resolver.insert(collection, values)
        if (itemUri == null) {
            bitmap.recycle()
            return null
        }

        try {
            resolver.openOutputStream(itemUri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                out.flush()
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val doneValues = ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }
                resolver.update(itemUri, doneValues, null, null)
            }
        } finally {
            bitmap.recycle()
        }
        return itemUri
    }

    fun getShareUri(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    fun createShareVideoIntent(context: Context, file: File): Intent {
        val uri = getShareUri(context, file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun createInstallApkIntent(context: Context, apkFile: File): Intent {
        val uri = getShareUri(context, apkFile)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun computeStorageUsage(context: Context): StorageUsageSummary {
        val videosBytes = directorySizeBytes(File(context.filesDir, "videos"))
        val mergedBytes = directorySizeBytes(File(context.filesDir, "merged"))
        val cacheBytes = directorySizeBytes(context.cacheDir)
        val dbFile = context.getDatabasePath("omniai_studio_db")
        val dbBytes = listOf(
            dbFile,
            File("${dbFile.absolutePath}-wal"),
            File("${dbFile.absolutePath}-shm")
        ).filter { it.exists() }.sumOf { it.length() }

        return StorageUsageSummary(
            videosMb = bytesToMb(videosBytes),
            mergedMb = bytesToMb(mergedBytes),
            tempCacheMb = bytesToMb(cacheBytes),
            dbMb = bytesToMb(dbBytes)
        )
    }

    fun cleanTempCache(context: Context): Double {
        val before = directorySizeBytes(context.cacheDir)
        context.cacheDir.listFiles()?.forEach { child ->
            child.deleteRecursively()
        }
        val after = directorySizeBytes(context.cacheDir)
        return bytesToMb((before - after).coerceAtLeast(0L))
    }

    fun wipeAllAppStorage(context: Context) {
        File(context.filesDir, "videos").deleteRecursively()
        File(context.filesDir, "merged").deleteRecursively()
        File(context.filesDir, "exports").deleteRecursively()
        File(context.filesDir, "crash_log.txt").delete()
        context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
    }

    private fun directorySizeBytes(dir: File): Long {
        if (!dir.exists()) return 0L
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    private fun bytesToMb(bytes: Long): Double = bytes.toDouble() / (1024.0 * 1024.0)
}
