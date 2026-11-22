package com.omniai.studio.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.math.min

data class AttachedDocument(
    val fileName: String,
    val mimeType: String,
    val extractedText: String,
    val pageCount: Int = 1,
    val chunks: List<String> = emptyList()
)

/**
 * 100% offline document reader supporting .txt, .md, .csv, .json, and .pdf files
 * using zero external PDF dependencies.
 *
 * For PDFs, pages are rendered via Android's built-in `android.graphics.pdf.PdfRenderer`
 * onto white-backed Bitmaps and passed through the existing offline ML Kit OCR engine
 * (Latin + Devanagari).
 */
object OfflineDocumentReader {

    private const val MAX_PDF_PAGES = 12
    private const val MAX_RENDER_WIDTH_PX = 1600
    private const val CHUNK_SIZE_CHARS = 2400
    private const val MAX_CONTEXT_CHARS = 7200

    suspend fun readDocument(context: Context, uri: Uri): AttachedDocument =
        withContext(Dispatchers.IO) {
            val fileName = resolveDisplayName(context, uri)
            val mimeType = context.contentResolver.getType(uri).orEmpty()
            val isPdf = mimeType.equals("application/pdf", ignoreCase = true) ||
                fileName.endsWith(".pdf", ignoreCase = true)

            if (isPdf) {
                readPdfWithNativeRendererAndOcr(context, uri, fileName, mimeType.ifBlank { "application/pdf" })
            } else {
                val rawText = context.contentResolver.openInputStream(uri)?.use { stream ->
                    BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).readText()
                }.orEmpty().trim()

                val chunks = chunkText(rawText)
                AttachedDocument(
                    fileName = fileName,
                    mimeType = mimeType.ifBlank { "text/plain" },
                    extractedText = rawText,
                    pageCount = 1,
                    chunks = chunks
                )
            }
        }

    private suspend fun readPdfWithNativeRendererAndOcr(
        context: Context,
        uri: Uri,
        fileName: String,
        mimeType: String
    ): AttachedDocument {
        val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw IllegalStateException("Unable to open PDF file descriptor.")

        val pageTexts = mutableListOf<String>()
        var totalPages = 0

        pfd.use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                totalPages = renderer.pageCount
                val pagesToProcess = min(totalPages, MAX_PDF_PAGES)
                for (index in 0 until pagesToProcess) {
                    renderer.openPage(index).use { page ->
                        val scale = min(2f, MAX_RENDER_WIDTH_PX.toFloat() / page.width.coerceAtLeast(1))
                        val targetWidth = (page.width * scale).toInt().coerceAtLeast(1)
                        val targetHeight = (page.height * scale).toInt().coerceAtLeast(1)
                        val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                        try {
                            val canvas = Canvas(bitmap)
                            canvas.drawColor(Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            val ocrText = OfflineOcrHelper.extractHindiAndEnglishFromBitmap(bitmap).trim()
                            if (ocrText.isNotBlank()) {
                                pageTexts.add("--- Page ${index + 1} ---\n$ocrText")
                            }
                        } finally {
                            bitmap.recycle()
                        }
                    }
                }
            }
        }

        val combined = pageTexts.joinToString("\n\n").trim()
        val chunks = chunkText(combined)
        return AttachedDocument(
            fileName = fileName,
            mimeType = mimeType,
            extractedText = combined,
            pageCount = totalPages.coerceAtLeast(1),
            chunks = chunks
        )
    }

    fun chunkText(text: String, chunkSize: Int = CHUNK_SIZE_CHARS): List<String> {
        val cleaned = text.trim()
        if (cleaned.isEmpty()) return emptyList()
        if (cleaned.length <= chunkSize) return listOf(cleaned)

        val chunks = mutableListOf<String>()
        var start = 0
        while (start < cleaned.length) {
            val hardEnd = min(start + chunkSize, cleaned.length)
            val breakAt = if (hardEnd < cleaned.length) {
                val nl = cleaned.lastIndexOf('\n', hardEnd)
                val sp = cleaned.lastIndexOf(' ', hardEnd)
                when {
                    nl > start + chunkSize / 2 -> nl
                    sp > start + chunkSize / 2 -> sp
                    else -> hardEnd
                }
            } else {
                hardEnd
            }
            chunks.add(cleaned.substring(start, breakAt).trim())
            start = if (breakAt <= start) hardEnd else breakAt
        }
        return chunks.filter { it.isNotBlank() }
    }

    fun buildContextSnippet(doc: AttachedDocument): String {
        val body = doc.chunks.take(3).joinToString("\n\n").take(MAX_CONTEXT_CHARS)
        val truncatedNote = if (doc.extractedText.length > body.length) {
            "\n[Document truncated cleanly to fit context window; ${doc.chunks.size} total chunks]"
        } else {
            ""
        }
        return buildString {
            appendLine("[ATTACHED DOCUMENT: ${doc.fileName} (${doc.pageCount} page(s))]")
            appendLine(body.ifBlank { "(No readable text extracted from document)" })
            if (truncatedNote.isNotEmpty()) appendLine(truncatedNote)
            append("[/ATTACHED DOCUMENT]")
        }
    }

    private fun resolveDisplayName(context: Context, uri: Uri): String {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) cursor.getString(idx) else null
                    } else null
                } ?: uri.lastPathSegment ?: "document.txt"
        } catch (_: Exception) {
            uri.lastPathSegment ?: "document.txt"
        }
    }
}
