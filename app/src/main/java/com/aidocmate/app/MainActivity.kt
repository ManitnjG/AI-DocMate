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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocMateApp(context: Context) {
    val store = remember { DocumentStore(context) }
    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    var history by remember { mutableStateOf(emptyList<SavedDoc>()) }
    var doc by remember { mutableStateOf<SavedDoc?>(null) }
    var output by remember { mutableStateOf("Import a PDF or image to begin.") }
    var busy by remember { mutableStateOf(false) }
    var question by remember { mutableStateOf("") }
    var language by remember { mutableStateOf(prefs.getString("language", "English") ?: "English") }
    var settings by remember { mutableStateOf(false) }
    var cloudConsent by remember { mutableStateOf(false) }
    var pendingSummary by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<SavedDoc?>(null) }
    var search by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { history = withContext(Dispatchers.IO) { store.list() } }
    suspend fun saveResult(result: String) {
        output = result
        doc?.let { current ->
            val updated = current.copy(result = result)
            withContext(Dispatchers.IO) { store.save(updated) }
            doc = updated
            history = withContext(Dispatchers.IO) { store.list() }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && !busy) {
            doc = null; output = "Reading document…"; busy = true
            scope.launch {
                try { doc = store.import(uri); output = "Loaded ${doc!!.pages.size} pages. Ask a question or view the source text."; history = withContext(Dispatchers.IO) { store.list() } }
                catch (e: Exception) { output = "Import failed: ${e.message}" }
                finally { busy = false }
            }
        }
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) { val snapshot = ResultFormatter.clean(output); scope.launch {
            try { withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(snapshot) } ?: error("Cannot write file") } }
            catch (e: Exception) { output = "Export failed: ${e.message}" }
        } }
    }
    if (settings) AlertDialog(onDismissRequest = { settings = false }, title = { Text("About & privacy") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (AiClient.configured) "AI is provided through DocMate. You do not need your own API key or server." else "Cloud AI is not activated in this build. Offline overview, source search, OCR and extraction are available.")
            Text("Cloud answers require internet and are subject to daily usage limits. Before each cloud request, you can choose whether to send the document text.")
            Text("Your saved documents stay on this device. Delete them from history to remove them. Offline tools do not upload your document.")
        }
    }, confirmButton = { TextButton(onClick = { settings = false }) { Text("Close") } })
    if (cloudConsent) AlertDialog(onDismissRequest = { cloudConsent = false }, title = { Text("Send document text to AI?") }, text = {
        Text("Extracted text will be sent to the DocMate service and its AI provider, OpenRouter. Only continue if you are comfortable sharing this document. Source excerpts remain available offline.")
    }, confirmButton = { TextButton(onClick = {
        cloudConsent = false
        val current = doc
        if (current != null) { busy = true; scope.launch {
            try { saveResult(AiClient.ask(context, current.pages, if (pendingSummary) "Summarize the key points, dates, amounts and action items." else question, language, pendingSummary)) }
            catch (e: Exception) { output = "Could not complete request: ${e.message}. Your document is saved; you can retry." }
            finally { busy = false }
        } }
    }) { Text("Continue") } }, dismissButton = { TextButton(onClick = { cloudConsent = false }) { Text("Cancel") } })
    if (rename) AlertDialog(onDismissRequest = { rename = false }, title = { Text("Rename document") }, text = { OutlinedTextField(newName, { newName = it.take(160) }) }, confirmButton = {
        TextButton(onClick = { doc?.let { current -> scope.launch { try { val updated = current.copy(name = newName.trim().ifBlank { current.name }); withContext(Dispatchers.IO) { store.save(updated) }; doc = updated; history = withContext(Dispatchers.IO) { store.list() } } catch(e: Exception) { output = "Rename failed: ${e.message}" } } }; rename = false }) { Text("Save") }
    })
    deleteTarget?.let { target -> AlertDialog(onDismissRequest = { deleteTarget = null }, title = { Text("Delete ${target.name}?") }, text = { Text("Removes saved text and results from this device.") }, confirmButton = {
        TextButton(onClick = { deleteTarget = null; scope.launch { try { withContext(Dispatchers.IO) { store.delete(target) }; history = withContext(Dispatchers.IO) { store.list() }; if (doc?.id == target.id) { doc = null; output = "Document deleted." } } catch(e: Exception) { output = "Delete failed: ${e.message}" } } }) { Text("Delete") }
    }, dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }) }
    Scaffold(topBar = { TopAppBar(title = { Text("AI DocMate") }, actions = { TextButton(onClick = { settings = true }, enabled = !busy) { Text("About") } }) }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Your documents, explained", style = MaterialTheme.typography.headlineSmall)
            Text("PDF and image import • private local history • answers with sources")
            if (!AiClient.configured) Text("Cloud AI is awaiting activation. Use the offline tools below.", style = MaterialTheme.typography.bodySmall)
            Button(onClick = { picker.launch(arrayOf("application/pdf", "image/jpeg", "image/png")) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Import PDF or photo") }
            Text("Up to 25 MB / 100 PDF pages. Scan recognition: Latin text. Selectable Tamil PDF text can be used with cloud AI.", style = MaterialTheme.typography.bodySmall)
            if (busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Processing… Please keep the app open.") }
            doc?.let { current ->
                Text(current.name, style = MaterialTheme.typography.titleLarge)
                val fullText = current.pages.joinToString("\n") { it.text }
                Text(ResultFormatter.classify(fullText), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ResultFormatter.suggestedQuestions(fullText).take(3).forEach { suggestion ->
                        AssistChip(onClick = { question = suggestion }, label = { Text(suggestion, maxLines = 1) }, enabled = !busy)
                    }
                }
                Row { TextButton(onClick = { newName = current.name; rename = true }, enabled = !busy) { Text("Rename") }
                    TextButton(onClick = { scope.launch { val updated = current.copy(favorite = !current.favorite); withContext(Dispatchers.IO) { store.save(updated) }; doc = updated; history = withContext(Dispatchers.IO) { store.list() } } }, enabled = !busy) { Text(if (current.favorite) "★ Favorite" else "☆ Favorite") }
                    TextButton(onClick = { deleteTarget = current }, enabled = !busy) { Text("Delete") } }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("English", "Tamil").forEach { lang -> FilterChip(selected = language == lang, onClick = { language = lang; prefs.edit().putString("language", lang).apply() }, label = { Text(lang) }, enabled = !busy) }
                }
                OutlinedTextField(question, { question = it.take(2000) }, modifier = Modifier.fillMaxWidth(), label = { Text("Ask about this document") }, enabled = !busy)
                Button(onClick = { pendingSummary = false; cloudConsent = true }, enabled = !busy && question.isNotBlank() && AiClient.configured, modifier = Modifier.fillMaxWidth()) { Text("Ask AI") }
                OutlinedButton(onClick = { pendingSummary = true; cloudConsent = true }, enabled = !busy && AiClient.configured, modifier = Modifier.fillMaxWidth()) { Text("AI summary") }
                OutlinedButton(onClick = { scope.launch { try { saveResult(withContext(Dispatchers.Default) { LocalAssistant.answer(current.pages, question, false) }) } catch(e: Exception) { output = "Could not search: ${e.message}" } } }, enabled = !busy && question.isNotBlank()) { Text("Search source offline") }
                OutlinedButton(onClick = { scope.launch { try { saveResult(withContext(Dispatchers.Default) { LocalAssistant.answer(current.pages, "", true) }) } catch(e: Exception) { output = "Could not summarize: ${e.message}" } } }, enabled = !busy) { Text("Offline overview") }
                OutlinedButton(onClick = { scope.launch { try { saveResult(current.pages.joinToString("\n\n") { "Page ${it.number}\n${DocTools.extract(it.text)}" }) } catch(e: Exception) { output = "Save failed: ${e.message}" } } }, enabled = !busy) { Text("Extract dates, amounts & phones offline") }
                OutlinedButton(onClick = { output = current.pages.joinToString("\n\n") { "Page ${it.number}\n${it.text}" } }, enabled = !busy) { Text("View source text") }
            }
            HorizontalDivider()
            Text("Result", style = MaterialTheme.typography.titleMedium)
            val displayOutput = ResultFormatter.clean(output)\n            SelectionContainer { Text(displayOutput, modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyLarge) }
            Row {
                TextButton(onClick = { (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("DocMate", displayOutput)) }) { Text("Copy") }
                TextButton(onClick = { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, displayOutput.take(100000)) }, "Share result")) }) { Text("Share") }
                TextButton(onClick = { exporter.launch("DocMate-result.txt") }) { Text("Export TXT") }
            }
            HorizontalDivider()
            Text("Saved documents (${history.size})", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(search, { search = it }, label = { Text("Search document names") }, modifier = Modifier.fillMaxWidth())
            history.filter { item -> search.isBlank() || item.name.contains(search, true) || item.tags.any { it.contains(search, true) } || item.pages.any { it.text.contains(search, true) } }.sortedByDescending { it.favorite }.forEach { item ->
                OutlinedButton(onClick = { doc = item; output = item.result.ifBlank { "Loaded ${item.pages.size} pages." }; question = "" }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text((if (item.favorite) "★ " else "") + item.name) }
            }
        }
    }
}
