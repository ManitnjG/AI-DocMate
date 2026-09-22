package com.aidocmate.app.scan

import android.content.Context
import com.googlecode.tesseract.android.TessBaseAPI
import com.googlecode.tesseract.android.TessPdfRenderer
import com.googlecode.leptonica.android.ReadFile
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory

object OcrLanguages {
    val names = linkedMapOf("eng" to "English", "tam" to "Tamil + English", "hin" to "Hindi + English", "ben" to "Bengali + English", "tel" to "Telugu + English", "mar" to "Marathi + English", "guj" to "Gujarati + English", "kan" to "Kannada + English", "mal" to "Malayalam + English", "pan" to "Punjabi + English", "ori" to "Odia + English", "asm" to "Assamese + English", "urd" to "Urdu + English", "san" to "Sanskrit + English")
    fun codes(language: String): List<String> { require(language in names); return listOf(language, "eng").distinct() }
}
class OcrModels(private val context: Context) {
    val root = File(context.filesDir, "ocr-models").apply { mkdirs() }
    private val dir = File(root, "tessdata").apply { mkdirs() }
    private val manifest by lazy { JSONObject(context.assets.open("ocr-models.json").bufferedReader().use { it.readText() }) }
    private val client = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).callTimeout(180, TimeUnit.SECONDS).build()
    private fun spec(code: String) = manifest.getJSONObject("models").getJSONObject(code)
    private fun valid(code: String): Boolean {
        val file = File(dir, "$code.traineddata")
        if (!file.isFile || file.length() != spec(code).getLong("size")) return false
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input -> val b = ByteArray(8192); while (true) { val n = input.read(b); if (n < 0) break; digest.update(b, 0, n) } }
        return digest.digest().joinToString("") { "%02x".format(it) } == spec(code).getString("sha256")
    }
    fun ready(language: String) = OcrLanguages.codes(language).all { valid(it) }
    fun download(language: String, cancelled: AtomicBoolean, progress: (String) -> Unit) {
        for (code in OcrLanguages.codes(language)) {
            if (cancelled.get()) error("Download cancelled")
            if (valid(code)) continue
            progress("Downloading ${OcrLanguages.names[code]} OCR pack…")
            val tmp = File(dir, "$code.part")
            try {
                val url = "https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/${manifest.getString("revision")}/$code.traineddata"
                client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    check(response.isSuccessful) { "OCR download failed (${response.code})" }
                    val body = response.body ?: error("Empty download")
                    body.byteStream().use { input -> tmp.outputStream().use { output ->
                        val buffer = ByteArray(8192); var size = 0L
                        while (true) { if (cancelled.get()) error("Download cancelled"); val n = input.read(buffer); if (n < 0) break
                            size += n; require(size <= spec(code).getLong("size")) { "Unexpected model size" }; output.write(buffer, 0, n) }
                    } }
                }
                val digest = MessageDigest.getInstance("SHA-256").digest(tmp.readBytes()).joinToString("") { "%02x".format(it) }
                check(tmp.length() == spec(code).getLong("size") && digest == spec(code).getString("sha256")) { "OCR model checksum mismatch; retry download" }
                check(tmp.renameTo(File(dir, "$code.traineddata"))) { "Cannot install OCR model" }
            } finally { tmp.delete() }
        }
    }
}

/** Creates the complete PDF in private cache before touching the user's export destination. */
class ScanPdfExporter(private val context: Context) {
    fun export(draft: ScanDraft, store: ScanDraftStore, cancelled: AtomicBoolean, progress: (String) -> Unit): Pair<File, List<String>> {
        require(draft.pages.isNotEmpty()) { "Scan at least one page" }
        val work = File(context.cacheDir, "scan-export-${java.util.UUID.randomUUID()}").apply { mkdirs() }
        val output = File(work, "scan.pdf"); val warnings = mutableListOf<String>()
        try {
            if (draft.searchable) {
                val models = OcrModels(context)
                check(models.ready(draft.language)) { "Download the selected OCR packs first" }
                val api = TessBaseAPI(); var renderer: TessPdfRenderer? = null
                try {
                    check(api.init(models.root.path, OcrLanguages.codes(draft.language).joinToString("+"), TessBaseAPI.OEM_LSTM_ONLY)) { "Cannot start OCR engine" }
                    api.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO)
                    renderer = TessPdfRenderer(api, File(work, "scan").path)
                    check(api.beginDocument(renderer)) { "Cannot start searchable PDF" }
                    draft.pages.forEachIndexed { index, page ->
                        if (cancelled.get()) error("Export cancelled")
                        progress("OCR page ${index + 1} / ${draft.pages.size}…")
                        val bitmap = ScanImages.render(store.image(page), draft.quality.edge, page.mode, page.rotation)
                        val jpg = File(work, "page.jpg")
                        try {
                            jpg.outputStream().use { check(bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, draft.quality.jpeg, it)) }
                            val pix = ReadFile.readBitmap(bitmap) ?: error("Cannot prepare OCR page")
                            try {
                                check(api.addPageToDocument(pix, jpg.path, renderer)) { "OCR failed on page ${index + 1}" }
                                if (api.getUTF8Text().isNullOrBlank()) warnings.add("Page ${index + 1}: no text recognised")
                                else if (api.meanConfidence() < 55) warnings.add("Page ${index + 1}: low OCR confidence; review text")
                            } finally { pix.recycle() }
                        } finally { bitmap.recycle(); jpg.delete() }
                    }
                    check(api.endDocument(renderer)) { "Could not finish searchable PDF" }
                } finally { renderer?.recycle(); api.recycle() }
            } else {
                PDDocument().use { pdf ->
                    draft.pages.forEachIndexed { index, page ->
                        if (cancelled.get()) error("Export cancelled")
                        progress("Export page ${index + 1} / ${draft.pages.size}…")
                        val bitmap = ScanImages.render(store.image(page), draft.quality.edge, page.mode, page.rotation)
                        try {
                            val width = 595f; val height = width * bitmap.height / bitmap.width
                            val p = PDPage(PDRectangle(width, height)); pdf.addPage(p)
                            PDPageContentStream(pdf, p).use { it.drawImage(JPEGFactory.createFromImage(pdf, bitmap, draft.quality.jpeg / 100f), 0f, 0f, width, height) }
                        } finally { bitmap.recycle() }
                    }
                    pdf.save(output)
                }
            }
            if (cancelled.get()) error("Export cancelled")
            check(output.isFile && output.length() > 0) { "No PDF was generated" }
            return output to warnings
        } catch (e: Throwable) { work.deleteRecursively(); throw e }
    }
}
