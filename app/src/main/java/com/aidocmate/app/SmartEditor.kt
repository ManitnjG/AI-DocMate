package com.aidocmate.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
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
    var scannerOpen by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    if (scannerOpen) { com.aidocmate.app.scan.ScannerScreen(context as androidx.activity.ComponentActivity) { scannerOpen = false }; return }
    val scope = rememberCoroutineScope()
    val fileSaver = Saver<File?, String>(save = { it?.path ?: "" }, restore = { path -> path.takeIf { it.isNotEmpty() }?.let(::File) })
    var source by rememberSaveable(stateSaver = fileSaver) { mutableStateOf<File?>(null) }
    val editsSaver = listSaver<PageEdits?, String>(save = { it?.snapshot() ?: emptyList() }, restore = { if (it.isEmpty()) null else PageEdits.restore(it) })
    var edits by rememberSaveable(stateSaver = editsSaver) { mutableStateOf<PageEdits?>(null) }
    var revision by remember { mutableIntStateOf(0) }
    var selected by rememberSaveable { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Open a PDF or image. Export saves a separate PDF; the original stays unchanged.") }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var panX by remember { mutableFloatStateOf(0f) }; var panY by remember { mutableFloatStateOf(0f) }
    var confirmClose by remember { mutableStateOf(false) }
    fun requestClose() { if (source == null) onBack() else confirmClose = true }
    BackHandler { if (!busy) requestClose() }
    if (confirmClose) AlertDialog(
        onDismissRequest = { confirmClose = false },
        title = { Text("Close editor?") },
        text = { Text("Export your PDF before closing. Unsaved changes will be discarded; original files stay unchanged.") },
        confirmButton = { TextButton(onClick = { source?.delete(); source = null; edits = null; onBack() }) { Text("Close editor") } },
        dismissButton = { TextButton(onClick = { confirmClose = false }) { Text("Keep editing") } }
    )
    LaunchedEffect(Unit) {
        if (source != null && source?.isFile != true) {
            source = null; edits = null; selected = 0
            message = "The temporary source is no longer available. Please open the original again."
        }
    }
    var exportOnlyPage by rememberSaveable { mutableStateOf(false) }
    var cameraFile by rememberSaveable(stateSaver = fileSaver) { mutableStateOf<File?>(null) }
    fun openSource(uri: Uri) {
        busy = true
        scope.launch {
            var loaded: File? = null
            try {
                loaded = withContext(Dispatchers.IO) { loadEditorSource(context, uri) }
                val count = withContext(Dispatchers.IO) { PDDocument.load(loaded).use { it.numberOfPages } }
                source?.delete(); source = loaded; loaded = null
                edits = PageEdits(count); selected = 0; revision++
                message = "Offline • pinch to zoom and drag to pan"
            } catch (e: Exception) { message = "Open failed: ${e.message}" }
            finally { loaded?.delete(); cameraFile?.delete(); cameraFile = null; busy = false }
        }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val file = cameraFile
        if (ok && file != null) openSource(Uri.fromFile(file))
        else { file?.delete(); cameraFile = null; message = "Capture cancelled." }
    }
    fun launchCamera() {
        try {
            val dir = File(context.cacheDir, "camera").apply { mkdirs() }
            val file = File.createTempFile("capture", ".jpg", dir); cameraFile = file
            camera.launch(androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file))
        } catch (e: Exception) { cameraFile?.delete(); cameraFile = null; message = "Camera unavailable: ${e.message}" }
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        if (allowed) launchCamera() else message = "Camera permission denied. You can still import a photo."
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) openSource(uri)
    }

    val merger = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            busy = true
            scope.launch {
                val inputs = mutableListOf<File>()
                var merged: File? = null
                try {
                    require(uris.size <= 20) { "Select at most 20 documents" }
                    val result = withContext(Dispatchers.IO) {
                        val output = File.createTempFile("editor-merged", ".pdf", context.cacheDir)
                        merged = output
                        PDDocument().use { target ->
                            val utility = com.tom_roush.pdfbox.multipdf.PDFMergerUtility()
                            var totalBytes = 0L
                            for (uri in uris) {
                                val input = loadEditorSource(context, uri); inputs.add(input)
                                totalBytes += input.length()
                                require(totalBytes <= 100L * 1024 * 1024) { "Combined files exceed 100 MB" }
                                PDDocument.load(input).use { document ->
                                    require(target.numberOfPages + document.numberOfPages <= 100) { "Combined document exceeds 100 pages" }
                                    utility.appendDocument(target, document)
                                }
                            }
                            target.save(output)
                            output to target.numberOfPages
                        }
                    }
                    source?.delete(); source = result.first; merged = null
                    edits = PageEdits(result.second); selected = 0; revision++
                    message = "Combined ${uris.size} documents. Review page order, then export PDF."
                } catch (e: Exception) { message = "Merge failed: ${e.message}" }
                finally { inputs.forEach { it.delete() }; merged?.delete(); busy = false }
            }
        }
    }
    var exportRange by rememberSaveable { mutableStateOf("") }
    var rangeDialog by rememberSaveable { mutableStateOf(false) }
    var rangeError by remember { mutableStateOf<String?>(null) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val file = source; val pages = edits?.pages?.let { if (exportRange.isNotBlank()) PageRanges.parse(exportRange, it.size).map { index -> it[index] } else if (exportOnlyPage) listOf(it[selected]) else it.toList() }
        if (uri != null && file != null && pages != null) { busy = true; scope.launch {
            try { withContext(Dispatchers.IO) {
                PDDocument.load(file).use { original -> PDDocument().use { result ->
                    pages.forEach { spec -> val imported = if (spec.source < 0) PDPage(PDRectangle.A4).also { result.addPage(it) } else result.importPage(original.getPage(spec.source)).apply { resources = original.getPage(spec.source).resources; cropBox = original.getPage(spec.source).cropBox; mediaBox = original.getPage(spec.source).mediaBox }; imported.rotation = (imported.rotation + spec.rotation) % 360 }
                    context.contentResolver.openOutputStream(uri)?.use { result.save(it) } ?: error("Cannot write destination")
                } }
            }; message = "PDF exported successfully." } catch (e: Exception) { message = "Export failed: ${e.message}. Choose a new destination and retry." } finally { busy = false }
        } }
    }
    if (rangeDialog) AlertDialog(
        onDismissRequest = { rangeDialog = false },
        title = { Text("Export page range") },
        text = { Column {
            OutlinedTextField(exportRange, { exportRange = it.take(1000); rangeError = null }, label = { Text("Pages, e.g. 1-3, 5") }, isError = rangeError != null)
            rangeError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = { TextButton(onClick = {
            try { PageRanges.parse(exportRange, edits?.pages?.size ?: 0); rangeDialog = false; exportOnlyPage = false; exporter.launch("DocMate-pages.pdf") }
            catch (e: IllegalArgumentException) { rangeError = e.message }
        }) { Text("Export") } },
        dismissButton = { TextButton(onClick = { rangeDialog = false }) { Text("Cancel") } }
    )
    LaunchedEffect(source, selected, revision) {
        val file = source ?: return@LaunchedEffect
        val pageSpec = edits?.pages?.getOrNull(selected) ?: return@LaunchedEffect
        zoom = 1f; panX = 0f; panY = 0f
        try { preview = withContext(Dispatchers.IO) {
            if (pageSpec.source < 0) Bitmap.createBitmap(990, 1400, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.WHITE) } else ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd -> PdfRenderer(fd).use { renderer -> renderer.openPage(pageSpec.source).use { page ->
                val scale = 1400f / maxOf(page.width, page.height)
                Bitmap.createBitmap((page.width * scale).toInt().coerceAtLeast(1), (page.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888).also { bitmap -> bitmap.eraseColor(android.graphics.Color.WHITE); page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY) }
            } } }
        } } catch (e: Exception) { preview = null; message = "Preview failed: ${e.message}" }
    }
    fun edit(action: (PageEdits) -> Unit) { try { edits?.let(action); selected = selected.coerceAtMost((edits?.pages?.size ?: 1) - 1); revision++ } catch (e: Exception) { message = e.message ?: "Cannot edit page" } }
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row { TextButton(onClick = { requestClose() }, enabled = !busy) { Text("Back") }; Text("Smart Editor", style = MaterialTheme.typography.headlineSmall) }
        Row { Button(onClick = { picker.launch(arrayOf("application/pdf", "image/jpeg", "image/png")) }, enabled = !busy && source == null) { Text("Open") }
            TextButton(onClick = { exportRange = ""; exportOnlyPage = false; exporter.launch("DocMate-edited.pdf") }, enabled = source != null && !busy) { Text("Export PDF") } }
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            TextButton(onClick = { scannerOpen = true }, enabled = !busy && source == null) { Text("Document scanner") }
            TextButton(onClick = { cameraPermission.launch(android.Manifest.permission.CAMERA) }, enabled = !busy && source == null) { Text("Capture photo") }
            TextButton(onClick = { merger.launch(arrayOf("application/pdf", "image/jpeg", "image/png")) }, enabled = !busy && source == null) { Text("Merge PDFs / photos") }
        }
        Text(message, style = MaterialTheme.typography.bodySmall)
        if (source != null) Text("Edits and undo history survive device rotation. Export before closing.", style = MaterialTheme.typography.labelSmall)
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        Box(Modifier.weight(1f).fillMaxWidth().clipToBounds().pointerInput(source, selected) { detectTransformGestures { _, pan, scale, _ -> zoom = (zoom * scale).coerceIn(1f, 5f); panX = (panX + pan.x).coerceIn(-2000f, 2000f); panY = (panY + pan.y).coerceIn(-2000f, 2000f) } }) {
            preview?.let { bitmap -> Image(bitmap.asImageBitmap(), "Page ${selected + 1}", Modifier.fillMaxSize().graphicsLayer { scaleX = zoom; scaleY = zoom; translationX = panX; translationY = panY; rotationZ = (edits?.pages?.getOrNull(selected)?.rotation ?: 0).toFloat() }) }
        }
        val model = edits
        if (model != null) {
            Row { TextButton(onClick = { selected-- }, enabled = selected > 0 && !busy) { Text("Previous") }; Text("${selected + 1} / ${model.pages.size}"); TextButton(onClick = { selected++ }, enabled = selected < model.pages.lastIndex && !busy) { Text("Next") } }
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                TextButton(onClick = { edit { it.undo() } }, enabled = model.canUndo && !busy) { Text("Undo") }
                TextButton(onClick = { edit { it.redo() } }, enabled = model.canRedo && !busy) { Text("Redo") }
                TextButton(onClick = { exportRange = ""; exportOnlyPage = true; exporter.launch("DocMate-page-${selected + 1}.pdf") }, enabled = !busy) { Text("Extract page") }
                TextButton(onClick = { exportRange = ""; rangeError = null; rangeDialog = true }, enabled = !busy) { Text("Export range") }
                TextButton(onClick = { edit { it.insertBlank(selected) } }, enabled = !busy && model.pages.size < 100) { Text("Blank page") }
                TextButton(onClick = { edit { it.rotate(selected) } }, enabled = !busy) { Text("Rotate") }
                TextButton(onClick = { edit { it.duplicate(selected) } }, enabled = !busy && model.pages.size < 100) { Text("Duplicate") }
                TextButton(onClick = { edit { it.delete(selected) } }, enabled = !busy && model.pages.size > 1) { Text("Delete") }
                TextButton(onClick = { edit { it.move(selected, selected - 1) }; if (selected > 0) selected-- }, enabled = !busy && selected > 0) { Text("Move earlier") }
                TextButton(onClick = { edit { it.move(selected, selected + 1) }; if (selected < model.pages.lastIndex) selected++ }, enabled = !busy && selected < model.pages.lastIndex) { Text("Move later") }
            }
        }
    }
}
