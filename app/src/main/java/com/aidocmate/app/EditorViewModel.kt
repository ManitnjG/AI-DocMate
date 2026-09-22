package com.aidocmate.app

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.*
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class EditorViewModel(app: Application): AndroidViewModel(app) {
    val store=EditorDraftStore(app)
    var draft by mutableStateOf<EditorDraft?>(null); private set
    var busy by mutableStateOf(true); private set
    var message by mutableStateOf("Restoring saved editor draft…"); private set
    var revision by mutableIntStateOf(0); private set
    var ocr by mutableStateOf<List<EditorOcrLine>>(emptyList()); private set
    private var pending: List<Uri>?=null
    init { task { draft=withContext(Dispatchers.IO) { store.load() }; message=if(draft==null) "Open a PDF or image." else "Saved draft restored." } }
    private fun task(block:suspend ()->Unit) {
        busy=true
        viewModelScope.launch {
            try { block() } catch(e:CancellationException) { throw e } catch(e:Exception) { message=e.message ?: "Operation failed" }
            finally { busy=false; pending?.let { pending=null; open(it) } }
        }
    }
    fun status(text:String) { message=text }
    fun open(uris:List<Uri>) {
        if(busy) { pending=uris; return }; if(uris.isEmpty()) return
        task {
            message="Importing editor source…"
            val next=withContext(Dispatchers.IO) {
                require(uris.size<=20) { "Select at most 20 files" }
                val inputs=mutableListOf<File>(); val merged=File.createTempFile("editor-merge",".pdf",getApplication<Application>().cacheDir)
                try {
                    PDDocument().use { output ->
                        val merger=com.tom_roush.pdfbox.multipdf.PDFMergerUtility(); var bytes=0L
                        uris.forEach { uri ->
                            val file=loadEditorSource(getApplication(),uri); inputs.add(file); bytes+=file.length()
                            require(bytes<=100L*1024*1024) { "Combined files exceed 100 MB" }
                            PDDocument.load(file).use { pdf -> require(output.numberOfPages+pdf.numberOfPages<=100) { "Maximum 100 pages" }; merger.appendDocument(output,pdf) }
                        }
                        output.save(merged); store.adopt(merged,output.numberOfPages)
                    }
                } finally { inputs.forEach { it.delete() }; merged.delete() }
            }
            draft=next; ocr=emptyList(); revision++; message="Draft saved on this device."
        }
    }
    fun change(action:(PageEdits)->Unit) { if(busy) return; val current=draft ?: return
        task {
            val next=withContext(Dispatchers.IO) {
                val model=PageEdits.restore(current.edits.snapshot()); action(model)
                current.copy(edits=model,selected=current.selected.coerceAtMost(model.pages.lastIndex)).also(store::save)
            }
            draft=next; ocr=emptyList(); revision++; message="All changes saved."
        }
    }
    fun select(index:Int) { if(busy) return; val current=draft ?: return; if(index !in current.edits.pages.indices) return
        task { val next=current.copy(selected=index); withContext(Dispatchers.IO) { store.save(next) }; draft=next; ocr=emptyList() }
    }
    fun discard() { if(busy) return; task { withContext(Dispatchers.IO) { store.clear() }; draft=null; ocr=emptyList(); revision++; message="Draft removed. Open another document." } }
    fun recognize(language:String) { if(busy) return; val current=draft ?: return
        task {
            message="Finding text regions…"
            ocr=withContext(Dispatchers.IO) {
                val bitmap=EditorRenderer.render(current.source,current.edits.pages[current.selected],2200)
                try { EditorOcr.lines(getApplication(),bitmap,language) } finally { bitmap.recycle() }
            }
            message="${ocr.size} text regions. Tap a box to edit. Font and background are estimated; review the preview."
        }
    }
    fun download(language:String) { if(busy) return; task {
        message="Downloading OCR packs…"
        withContext(Dispatchers.IO) { com.aidocmate.app.scan.OcrModels(getApplication()).download(language,AtomicBoolean(false)) {} }
        message="OCR packs ready for offline use."
    } }
    fun export(uri:Uri, indices:List<Int>) { if(busy) return; val current=draft ?: return
        task {
            message="Exporting edited PDF…"
            withContext(Dispatchers.IO) {
                val output=File.createTempFile("editor-export",".pdf",getApplication<Application>().cacheDir)
                try {
                    EditorRenderer.export(current.source,indices.map { current.edits.pages[it] },output)
                    getApplication<Application>().contentResolver.openOutputStream(uri)?.use { out -> output.inputStream().use { it.copyTo(out) } } ?: error("Cannot write destination")
                } finally { output.delete() }
            }
            message="PDF exported. Your editable draft is still saved."
        }
    }
}
