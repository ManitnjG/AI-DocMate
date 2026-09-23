package com.aidocmate.app

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.min

@Composable fun SmartEditor(context:Context,onBack:()->Unit) {
    val activity=context as ComponentActivity
    val vm=remember { ViewModelProvider(activity)[EditorViewModel::class.java] }
    var scannerOpen by rememberSaveable { mutableStateOf(false) }
    if(scannerOpen) { com.aidocmate.app.scan.ScannerScreen(activity) { scannerOpen=false }; return }
    val draft=vm.draft; val selected=draft?.selected ?: 0; val page=draft?.edits?.pages?.getOrNull(selected)
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var zoom by remember { mutableFloatStateOf(1f) }; var pan by remember { mutableStateOf(Offset.Zero) }
    var tool by rememberSaveable { mutableStateOf("pan") }
    var editing by remember { mutableStateOf<EditorMark?>(null) }
    var live by remember { mutableStateOf<List<EditorPoint>>(emptyList()) }
    var language by rememberSaveable { mutableStateOf("eng") }; var languageDialog by remember { mutableStateOf(false) }
    var discard by remember { mutableStateOf(false) }
    var range by rememberSaveable { mutableStateOf("") }; var rangeDialog by remember { mutableStateOf(false) }; var rangeError by remember { mutableStateOf<String?>(null) }
    var cameraPath by rememberSaveable { mutableStateOf("") }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> if(uris.isNotEmpty()) vm.open(uris) }
    val camera=rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if(ok && cameraPath.isNotBlank()) vm.open(listOf(Uri.fromFile(File(cameraPath))))
        else if(cameraPath.isNotBlank()) File(cameraPath).delete()
        cameraPath=""
    }
    fun capture() {
        try {
            val dir=File(context.cacheDir,"camera").apply { mkdirs() }
            val file=File.createTempFile("editor-camera",".jpg",dir); cameraPath=file.path
            camera.launch(androidx.core.content.FileProvider.getUriForFile(context,context.packageName+".fileprovider",file))
        } catch(e:Exception) { vm.status("Cannot open camera: ${e.message}") }
    }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if(it) capture() else vm.status("Camera permission denied. You can import a photo.") }
    val exporter=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if(uri!=null && vm.draft!=null) try { vm.export(uri,PageRanges.parse(range,vm.draft!!.edits.pages.size)) } catch(e:Exception) { vm.status(e.message ?: "Invalid pages") }
    }
    BackHandler { onBack() }
    LaunchedEffect(draft?.source,selected,vm.revision) {
        val current=vm.draft
        zoom=1f; pan=Offset.Zero; live=emptyList()
        if(current==null) { preview=null; return@LaunchedEffect }
        try { preview=withContext(Dispatchers.IO) { EditorRenderer.render(current.source,current.edits.pages[current.selected]) } }
        catch(e:Exception) { vm.status("Preview failed: ${e.message}"); preview=null }
    }
    if(discard) AlertDialog(onDismissRequest={discard=false},title={Text("Discard saved editor draft?")},text={Text("This removes the editable draft. Your originals and exported PDFs stay unchanged.")},
        confirmButton={TextButton(onClick={discard=false;vm.discard()}){Text("Discard draft")}},dismissButton={TextButton(onClick={discard=false}){Text("Keep draft")}})
    if(rangeDialog) AlertDialog(onDismissRequest={rangeDialog=false},title={Text("Export PDF pages")},text={Column {
        OutlinedTextField(range,{range=it.take(1000);rangeError=null},label={Text("Pages, e.g. 1-3, 5")},isError=rangeError!=null)
        Text("Edited pages are flattened for consistent appearance. Their text will not be searchable until OCR is run again.",style=MaterialTheme.typography.bodySmall)
        rangeError?.let { Text(it,color=MaterialTheme.colorScheme.error) }
    }},confirmButton={TextButton(onClick={try { PageRanges.parse(range,draft?.edits?.pages?.size ?: 0);rangeDialog=false;exporter.launch("DocMate-edited.pdf") } catch(e:Exception){rangeError=e.message}}){Text("Export")}},dismissButton={TextButton(onClick={rangeDialog=false}){Text("Cancel")}})
    if(languageDialog) AlertDialog(onDismissRequest={languageDialog=false},title={Text("OCR language")},text={Column(Modifier.heightIn(max=360.dp).verticalScroll(rememberScrollState())) {
        com.aidocmate.app.scan.OcrLanguages.names.forEach { (code,name) -> TextButton(onClick={language=code}) { Text((if(code==language) "✓ " else "")+name) } }
    }},confirmButton={TextButton(onClick={languageDialog=false;vm.download(language)}) { Text("Download packs") }},dismissButton={TextButton(onClick={languageDialog=false}) { Text("Done") }})
    editing?.let { mark -> EditorMarkDialog(mark,{editing=null},{updated->editing=null;vm.change { it.putMark(selected,updated) }},{editing=null;vm.change { it.removeMark(selected,mark.id) }}) }
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
        Surface(tonalElevation=4.dp,shape=RoundedCornerShape(18.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=5.dp),verticalAlignment=Alignment.CenterVertically) {
                IconButton(onClick=onBack){Text("‹",style=MaterialTheme.typography.headlineMedium)}
                Column(Modifier.weight(1f)){Text("Smart Editor",fontWeight=FontWeight.ExtraBold,style=MaterialTheme.typography.titleLarge);Text(if(draft==null) "Open a document" else "Saved automatically",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                if(draft!=null){IconButton(onClick={vm.change{it.undo()}},enabled=draft.edits.canUndo&&!vm.busy){Text("↶")};Button(onClick={range="1-"+draft.edits.pages.size;rangeDialog=true},enabled=!vm.busy,shape=RoundedCornerShape(14.dp),colors=ButtonDefaults.buttonColors(containerColor=DocMateBlue)){Text("Done")}}
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            TextButton(onClick={picker.launch(arrayOf("application/pdf","image/jpeg","image/png"))},enabled=!vm.busy && draft==null){Text("Open / merge")}
            TextButton(onClick={scannerOpen=true},enabled=!vm.busy && draft==null){Text("Document scanner")}
            TextButton(onClick={permission.launch(android.Manifest.permission.CAMERA)},enabled=!vm.busy && draft==null){Text("Capture photo")}
            if(draft!=null) {
                TextButton(onClick={range="1-${draft.edits.pages.size}";rangeDialog=true},enabled=!vm.busy){Text("Export PDF")}
                TextButton(onClick={discard=true},enabled=!vm.busy){Text("New / discard")}
            }
        }
        Text(vm.message,style=MaterialTheme.typography.bodySmall,maxLines=3)
        if(vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if(draft!=null) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("Tap the page to edit",Modifier.weight(1f),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant);Surface(shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.surfaceVariant){Text((selected+1).toString()+" / "+draft.edits.pages.size,Modifier.padding(horizontal=12.dp,vertical=6.dp),fontWeight=FontWeight.Bold)}}
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().clipToBounds(),contentAlignment=Alignment.Center) {
            val bitmap=preview
            if(bitmap!=null && page!=null) {
                val rotated=page.rotation%180!=0
                val factor=min(maxWidth.value/(if(rotated) bitmap.height else bitmap.width),maxHeight.value/(if(rotated) bitmap.width else bitmap.height))
                Box(Modifier.requiredSize((bitmap.width*factor).dp,(bitmap.height*factor).dp).graphicsLayer {
                    scaleX=zoom;scaleY=zoom;translationX=pan.x;translationY=pan.y;rotationZ=page.rotation.toFloat()
                }.pointerInput(tool,selected,vm.revision,vm.busy) {
                    fun point(offset:Offset)=EditorPoint((offset.x/size.width).coerceIn(0f,1f),(offset.y/size.height).coerceIn(0f,1f))
                    if(vm.busy) return@pointerInput
                    when(tool) {
                        "pan" -> detectTransformGestures { _,delta,scale,_ -> zoom=(zoom*scale).coerceIn(1f,5f);pan+=delta }
                        "text","select" -> detectTapGestures { position ->
                            val p=point(position)
                            if(tool=="text") editing=EditorMark(left=p.x.coerceAtMost(.9f),top=p.y.coerceAtMost(.9f),right=(p.x+.5f).coerceAtMost(1f),bottom=(p.y+.1f).coerceAtMost(1f))
                            else {
                                editing=(page.marks.asReversed()+vm.ocr.map { it.mark }).firstOrNull { p.x in it.left..it.right && p.y in it.top..it.bottom }
                                if(editing==null) vm.status("Tap an annotation or run OCR, then tap a detected text box.")
                            }
                        }
                        else -> detectDragGestures(onDragStart={live=listOf(point(it))},onDragCancel={live=emptyList()},onDragEnd={
                            val points=live;live=emptyList()
                            if(points.size>=2) {
                                val a=points.first();val b=points.last()
                                val l=if(tool=="ink") points.minOf{it.x} else minOf(a.x,b.x)
                                val t=if(tool=="ink") points.minOf{it.y} else minOf(a.y,b.y)
                                val r=if(tool=="ink") points.maxOf{it.x} else maxOf(a.x,b.x)
                                val bottom=if(tool=="ink") points.maxOf{it.y} else maxOf(a.y,b.y)
                                vm.change { it.putMark(selected,EditorMark(kind=tool,left=l,top=t,right=r,bottom=bottom,size=.003f,color=if(tool=="highlight") 0xFFFFCC00.toInt() else 0xFF111111.toInt(),points=if(tool=="ink") points else emptyList())) }
                            }
                        }) { change,_ -> change.consume();if(live.size<3000) live=live+point(change.position) }
                    }
                }) {
                    Image(bitmap.asImageBitmap(),"Page ${selected+1}",Modifier.fillMaxSize())
                    Canvas(Modifier.fillMaxSize()) {
                        vm.ocr.forEach { line -> val m=line.mark;drawRect(Color(0xFF2563EB),Offset(m.left*size.width,m.top*size.height),Size((m.right-m.left)*size.width,(m.bottom-m.top)*size.height),style=Stroke(2f)) }
                        live.zipWithNext().forEach { (a,b)->drawLine(Color.Black,Offset(a.x*size.width,a.y*size.height),Offset(b.x*size.width,b.y*size.height),3f) }
                    }
                }
            }
        }
        if(draft!=null) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically) {
                IconButton(onClick={vm.select(selected-1)},enabled=selected>0&&!vm.busy){Text("‹",style=MaterialTheme.typography.headlineSmall)}
                Text((selected+1).toString()+" / "+draft.edits.pages.size,modifier=Modifier.padding(horizontal=14.dp),fontWeight=FontWeight.Bold)
                IconButton(onClick={vm.select(selected+1)},enabled=selected<draft.edits.pages.lastIndex&&!vm.busy){Text("›",style=MaterialTheme.typography.headlineSmall)}
            }
            Surface(tonalElevation=8.dp,shape=RoundedCornerShape(20.dp)) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(8.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    listOf("select" to "⌖\nEdit","text" to "T\nText","ocr" to "⌗\nAI OCR","ink" to "✎\nSign","highlight" to "▰\nMarkup","pages" to "▤\nPages").forEach { pair ->
                        val key=pair.first; val label=pair.second; val active=tool==key
                        Surface(Modifier.width(68.dp).clickable(enabled=!vm.busy){when(key){"ocr"->{tool="select";vm.recognize(language)};"pages"->vm.change{it.rotate(selected)};else->tool=key}},shape=RoundedCornerShape(16.dp),color=if(active)DocMateBlue.copy(alpha=.18f) else MaterialTheme.colorScheme.surfaceVariant) {
                            Text(label,Modifier.padding(vertical=9.dp),textAlign=TextAlign.Center,fontWeight=if(active)FontWeight.Bold else FontWeight.Medium,color=if(active)DocMateBlue else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    Surface(Modifier.width(68.dp).clickable{languageDialog=true},shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surfaceVariant){Text("⋯\nMore",Modifier.padding(vertical=9.dp),textAlign=TextAlign.Center)}
                }
            }
        }
    }
}

