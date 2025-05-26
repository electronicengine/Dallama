package com.llama.dallama
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

private const val MAX_CHUNK_SIZE = 24

class PdfTextParser(private val context: Context) {

    init {
        // Required to initialize fonts and resources in Android
        PDFBoxResourceLoader.init(context)
    }

    /**
     * Extracts text from a PDF file selected via URI.
     */
    fun getFileNameFromUri(uri: Uri): String? {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        return cursor?.use {
            val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            it.moveToFirst()
            it.getString(nameIndex)
        }
    }

    suspend fun parseTextFromPdf(uri: Uri): List<String> = withContext(Dispatchers.IO) {
        val inputStream = context.contentResolver.openInputStream(uri)

        inputStream.use { stream ->
            if (stream != null) {
                val document = PDDocument.load(stream)
                val stripper = PDFTextStripper()
                val text = stripper.getText(document)
                document.close()

                val parts = splitTextSmartBySentence(text, MAX_CHUNK_SIZE)
                return@withContext parts
            } else {
                throw IllegalArgumentException("PDF açma başarısız.")
            }
        }
    }

    private fun splitTextSmartBySentence(text: String, maxChunkSize: Int): List<String> {
        val sentences = Regex("(?<=[.!?\\n])\\s+").split(text)  // Cümlelere ayır
        val result = mutableListOf<String>()
        val currentChunk = StringBuilder()

        for (sentence in sentences) {
            if (currentChunk.length + sentence.length + 1 <= maxChunkSize) {
                currentChunk.append(sentence).append(" ")
            } else {
                result.add(currentChunk.toString().trim())
                currentChunk.clear()
                currentChunk.append(sentence).append(" ")
            }
        }

        if (currentChunk.isNotEmpty()) {
            result.add(currentChunk.toString().trim())
        }

        return result
    }
}
