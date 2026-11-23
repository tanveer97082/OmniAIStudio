package com.omniai.studio.cloud

import android.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

data class GzipCompressionResult(
    val encodedPayload: String,
    val rawBytes: Int,
    val compressedBytes: Int,
    val savedPercent: Int
) {
    val summaryLabel: String
        get() = "${formatKb(rawBytes)} -> ${formatKb(compressedBytes)}, ${savedPercent}% saved"

    companion object {
        fun formatKb(bytes: Int): String {
            return if (bytes < 1024) {
                "$bytes B"
            } else {
                String.format(Locale.US, "%.1f KB", bytes / 1024.0)
            }
        }
    }
}

/**
 * Pure Java/Android GZIP + Base64 text codec enforcing strict text-only cloud sync payloads.
 * Strips local filesystem/media paths and rejects binary data URIs or payloads exceeding 256 KB.
 */
object PayloadGzipCodec {

    const val PREFIX = "gz:"
    const val MAX_COMPRESSED_BYTES = 262_144 // 256 KB hard PostgreSQL column cap
    private const val MAX_DECOMPRESSED_BYTES = 1_048_576 // 1 MB decompression safety limit

    private val jsonParser = Json { ignoreUnknownKeys = true }

    private val forbiddenLocalPathKeys = setOf(
        "linkedVideoPath",
        "thumbnailPath",
        "renderedClipPath",
        "outputFilePath",
        "filePath",
        "refImagePath",
        "localPath",
        "localFilePath",
        "apkPath"
    )

    private val binaryUriPrefixes = listOf(
        "data:image/",
        "data:video/",
        "data:audio/",
        "data:application/octet-stream",
        "content://",
        "file://"
    )

    /**
     * Recursively strips local file path fields and rejects inline binary URIs before cloud upload.
     */
    fun sanitizeTextOnlyJson(rawJson: String): String {
        val trimmed = rawJson.trim()
        require(trimmed.isNotEmpty()) { "Payload JSON cannot be empty" }
        val element = runCatching { jsonParser.parseToJsonElement(trimmed) }.getOrNull()
            ?: throw IllegalArgumentException("Payload must be valid UTF-8 JSON")
        val sanitized = sanitizeElement(element)
        return sanitized.toString()
    }

    private fun sanitizeElement(element: JsonElement): JsonElement = when (element) {
        is JsonObject -> {
            val cleaned = LinkedHashMap<String, JsonElement>(element.size)
            element.forEach { (key, value) ->
                if (key in forbiddenLocalPathKeys) {
                    cleaned[key] = JsonPrimitive("")
                } else {
                    cleaned[key] = sanitizeElement(value)
                }
            }
            JsonObject(cleaned)
        }
        is JsonArray -> JsonArray(element.map { sanitizeElement(it) })
        is JsonPrimitive -> {
            if (element.isString) {
                val content = element.content
                val lower = content.lowercase(Locale.US)
                if (binaryUriPrefixes.any { lower.startsWith(it) }) {
                    throw IllegalArgumentException("Binary/local URI blobs are forbidden in text-only cloud sync")
                }
                if (looksLikeDeviceAbsoluteMediaPath(content)) {
                    JsonPrimitive("")
                } else {
                    element
                }
            } else {
                element
            }
        }
    }

    private fun looksLikeDeviceAbsoluteMediaPath(value: String): Boolean {
        val trimmed = value.trim()
        if (!trimmed.startsWith("/storage/") && !trimmed.startsWith("/data/user/")) return false
        val lower = trimmed.lowercase(Locale.US)
        return lower.endsWith(".mp4") ||
            lower.endsWith(".mov") ||
            lower.endsWith(".mkv") ||
            lower.endsWith(".jpg") ||
            lower.endsWith(".jpeg") ||
            lower.endsWith(".png") ||
            lower.endsWith(".webp") ||
            lower.endsWith(".mp3") ||
            lower.endsWith(".wav") ||
            lower.endsWith(".apk")
    }

    /**
     * Sanitizes, GZIP-compresses UTF-8 JSON, and encodes with Base64.NO_WRAP prefixed with "gz:".
     */
    fun compress(json: String): GzipCompressionResult {
        val safeJson = sanitizeTextOnlyJson(json)
        val rawBytes = safeJson.toByteArray(StandardCharsets.UTF_8)

        val baos = ByteArrayOutputStream()
        GZIPOutputStream(baos).use { gzip ->
            gzip.write(rawBytes)
        }
        val gzippedBytes = baos.toByteArray()
        val encoded = PREFIX + Base64.encodeToString(gzippedBytes, Base64.NO_WRAP)
        val encodedSize = encoded.toByteArray(StandardCharsets.UTF_8).size

        require(encodedSize <= MAX_COMPRESSED_BYTES) {
            "Compressed payload ($encodedSize bytes) exceeds 256 KB text-only cloud limit"
        }

        val savedPct = if (rawBytes.isNotEmpty()) {
            (((rawBytes.size - encodedSize).toDouble() / rawBytes.size.toDouble()) * 100.0)
                .toInt()
                .coerceIn(0, 99)
        } else {
            0
        }

        return GzipCompressionResult(
            encodedPayload = encoded,
            rawBytes = rawBytes.size,
            compressedBytes = encodedSize,
            savedPercent = savedPct
        )
    }

    /**
     * Validates "gz:" prefix and inflates GZIP+Base64 text payload with zip-bomb length protection.
     */
    fun decompress(encoded: String): String {
        val trimmed = encoded.trim()
        require(trimmed.startsWith(PREFIX)) {
            "Invalid compressed payload: missing '$PREFIX' header"
        }
        val b64 = trimmed.substring(PREFIX.length)
        val compressedBytes = Base64.decode(b64, Base64.DEFAULT)

        val out = ByteArrayOutputStream()
        GZIPInputStream(ByteArrayInputStream(compressedBytes)).use { gis ->
            val buffer = ByteArray(4096)
            var total = 0
            while (true) {
                val read = gis.read(buffer)
                if (read <= 0) break
                total += read
                if (total > MAX_DECOMPRESSED_BYTES) {
                    throw IllegalStateException("Decompressed payload exceeded 1 MB safety limit")
                }
                out.write(buffer, 0, read)
            }
        }
        return String(out.toByteArray(), StandardCharsets.UTF_8)
    }
}