@Composable private fun EditorMarkDialog(initial:EditorMark,onDismiss:()->Unit,onSave:(EditorMark)->Unit,onDelete:()->Unit) {
    var value by remember(initial.id) { mutableStateOf(initial) }
    AlertDialog(onDismissRequest=onDismiss,title={Text(if(value.kind=="replace") "Replace recognised text" else "Edit annotation")},text={Column(Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        if(value.kind in listOf("text","replace")) {
            OutlinedTextField(value.text,{value=value.copy(text=it.take(4000))},label={Text("Text")},modifier=Modifier.fillMaxWidth())
            Text("Font size");Slider(value.size,{value=value.copy(size=it)},valueRange=.005f..0.1f)
            Row(Modifier.horizontalScroll(rememberScrollState())) { listOf("sans-serif","serif","monospace","cursive").forEach { name->FilterChip(selected=value.family==name,onClick={value=value.copy(family=name,fontKey="")},label={Text(name)}) } }
            Row { FilterChip(selected=value.bold,onClick={value=value.copy(bold=!value.bold)},label={Text("Bold")});FilterChip(selected=value.italic,onClick={value=value.copy(italic=!value.italic)},label={Text("Italic")}) }
        }
        Text("Colour")
        Row(Modifier.horizontalScroll(rememberScrollState())) { listOf("Black" to 0xFF111111.toInt(),"Blue" to 0xFF1749C9.toInt(),"Red" to 0xFFC52222.toInt(),"Yellow" to 0xFFFFCC00.toInt()).forEach { (name,color)->TextButton(onClick={value=value.copy(color=color)}){Text(name)} } }
        if(value.kind=="replace") { Text(if(value.fontKey.isNotEmpty()) "Embedded font available. New characters may fall back if missing from that font. Review the background." else "Font and background are estimates. Complex backgrounds may need manual retouching.",style=MaterialTheme.typography.bodySmall);TextButton(onClick={value=value.copy(background=0xFFFFFFFF.toInt())}){Text("Use white background") } }
        if(value.kind!="ink") {
            Text("Horizontal position");Slider(value.left,{x->val width=value.right-value.left;value=value.copy(left=x,right=(x+width).coerceAtMost(1f))},valueRange=0f..0.9f)
            Text("Vertical position");Slider(value.top,{y->val height=value.bottom-value.top;value=value.copy(top=y,bottom=(y+height).coerceAtMost(1f))},valueRange=0f..0.9f)
            Text("Box width");Slider((value.right-value.left).coerceAtLeast(.01f),{value=value.copy(right=(value.left+it).coerceAtMost(1f))},valueRange=.01f..1f)
            Text("Box height");Slider((value.bottom-value.top).coerceAtLeast(.01f),{value=value.copy(bottom=(value.top+it).coerceAtMost(1f))},valueRange=.01f..1f)
        }
        TextButton(onClick=onDelete){Text("Delete annotation")}
    }},confirmButton={TextButton(onClick={onSave(value)}){Text("Apply")}},dismissButton={TextButton(onClick=onDismiss){Text("Cancel")}})
}
