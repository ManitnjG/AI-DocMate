package com.aidocmate.app.scan

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

class ScanViewModel(application: Application) : AndroidViewModel(application) {
    val store = ScanDraftStore(application)
    var draft by mutableStateOf(ScanDraft()); private set
    var busy by mutableStateOf(true); private set
    var message by mutableStateOf("Loading saved scan…"); private set
    var modelsReady by mutableStateOf(false); private set
    private val cancelled = AtomicBoolean(false)
    init { runTask { draft = withContext(Dispatchers.IO) { store.load() }; refreshModels(); message = if (draft.pages.isEmpty()) "Start a scan or import photos." else "Restored ${draft.pages.size} saved pages." } }
    private suspend fun refreshModels() { modelsReady = withContext(Dispatchers.IO) { OcrModels(getApplication()).ready(draft.language) } }
    private fun runTask(action: suspend () -> Unit) {
        busy = true; cancelled.set(false)
        viewModelScope.launch { try { action() } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { message = e.message ?: "Scan operation failed; your saved draft is unchanged." } finally { busy = false } }
    }
    fun status(value: String) { message = value }
    fun import(uris: List<Uri>, replacement: String?) { if (busy) return; runTask {
        message = "Saving scan pages…"
        draft = withContext(Dispatchers.IO) { store.importPages(uris, replacement) }
        message = "Draft saved: ${draft.pages.size} pages. Quality warnings are advisory; inspect the preview."
    } }
    fun update(next: ScanDraft) { if (busy) return; runTask {
        withContext(Dispatchers.IO) { store.save(next) }; val changed = draft.language != next.language; draft = next
        if (changed) refreshModels()
        message = "Draft saved on this device."
    } }
    fun download() { if (busy) return; runTask {
        withContext(Dispatchers.IO) { OcrModels(getApplication()).download(draft.language, cancelled) { value -> viewModelScope.launch { message = value } } }
        refreshModels(); message = "OCR packs ready for offline use."
    } }
    fun cancel() { cancelled.set(true); message = "Stopping after the current page or download chunk…" }
    fun export(uri: Uri) { if (busy) return; val snapshot = draft; runTask {
        val result = withContext(Dispatchers.IO) {
            ScanPdfExporter(getApplication()).export(snapshot, store, cancelled) { value -> viewModelScope.launch { message = value } }
        }
        try {
            withContext(Dispatchers.IO) {
                getApplication<Application>().contentResolver.openOutputStream(uri)?.use { out -> result.first.inputStream().use { it.copyTo(out) } } ?: error("Cannot write PDF destination")
            }
            message = "PDF exported (${String.format(java.util.Locale.US, "%.1f", result.first.length() / 1048576.0)} MB). " + result.second.joinToString("; ")
        } finally { withContext(Dispatchers.IO) { result.first.parentFile?.deleteRecursively() } }
    } }
}
