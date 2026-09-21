package com.aidocmate.app

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity: ComponentActivity() {
 override fun onCreate(b: Bundle?) { super.onCreate(b); PDFBoxResourceLoader.init(applicationContext); setContent { MaterialTheme { DocMateApp(applicationContext) } } }
}

private suspend fun extractPdf(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
 val tmp=File.createTempFile("docmate",".pdf",context.cacheDir)
 context.contentResolver.openInputStream(uri)!!.use { i->tmp.outputStream().use{i.copyTo(it)} }
 PDDocument.load(tmp).use { PDFTextStripper().getText(it) }.also { tmp.delete() }
}
private fun details(t:String):String {
 val dates=Regex("""\b(?:\d{1,2}[-/.]\d{1,2}[-/.]\d{2,4}|\d{1,2}\s+(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\s+\d{2,4})\b""",RegexOption.IGNORE_CASE).findAll(t).map{it.value}.distinct().take(15).toList()
 val money=Regex("""(?:₹|Rs\.?|INR)\s?[\d,]+(?:\.\d{1,2})?""",RegexOption.IGNORE_CASE).findAll(t).map{it.value}.distinct().take(15).toList()
 return "Dates: "+(dates.ifEmpty{listOf("None detected")}.joinToString())+"\n\nAmounts: "+(money.ifEmpty{listOf("None detected")}.joinToString())
}
private fun localSummary(t:String):String {
 val clean=t.replace(Regex("\\s+")," ").trim()
 if(clean.isBlank()) return "No selectable text was found. This may be a scanned PDF; OCR support is required."
 val sentences=clean.split(Regex("(?<=[.!?])\\s+")).filter{it.length>30}
 return sentences.take(8).joinToString("\n\n").take(5000)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun DocMateApp(context:Context) {
 var text by remember{mutableStateOf("")}; var name by remember{mutableStateOf("No document selected")}; var output by remember{mutableStateOf("Import a PDF to begin.")}; var busy by remember{mutableStateOf(false)}
 val scope=rememberCoroutineScope()
 val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->
  if(uri!=null){ name=uri.lastPathSegment?:"Document"; busy=true; scope.launch { runCatching{extractPdf(context,uri)}.onSuccess{text=it;output="Document loaded: "+it.length+" characters extracted."}.onFailure{output="Could not read PDF: "+(it.message?:"unknown error")}; busy=false } }
 }
 Scaffold(topBar={TopAppBar(title={Text("AI DocMate")})}){p->
  Column(Modifier.padding(p).padding(16.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){
   Text("Understand documents faster",style=MaterialTheme.typography.headlineSmall); Text(name)
   Button({picker.launch(arrayOf("application/pdf"))},Modifier.fillMaxWidth()){Text("Upload PDF")}
   if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
   OutlinedButton({output=localSummary(text)},Modifier.fillMaxWidth(),enabled=text.isNotBlank()){Text("Smart Summary")}
   OutlinedButton({output=details(text)},Modifier.fillMaxWidth(),enabled=text.isNotBlank()){Text("Extract dates & amounts")}
   OutlinedButton({output=text.take(7000)},Modifier.fillMaxWidth(),enabled=text.isNotBlank()){Text("View extracted text")}
   HorizontalDivider(); Text("Result",style=MaterialTheme.typography.titleMedium); Text(output)
   HorizontalDivider(); Text("Privacy: PDF processing in this build happens on your device. AI cloud features will only be enabled through a secure backend.")
  }
 }
}