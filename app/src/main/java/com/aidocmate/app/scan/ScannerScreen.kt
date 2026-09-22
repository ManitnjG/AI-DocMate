package com.aidocmate.app.scan

import android.app.Activity
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import com.google.mlkit.vision.documentscanner.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable private fun ScanThumbnail(vm: ScanViewModel, page: ScanPage, modifier: Modifier) {
    var bitmap by remember(page) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(page) { bitmap = withContext(Dispatchers.IO) { runCatching { ScanImages.render(vm.store.image(page), 180, page.mode, page.rotation) }.getOrNull() } }
    bitmap?.let { Image(it.asImageBitmap(), "Scan page thumbnail", modifier) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ScannerScreen(activity: ComponentActivity, onBack: () -> Unit) {
    val vm = remember(activity) { ViewModelProvider(activity)[ScanViewModel::class.java] }
    var selected by rememberSaveable { mutableIntStateOf(0) }
    var replacement by rememberSaveable { mutableStateOf<String?>(null) }
    var starting by remember { mutableStateOf(false) }
    var languageMenu by remember { mutableStateOf(false) }
    var clearConfirm by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    val draft = vm.draft
    val index = selected.coerceIn(0, (draft.pages.size - 1).coerceAtLeast(0))
    val page = draft.pages.getOrNull(index)
    BackHandler { onBack() } // ViewModel work and persisted draft survive leaving this screen.
    val scanLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        starting = false
        if (result.resultCode == Activity.RESULT_OK) {
            val images = GmsDocumentScanningResult.fromActivityResultIntent(result.data)?.pages?.map { it.imageUri }.orEmpty()
            if (images.isEmpty()) vm.status("No scan pages were returned. Your previous draft is safe.") else vm.import(images, replacement)
        } else vm.status("Scan cancelled. Your saved draft is unchanged.")
        replacement = null
    }
    fun scan(retake: String? = null) {
        if (vm.busy || starting) return
        replacement = retake; starting = true
        val limit = if (retake != null) 1 else 30 - draft.pages.size
        if (limit <= 0) { starting = false; vm.status("Maximum 30 pages. Export or remove pages first."); return }
        val options = GmsDocumentScannerOptions.Builder().setGalleryImportAllowed(true).setPageLimit(limit)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG).setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL).build()
        vm.status("Opening scanner. First use may download scanner components through Google Play services.")
        GmsDocumentScanning.getClient(options).getStartScanIntent(activity)
            .addOnSuccessListener(activity) { sender ->
                try { scanLauncher.launch(IntentSenderRequest.Builder(sender).build()) }
                catch (e: Exception) { starting = false; vm.status("Cannot open scanner: ${e.message}") }
            }
            .addOnFailureListener(activity) { e -> starting = false; vm.status("Scanner unavailable: ${e.message}. Update Google Play services or import photos below. Imported photos do not receive automatic cropping.") }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> if (uris.isNotEmpty()) vm.import(uris, null) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri -> if (uri != null) vm.export(uri) }
    LaunchedEffect(page) {
        preview = null
        if (page != null) {
            try { preview = withContext(Dispatchers.IO) { ScanImages.render(vm.store.image(page), 1400, page.mode, page.rotation) } }
            catch (e: Exception) { vm.status("Preview unavailable: ${e.message}") }
        }
    }
    if (clearConfirm) AlertDialog(onDismissRequest = { clearConfirm = false }, title = { Text("Delete this scan draft?") },
        text = { Text("Removes the saved draft pages from this device. Exported PDFs are kept.") },
        confirmButton = { TextButton(onClick = { clearConfirm = false; selected = 0; vm.update(draft.copy(pages = emptyList())) }) { Text("Delete draft") } },
        dismissButton = { TextButton(onClick = { clearConfirm = false }) { Text("Keep draft") } })
    Scaffold(topBar = { TopAppBar(title = { Text("Document Scanner") }, navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }) }) { padding ->
        LazyColumn(Modifier.padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("Auto-capture • crop & straighten • multi-page", style = MaterialTheme.typography.titleSmall)
                Text("Use Auto capture, adjust the corners, then choose filters in the scanner. Tap Save to return pages to this draft. Returned pages and edits are saved automatically on this device.", style = MaterialTheme.typography.bodySmall)
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    Button(onClick = { scan() }, enabled = !vm.busy && !starting && draft.pages.size < 30) { Text(if (page == null) "Scan document" else "Add scan pages") }
                    TextButton(onClick = { importer.launch(arrayOf("image/jpeg", "image/png")) }, enabled = !vm.busy && !starting && draft.pages.size < 30) { Text("Import photos") }
                }
            }
            item { Text(vm.message, style = MaterialTheme.typography.bodySmall); if (vm.busy || starting) LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (draft.pages.isNotEmpty()) {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { itemsIndexed(draft.pages, key = { _, p -> p.id }) { i, p ->
                        OutlinedCard(Modifier.width(84.dp).clickable(enabled = !vm.busy) { selected = i }) {
                            ScanThumbnail(vm, p, Modifier.fillMaxWidth().height(90.dp))
                            Text("${i + 1}${if (i == index) " • selected" else ""}", style = MaterialTheme.typography.labelSmall)
                        }
                    } }
                }
                item { preview?.let { Image(it.asImageBitmap(), "Preview page ${index + 1}", Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 420.dp)) } }
                item {
                    Text("Page ${index + 1} of ${draft.pages.size}", style = MaterialTheme.typography.titleMedium)
                    page?.warnings?.forEach { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    Text("Warnings are estimates, not a guarantee. Check text and corners visually.", style = MaterialTheme.typography.labelSmall)
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        TextButton(onClick = { page?.let { scan(it.id) } }, enabled = !vm.busy && !starting) { Text("Retake / crop") }
                        TextButton(onClick = { vm.update(draft.copy(pages = draft.pages.mapIndexed { i, p -> if (i == index) p.copy(rotation = (p.rotation + 90) % 360) else p })) }, enabled = !vm.busy) { Text("Rotate") }
                        TextButton(onClick = { vm.update(draft.copy(pages = draft.pages.toMutableList().apply { add(index - 1, removeAt(index)) })); selected = index - 1 }, enabled = !vm.busy && index > 0) { Text("Earlier") }
                        TextButton(onClick = { vm.update(draft.copy(pages = draft.pages.toMutableList().apply { add(index + 1, removeAt(index)) })); selected = index + 1 }, enabled = !vm.busy && index < draft.pages.lastIndex) { Text("Later") }
                        TextButton(onClick = { vm.update(draft.copy(pages = draft.pages.filterIndexed { i, _ -> i != index })); selected = (index - 1).coerceAtLeast(0) }, enabled = !vm.busy) { Text("Delete page") }
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ScanMode.entries.forEach { mode -> FilterChip(selected = page?.mode == mode, onClick = { vm.update(draft.copy(pages = draft.pages.mapIndexed { i, p -> if (i == index) p.copy(mode = mode) else p })) }, enabled = !vm.busy, label = { Text(when (mode) { ScanMode.COLOUR -> "Colour"; ScanMode.GRAYSCALE -> "Grayscale"; ScanMode.ENHANCED -> "Remove shadows"; ScanMode.BLACK_WHITE -> "B&W" }) }) }
                    }
                }
                item {
                    HorizontalDivider(); Text("PDF quality", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { ExportQuality.entries.forEach { quality -> FilterChip(selected = draft.quality == quality, onClick = { vm.update(draft.copy(quality = quality)) }, enabled = !vm.busy, label = { Text(quality.label) }) } }
                    Text("Small reduces file size; High retains more detail for small text.", style = MaterialTheme.typography.bodySmall)
                    Row { Checkbox(checked = draft.searchable, onCheckedChange = { vm.update(draft.copy(searchable = it)) }, enabled = !vm.busy); Text("Searchable PDF (offline OCR)") }
                    if (draft.searchable) {
                        Box {
                            OutlinedButton(onClick = { languageMenu = true }, enabled = !vm.busy) { Text(OcrLanguages.names[draft.language] ?: "Choose OCR language") }
                            DropdownMenu(expanded = languageMenu, onDismissRequest = { languageMenu = false }) { OcrLanguages.names.forEach { (code, name) -> DropdownMenuItem(text = { Text(name) }, onClick = { languageMenu = false; vm.update(draft.copy(language = code)) }) } }
                        }
                        Text("Download language packs once, then OCR runs on-device. Scans are not uploaded. Recognition can contain mistakes; review important text.", style = MaterialTheme.typography.bodySmall)
                        if (!vm.modelsReady) OutlinedButton(onClick = { vm.download() }, enabled = !vm.busy) { Text("Download selected OCR packs") }
                        else Text("OCR packs installed • ready offline", style = MaterialTheme.typography.labelMedium)
                    }
                    Button(onClick = { exporter.launch("DocMate-scan.pdf") }, enabled = !vm.busy && (!draft.searchable || vm.modelsReady), modifier = Modifier.fillMaxWidth()) { Text("Export PDF") }
                    if (vm.busy) TextButton(onClick = { vm.cancel() }) { Text("Cancel processing") }
                    TextButton(onClick = { clearConfirm = true }, enabled = !vm.busy) { Text("Delete saved draft") }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
