package com.investalert.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.OpenableColumns
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File

object TraderAttachmentProcessor {
    suspend fun process(context: Context, uri: Uri): TraderAttachment = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val name = queryName(context, uri)
        val mime = resolver.getType(uri).orEmpty().ifBlank { guessMime(name) }

        when {
            mime.startsWith("image/") -> {
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                try {
                    val image = InputImage.fromFilePath(context, uri)
                    val result = recognizer.process(image).await()
                    TraderAttachment(
                        name = name,
                        mimeType = mime,
                        extractedText = result.text,
                        kind = "Image"
                    )
                } finally {
                    recognizer.close()
                }
            }
            mime == "application/pdf" || name.endsWith(".pdf", true) -> {
                TraderAttachment(
                    name = name,
                    mimeType = "application/pdf",
                    extractedText = readPdfWithOcr(context, uri),
                    kind = "PDF"
                )
            }
            mime.startsWith("text/") ||
                name.endsWith(".csv", true) ||
                name.endsWith(".txt", true) ||
                name.endsWith(".json", true) -> {
                val text = resolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
                TraderAttachment(name, mime.ifBlank { "text/plain" }, text.take(120_000), "Text")
            }
            else -> {
                TraderAttachment(
                    name = name,
                    mimeType = mime.ifBlank { "application/octet-stream" },
                    extractedText = "",
                    kind = "Unsupported"
                )
            }
        }
    }

    private suspend fun readPdfWithOcr(context: Context, uri: Uri): String {
        val resolver = context.contentResolver
        val temp = File.createTempFile("investalert_", ".pdf", context.cacheDir)
        resolver.openInputStream(uri)?.use { input ->
            temp.outputStream().use { output -> input.copyTo(output) }
        } ?: return ""

        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val out = StringBuilder()
        try {
            android.os.ParcelFileDescriptor.open(temp, android.os.ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    val pageCount = minOf(renderer.pageCount, 8)
                    for (i in 0 until pageCount) {
                        renderer.openPage(i).use { page ->
                            val width = page.width.coerceAtMost(1800)
                            val scale = width.toFloat() / page.width.toFloat()
                            val height = (page.height * scale).toInt().coerceAtLeast(1)
                            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            try {
                                val result = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
                                if (result.text.isNotBlank()) {
                                    out.append("\n--- Page ").append(i + 1).append(" ---\n")
                                    out.append(result.text)
                                }
                            } finally {
                                bitmap.recycle()
                            }
                        }
                    }
                }
            }
        } finally {
            recognizer.close()
            temp.delete()
        }
        return out.toString().take(120_000)
    }

    private fun queryName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) return cursor.getString(index)
            }
        }
        return uri.lastPathSegment ?: "attachment"
    }

    private fun guessMime(name: String): String = when {
        name.endsWith(".pdf", true) -> "application/pdf"
        name.endsWith(".csv", true) -> "text/csv"
        name.endsWith(".txt", true) -> "text/plain"
        name.endsWith(".json", true) -> "application/json"
        name.endsWith(".png", true) -> "image/png"
        name.endsWith(".jpg", true) || name.endsWith(".jpeg", true) -> "image/jpeg"
        else -> "application/octet-stream"
    }
}
