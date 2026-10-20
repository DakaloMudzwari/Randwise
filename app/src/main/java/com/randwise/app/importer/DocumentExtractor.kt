package com.randwise.app.importer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File

enum class ExtractionMethod { CSV, PDF_TEXT, PDF_OCR, PLAIN_TEXT }

/** [rawCsv] is kept (in memory only) so the user can remap columns without re-reading the file. */
data class ExtractionResult(val fileName: String, val method: ExtractionMethod, val statement: ParsedStatement, val rawCsv: String? = null)

/**
 * F03 Bank Statement Parser front-end. Reads CSV directly, extracts text-based PDFs with
 * PDFBox and falls back to on-device ML Kit OCR for scanned PDFs. Nothing leaves the device.
 */
class DocumentExtractor(private val context: Context) {

    suspend fun extract(uri: Uri, password: String? = null): ExtractionResult = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val name = displayName(uri)
        val mime = resolver.getType(uri).orEmpty()
        val isPdf = mime == "application/pdf" || name.endsWith(".pdf", ignoreCase = true)
        if (!isPdf) {
            val text = resolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                ?: error("Could not open file")
            val isCsv = name.endsWith(".csv", true) || mime.contains("csv") || mime.contains("comma")
            return@withContext if (isCsv || text.lines().take(5).any { it.count { c -> c == ',' || c == ';' } >= 2 }) {
                ExtractionResult(name, ExtractionMethod.CSV, CsvStatementParser.parse(text), rawCsv = text)
            } else {
                ExtractionResult(name, ExtractionMethod.PLAIN_TEXT, TextStatementParser.parse(text))
            }
        }

        // Copy to a private cache file: PdfRenderer needs a seekable descriptor.
        val tmp = File.createTempFile("stmt", ".pdf", context.cacheDir)
        try {
            resolver.openInputStream(uri)?.use { input -> tmp.outputStream().use { input.copyTo(it) } }
                ?: error("Could not open file")
            val text = extractPdfText(tmp, password)
            val textStatement = TextStatementParser.parse(text)
            if (textStatement.lines.size >= 3 || text.length > 400 && textStatement.lines.isNotEmpty()) {
                ExtractionResult(name, ExtractionMethod.PDF_TEXT, textStatement)
            } else {
                val ocrText = ocrPdf(tmp)
                ExtractionResult(name, ExtractionMethod.PDF_OCR, TextStatementParser.parse(ocrText))
            }
        } finally {
            tmp.delete()
        }
    }

    private fun extractPdfText(file: File, password: String?): String {
        PDFBoxResourceLoader.init(context.applicationContext)
        return runCatching {
            val doc = if (password.isNullOrEmpty()) PDDocument.load(file) else PDDocument.load(file, password)
            doc.use { d ->
                PDFTextStripper().apply { sortByPosition = true }.getText(d)
            }
        }.getOrElse { e ->
            if (e.javaClass.simpleName.contains("InvalidPassword")) throw PasswordRequiredException()
            ""
        }
    }

    /** Renders each page at ~2x and runs Latin text recognition, rebuilding table rows by y-position. */
    private suspend fun ocrPdf(file: File): String {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val sb = StringBuilder()
        try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    for (i in 0 until minOf(renderer.pageCount, 30)) {
                        renderer.openPage(i).use { page ->
                            val bmp = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                            bmp.eraseColor(Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            val result = recognizer.process(InputImage.fromBitmap(bmp, 0)).await()
                            sb.append(rebuildRows(result)).append('\n')
                            bmp.recycle()
                        }
                    }
                }
            }
        } catch (e: SecurityException) {
            throw PasswordRequiredException()
        } finally {
            recognizer.close()
        }
        return sb.toString()
    }

    private fun rebuildRows(text: com.google.mlkit.vision.text.Text): String {
        data class Piece(val top: Int, val bottom: Int, val left: Int, val text: String)
        val pieces = text.textBlocks.flatMap { it.lines }.mapNotNull { l ->
            val b = l.boundingBox ?: return@mapNotNull null
            Piece(b.top, b.bottom, b.left, l.text)
        }.sortedBy { it.top }
        val rows = mutableListOf<MutableList<Piece>>()
        for (p in pieces) {
            val centre = (p.top + p.bottom) / 2
            val row = rows.lastOrNull()?.takeIf { r ->
                val ref = r.first()
                centre in ref.top..ref.bottom
            }
            if (row != null) row += p else rows += mutableListOf(p)
        }
        return rows.joinToString("\n") { r -> r.sortedBy { it.left }.joinToString("  ") { it.text } }
    }

    private fun displayName(uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) return c.getString(0) ?: "statement"
        }
        return uri.lastPathSegment ?: "statement"
    }
}

class PasswordRequiredException : Exception("This PDF is password protected. Enter the statement password (often your ID number).")
