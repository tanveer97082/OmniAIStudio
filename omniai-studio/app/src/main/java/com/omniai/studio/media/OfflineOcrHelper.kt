package com.omniai.studio.media

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 100% offline ML Kit OCR supporting both Hindi (Devanagari) and English (Latin) text extraction
 * from images picked via `ActivityResultContracts.PickVisualMedia` or Bitmaps rendered by `PdfRenderer`.
 */
object OfflineOcrHelper {

    suspend fun extractHindiAndEnglishText(context: Context, imageUri: Uri): String =
        withContext(Dispatchers.Default) {
            val inputImage = InputImage.fromFilePath(context, imageUri)
            recognizeInputImage(inputImage)
        }

    suspend fun extractHindiAndEnglishFromBitmap(bitmap: Bitmap): String =
        withContext(Dispatchers.Default) {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            recognizeInputImage(inputImage)
        }

    private suspend fun recognizeInputImage(inputImage: InputImage): String {
        // Devanagari recognizer natively reads both Hindi (Devanagari) and Latin scripts.
        val devanagariRecognizer =
            TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
        val latinRecognizer =
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        return try {
            val devanagariText = recognizeWith(devanagariRecognizer, inputImage).trim()
            val containsDevanagari = devanagariText.any { ch -> ch in '\u0900'..'\u097F' }
            if (containsDevanagari) {
                devanagariText
            } else {
                val latinText = recognizeWith(latinRecognizer, inputImage).trim()
                if (latinText.length >= devanagariText.length) latinText else devanagariText
            }
        } finally {
            devanagariRecognizer.close()
            latinRecognizer.close()
        }
    }

    private suspend fun recognizeWith(recognizer: TextRecognizer, image: InputImage): String =
        suspendCancellableCoroutine { continuation ->
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    if (continuation.isActive) {
                        continuation.resume(visionText.text.orEmpty())
                    }
                }
                .addOnFailureListener { ex ->
                    if (continuation.isActive) {
                        continuation.resumeWithException(ex)
                    }
                }
        }
}
