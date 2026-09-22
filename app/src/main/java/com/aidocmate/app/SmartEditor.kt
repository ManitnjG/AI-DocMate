package com.aidocmate.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private fun loadEditorSource(context: Context, uri: Uri): File {
    val raw = File.createTempFile("editor-input", ".bin", context.cacheDir)
    val pdfFile = File.createTempFile("editor-source", ".pdf", context.cacheDir)
    try {
        context.contentResolver.openInputStream(uri)?.use { input -> raw.outputStream().use { out ->
            val buffer = ByteArray(8192); var total = 0
            while (true) { val n = input.read(buffer); if (n < 0) break; total += n
                require(total <= 25 * 1024 * 1024) { "Maximum file size is 25 MB" }; out.write(buffer, 0, n) }
        } } ?: error("Cannot read file")
        val header = ByteArray(5); raw.inputStream().use { it.read(header) }
        if (String(header, Charsets.US_ASCII) == "%PDF-") {
            PDDocument.load(raw).use { require(it.numberOfPages in 1..100) { "Choose 1–100 pages" }; require(!it.isEncrypted) { "Unlock the PDF before editing" } }
            raw.copyTo(pdfFile, overwrite = true)
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }; BitmapFactory.decodeFile(raw.path, bounds)
            require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Choose PDF, JPG or PNG" }
            var sample = 1; while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2400) sample *= 2
            val bitmap = BitmapFactory.decodeFile(raw.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: error("Cannot decode image")
            try { PDDocument().use { pdf ->
                val page = PDPage(PDRectangle(bitmap.width.toFloat(), bitmap.height.toFloat())); pdf.addPage(page)
                PDPageContentStream(pdf, page).use { it.drawImage(LosslessFactory.createFromImage(pdf, bitmap), 0f, 0f, page.mediaBox.width, page.mediaBox.height) }; pdf.save(pdfFile)
            } } finally { bitmap.recycle() }
        }
        return pdfFile
    } catch (e: Exception) { pdfFile.delete(); throw e } finally { raw.delete() }
}

@Composable fun SmartEditor(context: Context, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var source by remember { mutableStateOf<File?>(null) }
    var edits by remember { mutableStateOf<PageEdits?>(null) }
    var revision by remember { mutableIntStateOf(0) }
    var selected by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Open a PDF or image. Export saves a separate PDF; the original stays unchanged.") }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var panX by remember { mutableFloatStateOf(0f) }; var panY by remember { mutableFloatStateOf(0f) }
    DisposableEffect(Unit) { onDispose { source?.delete() } }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { busy = true; scope.launch {
            try { val file = withContext(Dispatchers.IO) { loadEditorSource(context, uri) }
                val count = withContext(Dispatchers.IO) { PDDocument.load(file).use { it.numberOfPages } }
                source?.delete(); source = file; edits = PageEdits(count); selected = 0; revision++; message = "Offline • pinch to zoom and drag to pan"
            } catch (e: Exception) { message = "Open failed: ${e.message}" } finally { busy = false }
        } }
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val file = source; val pages = edits?.pages?.toList()
        if (uri != null && file != null && pages != null) { busy = true; scope.launch {
            try { withContext(Dispatchers.IO) {
                PDDocument.load(file).use { original -> PDDocument().use { result ->
                    pages.forEach { spec -> val imported = result.importPage(original.getPage(spec.source)); imported.rotation = (imported.rotation + spec.rotation) % 360 }
                    context.contentResolver.openOutputStream(uri)?.use { result.save(it) } ?: error("Cannot write destination")
                } }
            }; message = "PDF exported successfully." } catch (e: Exception) { message = "Export failed: ${e.message}. Choose a new destination and retry." } finally { busy = false }
        } }
    }
    LaunchedEffect(source, selected, revision) {
        val file = source ?: return@LaunchedEffect
        val pageSpec = edits?.pages?.getOrNull(selected) ?: return@LaunchedEffect
        zoom = 1f; panX = 0f; panY = 0f
        try { preview = withContext(Dispatchers.IO) {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd -> PdfRenderer(fd).use { renderer -> renderer.openPage(pageSpec.source).use { page ->
                val scale = 1400f / maxOf(page.width, page.height)
                Bitmap.createBitmap((page.width * scale).toInt().coerceAtLeast(1), (page.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888).also { bitmap -> bitmap.eraseColor(android.graphics.Color.WHITE); page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY) }
            } } }
        } } catch (e: Exception) { preview = null; message = "Preview failed: ${e.message}" }
    }
    fun edit(action: (PageEdits) -> Unit) { try { edits?.let(action); selected = selected.coerceAtMost((edits?.pages?.size ?: 1) - 1); revision++ } catch (e: Exception) { message = e.message ?: "Cannot edit page" } }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row { TextButton(onClick = onBack, enabled = !busy) { Text("Back") }; Text("Smart Editor", style = MaterialTheme.typography.headlineSmall) }
        Row { Button(onClick = { picker.launch(arrayOf("application/pdf", "image/jpeg", "image/png")) }, enabled = !busy) { Text("Open") }
            TextButton(onClick = { exporter.launch("DocMate-edited.pdf") }, enabled = source != null && !busy) { Text("Export PDF") } }
        Text(message, style = MaterialTheme.typography.bodySmall)
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        Box(Modifier.weight(1f).fillMaxWidth().pointerInput(source, selected) { detectTransformGestures { _, pan, scale, _ -> zoom = (zoom * scale).coerceIn(1f, 5f); panX += pan.x; panY += pan.y } }) {
            preview?.let { bitmap -> Image(bitmap.asImageBitmap(), "Page ${selected + 1}", Modifier.fillMaxSize().graphicsLayer { scaleX = zoom; scaleY = zoom; translationX = panX; translationY = panY; rotationZ = (edits?.pages?.getOrNull(selected)?.rotation ?: 0).toFloat() }) }
        }
        val model = edits
        if (model != null) {
            Row { TextButton(onClick = { selected-- }, enabled = selected > 0 && !busy) { Text("Previous") }; Text("${selected + 1} / ${model.pages.size}"); TextButton(onClick = { selected++ }, enabled = selected < model.pages.lastIndex && !busy) { Text("Next") } }
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                TextButton(onClick = { edit { it.undo() } }, enabled = model.canUndo && !busy) { Text("Undo") }
                TextButton(onClick = { edit { it.redo() } }, enabled = model.canRedo && !busy) { Text("Redo") }
                TextButton(onClick = { edit { it.rotate(selected) } }, enabled = !busy) { Text("Rotate") }
                TextButton(onClick = { edit { it.duplicate(selected) } }, enabled = !busy && model.pages.size < 100) { Text("Duplicate") }
                TextButton(onClick = { edit { it.delete(selected) } }, enabled = !busy && model.pages.size > 1) { Text("Delete") }
                TextButton(onClick = { edit { it.move(selected, selected - 1) }; if (selected > 0) selected-- }, enabled = !busy && selected > 0) { Text("Move earlier") }
                TextButton(onClick = { edit { it.move(selected, selected + 1) }; if (selected < model.pages.lastIndex) selected++ }, enabled = !busy && selected < model.pages.lastIndex) { Text("Move later") }
            }
        }
    }
}
