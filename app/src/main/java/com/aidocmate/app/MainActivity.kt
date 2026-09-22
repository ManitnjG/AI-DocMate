package com.aidocmate.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); PDFBoxResourceLoader.init(applicationContext); setContent { MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme(primary = Color(0xFFB7B2FF), secondary = Color(0xFF7DD3FC), tertiary = Color(0xFFFCD34D)) else lightColorScheme(primary = Color(0xFF4F46E5), secondary = Color(0xFF0EA5E9), tertiary = Color(0xFFF59E0B), background = Color(0xFFF8FAFC), surface = Color.White, surfaceVariant = Color(0xFFF1F5F9))) { DocMateApp(this) } } }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun DocMateApp(context: Context) {
    var scannerOpen by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    if (scannerOpen) { com.aidocmate.app.scan.ScannerScreen(context as ComponentActivity) { scannerOpen = false }; return }
    var editorOpen by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    if (editorOpen) { SmartEditor(context) { editorOpen = false }; return }
    val store=remember{DocumentStore(context)}; val prefs=remember{context.getSharedPreferences("settings",Context.MODE_PRIVATE)}; val scope=rememberCoroutineScope()
    var history by remember{mutableStateOf(emptyList<SavedDoc>())}; var doc by remember{mutableStateOf<SavedDoc?>(null)}; var output by remember{mutableStateOf("Import a document to begin.")}; var busy by remember{mutableStateOf(false)}; var question by remember{mutableStateOf("")}; var language by remember{mutableStateOf(prefs.getString("language","English")?:"English")}; var settings by remember{mutableStateOf(false)}; var cloudConsent by remember{mutableStateOf(false)}; var pendingSummary by remember{mutableStateOf(false)}; var rename by remember{mutableStateOf(false)}; var newName by remember{mutableStateOf("")}; var deleteTarget by remember{mutableStateOf<SavedDoc?>(null)}; var search by remember{mutableStateOf("")}; var exportCsv by remember{mutableStateOf(false)}; var selectedDocs by remember{mutableStateOf(setOf<String>())}
    var ocrLanguage by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(prefs.getString("ocrLanguage", "eng") ?: "eng") }
    var ocrDialog by remember { mutableStateOf(false) }
    var ocrStatus by remember { mutableStateOf("") }
    var jobs by remember { mutableStateOf(emptyList<AssistantJob>()) }
    var showConversation by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        history=withContext(Dispatchers.IO){store.list()}
        doc=history.firstOrNull { it.id==prefs.getString("activeDoc",null) }
        doc?.let { output=it.result.ifBlank { "Loaded ${it.pages.size} pages/slides." } }
        withContext(Dispatchers.IO) { AssistantJobs.recover(context) }
        androidx.work.WorkManager.getInstance(context).getWorkInfosByTagFlow("docmate-ai").collect {
            jobs=withContext(Dispatchers.IO) { AssistantJobStore(context).use { db->db.list() } }
            history=withContext(Dispatchers.IO){store.list()}
            doc?.let { current-> history.firstOrNull { it.id==current.id }?.let { fresh->
                if(fresh.result!=current.result && fresh.result.isNotBlank()) output=fresh.result
                doc=fresh
            } }
        }
    }
    LaunchedEffect(doc?.id) { doc?.let { prefs.edit().putString("activeDoc",it.id).apply() } }
    if(showConversation) AlertDialog(onDismissRequest={showConversation=false},title={Text("Conversation history")},text={Column(Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        val entries=jobs.filter { it.kind=="ai" && it.docId==doc?.id }
        if(entries.isEmpty()) Text("Questions and answers will be saved here.")
        entries.forEach { item->
            Text(item.question,fontWeight=FontWeight.Bold)
            Text(item.status,style=MaterialTheme.typography.labelSmall)
            if(item.answer.isNotBlank()) SelectionContainer { Text(ResultFormatter.clean(item.answer)) }
            if(item.status in listOf("queued","running","retrying")) TextButton(onClick={AssistantJobs.cancel(context,item.id)}) { Text("Cancel request") }
            if(item.status in listOf("failed","cancelled")) TextButton(onClick={doc?.let { current->AssistantJobs.enqueue(context,current,item.question,item.language,item.summary) }}) { Text("Retry") }
            HorizontalDivider()
        }
    }},confirmButton={TextButton(onClick={showConversation=false}){Text("Close")}})
    suspend fun saveResult(result:String){output=result;doc?.let{current->val updated=current.copy(result=result);withContext(Dispatchers.IO){store.save(updated)};doc=updated;history=withContext(Dispatchers.IO){store.list()}}}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri!=null) scope.launch {
            try { withContext(Dispatchers.IO) { AssistantJobs.importDocument(context,uri,ocrLanguage) }; output="Import queued. OCR can continue in the background; open the result from Saved documents." }
            catch(e:Exception) { output="Could not queue import: ${e.message}" }
        }
    }
    val exporter=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")){uri->if(uri!=null){val snapshot=if(exportCsv)StructuredExtractor.csv(doc?.pages?.joinToString("\n"){it.text}?:"")else ResultFormatter.clean(output);scope.launch{try{withContext(Dispatchers.IO){context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use{it.write(snapshot)}?:error("Cannot write file")}}catch(e:Exception){output="Export failed: ${e.message}"}}}}
    var exportMessage by remember { mutableStateOf("") }
    var citationPage by remember { mutableStateOf<DocPage?>(null) }
    citationPage?.let { page -> AlertDialog(
        onDismissRequest = { citationPage = null },
        title = { Text("Page / slide ${page.number}") },
        text = { SelectionContainer { Text(page.text, Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) } },
        confirmButton = { TextButton(onClick = { citationPage = null }) { Text("Close") } }
    ) }
    val docxExporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.wordprocessingml.document")) { uri ->
        if (uri != null) {
            val text = ResultFormatter.clean(output); val title = doc?.name ?: "DocMate result"
            scope.launch {
                try { withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { DocxWriter.write(it, title, text) } ?: error("Cannot write destination")
                }; exportMessage = "Word document exported." }
                catch (e: Exception) { exportMessage = "Export failed: ${e.message}" }
            }
        }
    }
    if (ocrDialog) AlertDialog(
        onDismissRequest = { if (!busy) ocrDialog = false },
        title = { Text("Scan text language") },
        text = { Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
            Text("English works with built-in OCR. Other languages need a one-time download; processing then runs on your device.")
            com.aidocmate.app.scan.OcrLanguages.names.forEach { (code, label) ->
                TextButton(enabled = !busy, onClick = {
                    ocrLanguage = code; prefs.edit().putString("ocrLanguage", code).apply()
                    ocrStatus = "Selected $label"
                }) { Text((if (ocrLanguage == code) "✓ " else "") + label) }
            }
            Text(ocrStatus)
        } },
        confirmButton = { TextButton(enabled = !busy, onClick = {
            if (ocrLanguage == "eng") ocrDialog = false else {
                busy = true; ocrStatus = "Downloading language packs…"
                scope.launch {
                    try { withContext(Dispatchers.IO) {
                        com.aidocmate.app.scan.OcrModels(context).download(ocrLanguage, java.util.concurrent.atomic.AtomicBoolean(false)) { }
                    }; ocrStatus = "Language packs ready. You can import scans offline." }
                    catch (e: Exception) { ocrStatus = "Download failed: ${e.message}" }
                    finally { busy = false }
                }
            }
        }) { Text(if (ocrLanguage == "eng") "Done" else "Download packs") } },
        dismissButton = { TextButton(enabled = !busy, onClick = { ocrDialog = false }) { Text("Close") } }
    )
    if(settings)AlertDialog(onDismissRequest={settings=false},title={Text("About & privacy")},text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)){Text(if(AiClient.configured)"AI is provided through DocMate. You do not need your own API key or server." else "Cloud AI is not activated in this build. Offline overview, source search, OCR and extraction are available.");Text("Cloud answers require internet and are subject to daily usage limits. Before each cloud request, you can choose whether to send the document text.");Text("Your saved documents stay on this device. Delete them from history to remove them. Offline tools do not upload your document.")}},confirmButton={TextButton(onClick={settings=false}){Text("Close")}})
    if(cloudConsent)AlertDialog(onDismissRequest={cloudConsent=false},title={Text("Send document text to AI?")},text={Text("Document text and recent conversation will be sent to the DocMate service and its AI provider. The request is saved and can continue in the background when connected.")},confirmButton={TextButton(onClick={
        cloudConsent=false
        doc?.let { current->
            val prompt=if(pendingSummary) "Summarize the key points, dates, amounts and action items." else question
            scope.launch { try {
                withContext(Dispatchers.IO) { AssistantJobs.enqueue(context,current,prompt,language,pendingSummary) }
                output="AI request queued. You can leave this screen; the answer will be saved in conversation history."
            } catch(e:Exception) { output="Could not queue request: ${e.message}" } }
        }
    }){Text("Continue")}},dismissButton={TextButton(onClick={cloudConsent=false}){Text("Cancel")}})
    if(rename)AlertDialog(onDismissRequest={rename=false},title={Text("Rename document")},text={OutlinedTextField(newName,{newName=it.take(160)})},confirmButton={TextButton(onClick={doc?.let{current->scope.launch{try{val updated=current.copy(name=newName.trim().ifBlank{current.name});withContext(Dispatchers.IO){store.save(updated)};doc=updated;history=withContext(Dispatchers.IO){store.list()}}catch(e:Exception){output="Rename failed: ${e.message}"}}};rename=false}){Text("Save")}})
    deleteTarget?.let{target->AlertDialog(onDismissRequest={deleteTarget=null},title={Text("Delete ${target.name}?")},text={Text("Removes saved text and results from this device.")},confirmButton={TextButton(onClick={deleteTarget=null;scope.launch{try{withContext(Dispatchers.IO){AssistantJobStore(context).use{db->db.list(target.id).forEach{AssistantJobs.cancel(context,it.id)};db.deleteDocument(target.id)};store.delete(target)};history=withContext(Dispatchers.IO){store.list()};selectedDocs=selectedDocs-target.id;if(doc?.id==target.id){doc=null;output="Document deleted."}}catch(e:Exception){output="Delete failed: ${e.message}"}}}){Text("Delete")}},dismissButton={TextButton(onClick={deleteTarget=null}){Text("Cancel")}})}
    Scaffold(containerColor=MaterialTheme.colorScheme.background,topBar={TopAppBar(title={Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){Box(Modifier.size(36.dp).background(Brush.linearGradient(listOf(Color(0xFF4F46E5),Color(0xFF0EA5E9),Color(0xFFF59E0B))),RoundedCornerShape(10.dp)),contentAlignment=Alignment.Center){Text("AI",color=Color.White,fontWeight=FontWeight.ExtraBold)};Column{Text("AI DocMate",fontWeight=FontWeight.Bold);Text("Intelligent document companion",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}}},actions={TextButton(onClick={settings=true},enabled=!busy){Text("Privacy")}},colors=TopAppBarDefaults.topAppBarColors(containerColor=MaterialTheme.colorScheme.surface))}){padding->Column(Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(16.dp)){
        if(doc==null){Card(shape=RoundedCornerShape(24.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.72f)),modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){Box(Modifier.size(50.dp).background(Brush.linearGradient(listOf(Color(0xFF4F46E5),Color(0xFF0EA5E9),Color(0xFFF59E0B))),RoundedCornerShape(14.dp)),contentAlignment=Alignment.Center){Text("AI",color=Color.White,fontWeight=FontWeight.ExtraBold)};Column{Text("Your documents, explained",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold);Text("Private-first document intelligence",color=MaterialTheme.colorScheme.onSurfaceVariant)}};Text("Explain, search, summarize, compare and extract data from PDFs, Word documents, PowerPoint presentations, scans and photos.",color=MaterialTheme.colorScheme.onSurfaceVariant);Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("PDF","DOCX","PPTX","OCR","TXT").forEach{label->Surface(shape=RoundedCornerShape(8.dp),color=MaterialTheme.colorScheme.primary.copy(alpha=.10f)){Text(label,Modifier.padding(horizontal=8.dp,vertical=5.dp),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold)}}}}}}
        Button(onClick={scannerOpen=true},enabled=!busy,modifier=Modifier.fillMaxWidth()){Text("Scan Document • saved drafts")}
        OutlinedButton(onClick={editorOpen=true},enabled=!busy,modifier=Modifier.fillMaxWidth()){Text("Smart Editor • PDF & images")}
        if(!AiClient.configured)Text("Cloud AI is awaiting activation. Use the offline tools below.",style=MaterialTheme.typography.bodySmall)
        Button(onClick={picker.launch(arrayOf("application/pdf","image/jpeg","image/png","text/plain","application/vnd.openxmlformats-officedocument.wordprocessingml.document","application/vnd.openxmlformats-officedocument.presentationml.presentation"))},enabled=!busy,modifier=Modifier.fillMaxWidth().height(52.dp),shape=RoundedCornerShape(16.dp)){Text(if(doc==null)"Import Document or Photo" else "Import Another Document",fontWeight=FontWeight.SemiBold)}
        TextButton(onClick = { ocrDialog = true }, enabled = !busy) { Text("OCR language: ${com.aidocmate.app.scan.OcrLanguages.names[ocrLanguage] ?: "English"}") }
        Text("Up to 25 MB / 100 PDF pages. DOCX, PPTX and TXT text extraction works offline. Select the scan language before importing photos or scanned PDFs.",style=MaterialTheme.typography.bodySmall)
        jobs.filter { it.kind=="import" && it.status in listOf("queued","running","failed") }.take(5).forEach { job ->
            Text("Document import: ${job.status}. ${job.answer}",style=MaterialTheme.typography.bodySmall)
            if(job.status!="failed") TextButton(onClick={AssistantJobs.cancel(context,job.id)}) { Text("Cancel import") }
        }
        if(busy){LinearProgressIndicator(Modifier.fillMaxWidth());Text("Processing… Please keep the app open.")}
        doc?.let{current->
            val activeJobs=jobs.filter { it.docId==current.id && it.status in listOf("queued","running","retrying") }
            if(activeJobs.isNotEmpty()) { LinearProgressIndicator(Modifier.fillMaxWidth());Text("${activeJobs.size} saved AI request(s): ${activeJobs.first().status}. You can leave the app.") }
            TextButton(onClick={showConversation=true}){Text("Conversation history")}
            Text(current.name,style=MaterialTheme.typography.titleLarge);val fullText=current.pages.joinToString("\n"){it.text};Text(ResultFormatter.classify(fullText),style=MaterialTheme.typography.labelLarge);Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){ResultFormatter.suggestedQuestions(fullText).take(3).forEach{suggestion->AssistChip(onClick={question=suggestion},label={Text(suggestion,maxLines=1)},enabled=!busy)}};Row(Modifier.horizontalScroll(rememberScrollState())){TextButton(onClick={newName=current.name;rename=true},enabled=!busy){Text("Rename")};TextButton(onClick={scope.launch{val updated=current.copy(favorite=!current.favorite);withContext(Dispatchers.IO){store.save(updated)};doc=updated;history=withContext(Dispatchers.IO){store.list()}}},enabled=!busy){Text(if(current.favorite)"★ Favorite" else "☆ Favorite")};TextButton(onClick={deleteTarget=current},enabled=!busy){Text("Delete")}};Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("English","Tamil").forEach{lang->FilterChip(selected=language==lang,onClick={language=lang;prefs.edit().putString("language",lang).apply()},label={Text(lang)},enabled=!busy)}};OutlinedTextField(question,{question=it.take(2000)},modifier=Modifier.fillMaxWidth(),label={Text("Ask about this document")},enabled=!busy);Button(onClick={pendingSummary=false;cloudConsent=true},enabled=!busy&&question.isNotBlank()&&AiClient.configured,modifier=Modifier.fillMaxWidth()){Text("Ask AI")};OutlinedButton(onClick={pendingSummary=true;cloudConsent=true},enabled=!busy&&AiClient.configured,modifier=Modifier.fillMaxWidth()){Text("AI summary")};OutlinedButton(onClick={scope.launch{try{saveResult(withContext(Dispatchers.Default){LocalAssistant.answer(current.pages,question,false)})}catch(e:Exception){output="Could not search: ${e.message}"}}},enabled=!busy&&question.isNotBlank()){Text("Search source offline")};OutlinedButton(onClick={scope.launch{try{saveResult(withContext(Dispatchers.Default){LocalAssistant.answer(current.pages,"",true)})}catch(e:Exception){output="Could not summarize: ${e.message}"}}},enabled=!busy){Text("Offline overview")};OutlinedButton(onClick={scope.launch{try{val invoice=StructuredExtractor.invoice(fullText).display();val generic=current.pages.joinToString("\n\n"){"Page/slide ${it.number}\n${DocTools.extract(it.text)}"};saveResult(if(invoice.isNotBlank())"Structured invoice / GST details\n\n$invoice\n\n$generic" else generic)}catch(e:Exception){output="Save failed: ${e.message}"}}},enabled=!busy){Text("Extract document fields offline")};OutlinedButton(onClick={output=current.pages.joinToString("\n\n"){"Page/slide ${it.number}\n${it.text}"}},enabled=!busy){Text("View source text")}}
        HorizontalDivider();Card(shape=RoundedCornerShape(18.dp),modifier=Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){Text("Analysis Result",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold);val displayOutput=ResultFormatter.clean(output);SelectionContainer{Text(displayOutput,modifier=Modifier.fillMaxWidth(),style=MaterialTheme.typography.bodyLarge)};doc?.let{current->val citations=ResultFormatter.citations(output,current.pages);if(citations.isNotEmpty()){HorizontalDivider();Text("Sources",fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.primary);citations.forEach{source->Surface(shape=RoundedCornerShape(12.dp),color=MaterialTheme.colorScheme.primary.copy(alpha=.07f),modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){Text("Page / slide ${source.page}",fontWeight=FontWeight.SemiBold,color=MaterialTheme.colorScheme.primary);Text(source.snippet,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant);TextButton(onClick={citationPage=current.pages.firstOrNull{it.number==source.page}}){Text("Read cited page")}}}}}};Row(Modifier.horizontalScroll(rememberScrollState())){TextButton(onClick={(context.getSystemService(Context.CLIPBOARD_SERVICE)as ClipboardManager).setPrimaryClip(ClipData.newPlainText("DocMate",displayOutput))}){Text("Copy")};TextButton(onClick={context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply{type="text/plain";putExtra(Intent.EXTRA_TEXT,displayOutput.take(100000))},"Share result"))}){Text("Share")};TextButton(onClick={exportCsv=false;exporter.launch("DocMate-result.txt")}){Text("TXT")};TextButton(onClick={docxExporter.launch("DocMate-result.docx")}){Text("DOCX")};if(doc!=null)TextButton(onClick={exportCsv=true;exporter.launch("DocMate-invoice.csv")}){Text("CSV")}}}}
        if(exportMessage.isNotBlank()) Text(exportMessage, style=MaterialTheme.typography.bodySmall)
        HorizontalDivider();Text("Saved documents (${history.size})",style=MaterialTheme.typography.titleLarge);OutlinedTextField(search,{search=it},label={Text("Search saved documents")},modifier=Modifier.fillMaxWidth());if(selectedDocs.size>=2){Button(onClick={val chosen=history.filter{it.id in selectedDocs};output=MultiDocAssistant.compare(chosen,question)},enabled=!busy,modifier=Modifier.fillMaxWidth()){Text("Compare ${selectedDocs.size} documents offline")}};history.filter{item->search.isBlank()||item.name.contains(search,true)||item.tags.any{it.contains(search,true)}||item.pages.any{it.text.contains(search,true)}}.sortedByDescending{it.favorite}.forEach{item->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){Checkbox(checked=item.id in selectedDocs,onCheckedChange={checked->selectedDocs=if(checked)selectedDocs+item.id else selectedDocs-item.id},enabled=!busy);OutlinedButton(onClick={doc=item;output=item.result.ifBlank{"Loaded ${item.pages.size} pages/slides."};question=""},enabled=!busy,modifier=Modifier.weight(1f)){Text((if(item.favorite)"★ " else "")+item.name)}}}
    }}
}
