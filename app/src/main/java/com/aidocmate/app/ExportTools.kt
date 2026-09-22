package com.aidocmate.app

import android.content.Context
import android.graphics.pdf.PdfDocument
import android.net.Uri
import java.io.OutputStreamWriter

object ExportTools {
    fun writePdf(context: Context, uri: Uri, title: String, text: String) {
        val pdf = PdfDocument()
        val paint = android.graphics.Paint().apply { textSize = 12f; isAntiAlias = true }
        val titlePaint = android.graphics.Paint().apply { textSize = 18f; isFakeBoldText = true; isAntiAlias = true }
        val width = 595; val height = 842; val margin = 42f; val lineHeight = 17f
        val maxChars = 82
        val lines = text.lineSequence().flatMap { line ->
            if (line.isBlank()) sequenceOf("") else line.chunked(maxChars).asSequence()
        }.toList()
        var index = 0; var pageNo = 1
        do {
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(width, height, pageNo).create())
            var y = margin
            page.canvas.drawText(if (pageNo == 1) title.take(60) else title.take(50) + " — continued", margin, y, titlePaint)
            y += 30f
            while (index < lines.size && y < height - margin) {
                page.canvas.drawText(lines[index], margin, y, paint); y += lineHeight; index++
            }
            pdf.finishPage(page); pageNo++
        } while (index < lines.size)
        context.contentResolver.openOutputStream(uri)?.use { pdf.writeTo(it) } ?: error("Cannot write PDF")
        pdf.close()
    }

    fun writeHtmlDoc(context: Context, uri: Uri, title: String, text: String) {
        fun esc(s: String) = s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;")
        val html = "<html><head><meta charset=\"utf-8\"><title>${esc(title)}</title></head><body><h1>${esc(title)}</h1><pre style=\"white-space:pre-wrap;font-family:sans-serif\">${esc(text)}</pre></body></html>"
        context.contentResolver.openOutputStream(uri)?.use { OutputStreamWriter(it, Charsets.UTF_8).use { w -> w.write(html) } } ?: error("Cannot write document")
    }
}
