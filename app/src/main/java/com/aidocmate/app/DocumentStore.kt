package com.aidocmate.app

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.OpenableColumns
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.zip.ZipFile

data class DocPage(val number: Int, val text: String)
data class SavedDoc(val id: String, val name: String, val pages: List<DocPage>, val result: String = "", val favorite: Boolean = false, val tags: List<String> = emptyList())

class DocumentStore(private val context: Context) {
    private val dir = File(context.filesDir, "documents").apply { mkdirs() }

    fun list(): List<SavedDoc> = dir.listFiles()?.filter { it.extension == "json" }?.sortedByDescending { it.lastModified() }?.mapNotNull { runCatching {
        val o = JSONObject(it.readText()); val a = o.getJSONArray("pages")
        SavedDoc(o.getString("id"), o.getString("name"), (0 until a.length()).map { i -> a.getJSONObject(i).let { p -> DocPage(p.getInt("number"), p.getString("text")) } }, o.optString("result"), o.optBoolean("favorite", false), o.optJSONArray("tags")?.let { tags -> (0 until tags.length()).map { i -> tags.getString(i) } } ?: emptyList())
    }.getOrNull() } ?: emptyList()

    fun save(d: SavedDoc) {
        val o = JSONObject().put("id", d.id).put("name", d.name).put("result", d.result).put("favorite", d.favorite).put("tags", JSONArray(d.tags)).put("pages", pagesJson(d.pages))
        val temp = File(dir, d.id + ".tmp"); temp.writeText(o.toString())
        check(temp.renameTo(File(dir, d.id + ".json"))) { "Could not save document" }
    }
    fun delete(d: SavedDoc) { check(File(dir, d.id + ".json").delete()) { "Could not delete document" } }

    private fun xmlText(xml: String): String = xml
        .replace(Regex("</(?:w:p|a:p)>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("<(?:w:tab|a:tab)[^>]*/>", RegexOption.IGNORE_CASE), "\t")
        .replace(Regex("<[^>]+>"), "")
        .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&apos;", "'")
        .trim()

    private fun officePages(file: File, extension: String): List<DocPage> = ZipFile(file).use { zip ->
        when (extension) {
            "docx" -> {
                val entry = zip.getEntry("word/document.xml") ?: error("Invalid DOCX document")
                listOf(DocPage(1, xmlText(zip.getInputStream(entry).bufferedReader().use { it.readText() })))
            }
            "pptx" -> {
                val slideRegex = Regex("ppt/slides/slide(\\d+)\\.xml")
                zip.entries().asSequence().mapNotNull { entry ->
                    val match = slideRegex.matchEntire(entry.name) ?: return@mapNotNull null
                    val number = match.groupValues[1].toInt()
                    number to xmlText(zip.getInputStream(entry).bufferedReader().use { it.readText() })
                }.sortedBy { it.first }.map { DocPage(it.first, it.second) }.toList().also { require(it.isNotEmpty()) { "Invalid PPTX presentation" } }
            }
            else -> error("Unsupported Office document")
        }
    }

    suspend fun import(uri: Uri): SavedDoc = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: "Document"
        val extension = name.substringAfterLast('.', "").lowercase()
        val tmp = File.createTempFile("import", if (extension.isBlank()) ".bin" else ".$extension", context.cacheDir)
        try {
            resolver.openInputStream(uri)?.use { input -> tmp.outputStream().use { out ->
                val buffer = ByteArray(8192); var total = 0
                while (true) { val n = input.read(buffer); if (n < 0) break; total += n
                    require(total <= 25 * 1024 * 1024) { "Choose a file smaller than 25 MB" }; out.write(buffer, 0, n) }
            } } ?: error("Cannot open this file")
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val pages = try {
                when {
                    extension == "txt" -> listOf(DocPage(1, tmp.readText()))
                    extension == "docx" || extension == "pptx" -> officePages(tmp, extension)
                    resolver.getType(uri) == "application/pdf" || extension == "pdf" -> {
                        val extracted = PDDocument.load(tmp).use { pdf ->
                            require(pdf.numberOfPages <= 100) { "Choose a PDF with at most 100 pages" }
                            (1..pdf.numberOfPages).map { n -> val stripper = PDFTextStripper(); stripper.startPage = n; stripper.endPage = n; DocPage(n, stripper.getText(pdf)) }
                        }
                        if (extracted.any { it.text.isBlank() }) {
                            android.os.ParcelFileDescriptor.open(tmp, android.os.ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                                PdfRenderer(fd).use { renderer -> extracted.map { p ->
                                    if (p.text.isNotBlank()) p else renderer.openPage(p.number - 1).use { page ->
                                        val scale = minOf(2f, 1800f / maxOf(page.width, page.height))
                                        val bitmap = android.graphics.Bitmap.createBitmap((page.width * scale).toInt().coerceAtLeast(1), (page.height * scale).toInt().coerceAtLeast(1), android.graphics.Bitmap.Config.ARGB_8888)
                                        try { bitmap.eraseColor(android.graphics.Color.WHITE); page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY); DocPage(p.number, recognizer.process(InputImage.fromBitmap(bitmap, 0)).await().text) } finally { bitmap.recycle() }
                                    }
                                } }
                            }
                        } else extracted
                    }
                    else -> {
                        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        android.graphics.BitmapFactory.decodeFile(tmp.path, bounds)
                        var sample = 1
                        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2400) sample *= 2
                        val bitmap = android.graphics.BitmapFactory.decodeFile(tmp.path, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }) ?: error("Unsupported file. Choose PDF, DOCX, PPTX, TXT, JPG or PNG.")
                        try { listOf(DocPage(1, recognizer.process(InputImage.fromBitmap(bitmap, 0)).await().text)) } finally { bitmap.recycle() }
                    }
                }
            } finally { recognizer.close() }
            require(pages.any { it.text.isNotBlank() }) { "No text detected. Built-in scan OCR currently supports Latin text." }
            require(pages.sumOf { it.text.length } <= 1_000_000) { "Document text is too large" }
            SavedDoc(UUID.randomUUID().toString(), name, pages).also { save(it) }
        } finally { tmp.delete() }
    }
}
fun pagesJson(pages: List<DocPage>) = JSONArray().apply { pages.forEach { put(JSONObject().put("number", it.number).put("text", it.text)) } }
