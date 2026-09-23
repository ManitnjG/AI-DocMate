package com.aidocmate.app

import androidx.compose.ui.graphics.Color

enum class ToolCapability { READY, UI_READY, PLANNED }
data class DocMateTool(val title:String,val subtitle:String,val group:String,val color:Color,val capability:ToolCapability)

object ToolCatalog {
 val tools=listOf(
  DocMateTool("Scan to PDF","Camera scanner","Create & Scan",DocMateBlue,ToolCapability.READY),
  DocMateTool("Images to PDF","Import photos","Create & Scan",DocMateGreen,ToolCapability.READY),
  DocMateTool("Camera Scan","Auto crop & clean","Create & Scan",DocMatePurple,ToolCapability.READY),
  DocMateTool("Import Files","PDF, DOCX, PPTX, TXT","Create & Scan",DocMateGreen,ToolCapability.READY),
  DocMateTool("Edit PDF","Text, images, signatures","Edit & Organize",DocMatePink,ToolCapability.READY),
  DocMateTool("Merge PDF","Combine files","Edit & Organize",DocMateBlue,ToolCapability.READY),
  DocMateTool("Split PDF","Extract selected pages","Edit & Organize",DocMateOrange,ToolCapability.READY),
  DocMateTool("Reorder Pages","Drag & reorder","Edit & Organize",DocMatePurple,ToolCapability.READY),
  DocMateTool("Compress PDF","Reduce scan size","Edit & Organize",DocMateGreen,ToolCapability.READY),
  DocMateTool("Rotate Pages","90° / 180°","Edit & Organize",DocMateCyan,ToolCapability.READY),
  DocMateTool("Add Watermark","Text or image","Edit & Organize",DocMatePurple,ToolCapability.UI_READY),
  DocMateTool("Protect PDF","Password & permissions","Edit & Organize",DocMateOrange,ToolCapability.UI_READY),
  DocMateTool("Sign","Add signature","Edit & Organize",DocMatePurple,ToolCapability.READY),
  DocMateTool("OCR","Indian-language OCR","AI Tools",DocMateOrange,ToolCapability.READY),
  DocMateTool("AI Summary","Summarize document","AI Tools",DocMatePurple,ToolCapability.READY),
  DocMateTool("Extract Text","Source text & fields","AI Tools",DocMateBlue,ToolCapability.READY),
  DocMateTool("Ask Questions","Chat with document","AI Tools",DocMateBlue,ToolCapability.READY),
  DocMateTool("Translate","English / Tamil","AI Tools",DocMateCyan,ToolCapability.READY),
  DocMateTool("Compare","Compare documents","AI Tools",DocMatePurple,ToolCapability.READY),
  DocMateTool("PDF to Word","DOCX export","Convert",DocMateBlue,ToolCapability.READY),
  DocMateTool("PDF to Image","JPG / PNG","Convert",DocMateOrange,ToolCapability.UI_READY),
  DocMateTool("Image to PDF","Photos to PDF","Convert",DocMatePurple,ToolCapability.READY),
  DocMateTool("PDF to Excel","Structured CSV / spreadsheet","Convert",DocMateGreen,ToolCapability.READY),
  DocMateTool("PDF to PowerPoint","PPTX","Convert",DocMateOrange,ToolCapability.PLANNED),
  DocMateTool("PDF to Text","TXT","Convert",DocMateCyan,ToolCapability.READY),
  DocMateTool("PDF to HTML","HTML export","Convert",DocMateOrange,ToolCapability.UI_READY)
 )
}
