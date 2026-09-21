package com.aidocmate.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
 override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { MaterialTheme { DocMateApp() } } }
}

@Composable fun DocMateApp() {
 var status by remember { mutableStateOf("Choose a document to begin") }
 Scaffold(topBar={ TopAppBar(title={Text("AI DocMate")}) }) { p ->
  Column(Modifier.padding(p).padding(20.dp), verticalArrangement=Arrangement.spacedBy(14.dp)) {
   Text("Understand any document", style=MaterialTheme.typography.headlineMedium)
   Text("Summarize • Ask questions • Extract details • Translate • Compare")
   Button(onClick={status="Document picker is next in the MVP"}, modifier=Modifier.fillMaxWidth()){Text("Scan or upload document")}
   HorizontalDivider()
   Text(status)
   Text("Quick tools", style=MaterialTheme.typography.titleLarge)
   listOf("AI Summary","Ask Document","Extract dates & amounts","Tamil ↔ English","Compare Documents").forEach { OutlinedButton(onClick={status="$it — select a document first"}, modifier=Modifier.fillMaxWidth()){Text(it)} }
  }
 }
}
