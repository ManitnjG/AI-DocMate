package com.aidocmate.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

val DocMateBlue=Color(0xFF087CFF)
val DocMatePurple=Color(0xFF7C3AED)
val DocMateCyan=Color(0xFF12D6D0)
val DocMatePink=Color(0xFFFF3D81)
val DocMateOrange=Color(0xFFFF8A1F)
val DocMateGreen=Color(0xFF08B981)

@Composable
fun DocMateHero(){
 Card(shape=RoundedCornerShape(24.dp),modifier=Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=Color.Transparent)){
  Box(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(DocMateBlue,DocMatePurple,DocMateCyan)),RoundedCornerShape(24.dp)).padding(20.dp)){
   Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
    Text("Turn any document",color=Color.White,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.ExtraBold)
    Text("into useful information",color=Color.White,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
    Text("Scan  •  Edit  •  Convert  •  Ask AI",color=Color.White.copy(alpha=.9f))
   }
  }
 }
}

@Composable
fun ColorTool(label:String,color:Color,onClick:()->Unit){
 Button(onClick=onClick,modifier=Modifier.height(72.dp).fillMaxWidth(),shape=RoundedCornerShape(18.dp),
  colors=ButtonDefaults.buttonColors(containerColor=color,contentColor=Color.White),
  contentPadding=PaddingValues(8.dp)){ Text(label,fontWeight=FontWeight.Bold) }
}

@Composable
fun DocMateQuickActions(onScan:()->Unit,onEdit:()->Unit,onImport:()->Unit,onAi:()->Unit){
 Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
  Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
   Box(Modifier.weight(1f)){ColorTool("Scan\nDocument",DocMateBlue,onScan)}
   Box(Modifier.weight(1f)){ColorTool("AI\nAssistant",DocMatePurple,onAi)}
  }
  Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
   Box(Modifier.weight(1f)){ColorTool("Edit PDF",DocMatePink,onEdit)}
   Box(Modifier.weight(1f)){ColorTool("Import",DocMateGreen,onImport)}
   Box(Modifier.weight(1f)){ColorTool("OCR",DocMateOrange,onImport)}
  }
 }
}

@Composable
fun DocMateBottomBar(selected:String,onSelect:(String)->Unit){
 NavigationBar(containerColor=MaterialTheme.colorScheme.surface,tonalElevation=6.dp){
  listOf("Home","Files","Add","Tools","AI").forEach { item->
   NavigationBarItem(selected=selected==item,onClick={onSelect(item)},icon={
    Surface(shape=RoundedCornerShape(12.dp),color=if(item=="Add") DocMateBlue else Color.Transparent){
     Text(if(item=="Add") "+" else when(item){"Home"->"⌂";"Files"->"▤";"Tools"->"⊞";else->"✦"},
      modifier=Modifier.padding(horizontal=if(item=="Add") 12.dp else 6.dp,vertical=6.dp),
      color=if(item=="Add") Color.White else MaterialTheme.colorScheme.onSurface)
    }
   },label={if(item!="Add") Text(item)})
  }
 }
}
