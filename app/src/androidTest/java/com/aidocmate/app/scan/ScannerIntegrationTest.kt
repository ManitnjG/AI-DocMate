package com.aidocmate.app.scan

import android.content.Context
import android.graphics.*
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(AndroidJUnit4::class)
class ScannerIntegrationTest {
    private lateinit var context: Context
    @Before fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        PDFBoxResourceLoader.init(context)
        File(context.filesDir, "scan-draft").deleteRecursively()
        val modelDir = File(context.filesDir, "ocr-models/tessdata").apply { mkdirs() }
        for (code in listOf("eng", "tam")) {
            InstrumentationRegistry.getInstrumentation().context.assets.open("$code.traineddata").use { input -> File(modelDir, "$code.traineddata").outputStream().use { input.copyTo(it) } }
        }
    }
    private fun sample(tamil: Boolean = false): File {
        val bitmap = Bitmap.createBitmap(1600, 2200, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap); canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 90f; typeface = Typeface.create("sans-serif", Typeface.NORMAL); textLocale = if (tamil) java.util.Locale("ta") else java.util.Locale.US }
        repeat(5) { i -> canvas.drawText(if (tamil) "தமிழ் ஆவணம்" else "INVOICE NUMBER 12345", 120f, 300f + i * 250, paint) }
        return File.createTempFile("scan-fixture", ".jpg", context.cacheDir).also { f -> f.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 96, it) }; bitmap.recycle() }
    }
    @Test fun draftRestoresPagesEditsAndSettingsFromDisk() {
        val image = sample(); val store = ScanDraftStore(context)
        val draft = store.importPages(listOf(Uri.fromFile(image), Uri.fromFile(image)))
        store.save(draft.copy(pages = draft.pages.reversed().map { it.copy(rotation = 90, mode = ScanMode.GRAYSCALE) }, language = "tam", quality = ExportQuality.HIGH, searchable = true))
        val restored = ScanDraftStore(context).load()
        assertEquals(draft.pages.last().id, restored.pages.first().id)
        assertEquals(90, restored.pages.first().rotation); assertEquals("tam", restored.language); assertTrue(restored.searchable)
        assertTrue(store.image(restored.pages.first()).isFile)
        image.delete()
    }
    @Test fun searchableEnglishPdfContainsRecognisedText() { verifySearchable(false) }
    @Test fun searchableTamilPdfContainsTamilUnicode() { verifySearchable(true) }
    private fun verifySearchable(tamil: Boolean) {
        val image = sample(tamil); val store = ScanDraftStore(context)
        val draft = store.importPages(listOf(Uri.fromFile(image))).copy(language = if (tamil) "tam" else "eng", searchable = true, quality = ExportQuality.HIGH)
        val (pdf, _) = ScanPdfExporter(context).export(draft, store, AtomicBoolean(false)) {}
        try {
            PDDocument.load(pdf).use { document ->
                assertEquals(1, document.numberOfPages)
                val text = PDFTextStripper().getText(document)
                if (tamil) assertTrue("Tamil Unicode not found in searchable layer: $text", text.any { it in '\u0B80'..'\u0BFF' })
                else assertTrue("English search text missing: $text", text.contains("INVOICE", true))
            }
        } finally { pdf.parentFile?.deleteRecursively(); image.delete() }
    }
    @Test fun smallExportIsSmallerAndRetakePreservesPagePosition() {
        val image = sample(); val store = ScanDraftStore(context)
        val original = store.importPages(listOf(Uri.fromFile(image), Uri.fromFile(image)))
        val replaced = store.importPages(listOf(Uri.fromFile(image)), original.pages.first().id)
        assertNotEquals(original.pages.first().id, replaced.pages.first().id)
        assertEquals(original.pages.last().id, replaced.pages.last().id)
        val small = ScanPdfExporter(context).export(replaced.copy(quality = ExportQuality.SMALL), store, AtomicBoolean(false)) {}.first
        val high = ScanPdfExporter(context).export(replaced.copy(quality = ExportQuality.HIGH), store, AtomicBoolean(false)) {}.first
        try { assertTrue(small.length() < high.length()); PDDocument.load(small).use { assertEquals(2, it.numberOfPages) } }
        finally { small.parentFile?.deleteRecursively(); high.parentFile?.deleteRecursively(); image.delete() }
    }
    @Test fun scanResultDuringDraftLoadingIsNotLost() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val image = sample()
        val models = androidx.lifecycle.ViewModelStore()
        lateinit var vm: ScanViewModel
        instrumentation.runOnMainSync {
            vm = androidx.lifecycle.ViewModelProvider(models, androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory(context.applicationContext as android.app.Application))[ScanViewModel::class.java]
            vm.import(listOf(Uri.fromFile(image)), null)
        }
        var complete = false
        for (attempt in 0 until 100) {
            instrumentation.runOnMainSync { complete = !vm.busy && vm.draft.pages.size == 1 }
            if (complete) break
            Thread.sleep(100)
        }
        try { assertTrue("A scan returned during restore was lost", complete) }
        finally { instrumentation.runOnMainSync { models.clear() }; image.delete() }
    }
    @Test fun cancelledExportLeavesDraftIntact() {
        val image = sample(); val store = ScanDraftStore(context); val draft = store.importPages(listOf(Uri.fromFile(image)))
        try { ScanPdfExporter(context).export(draft, store, AtomicBoolean(true)) {}; fail("Expected cancellation") } catch (_: IllegalStateException) { }
        assertEquals(draft.pages.first().id, ScanDraftStore(context).load().pages.first().id)
        assertTrue(store.image(draft.pages.first()).isFile); image.delete()
    }
}
