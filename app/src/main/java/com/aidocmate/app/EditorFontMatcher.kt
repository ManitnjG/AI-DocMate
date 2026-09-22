package com.aidocmate.app

import android.graphics.Paint
import android.graphics.Typeface
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.File
import java.security.MessageDigest

object EditorFontMatcher {
    /** Reuses an embedded font only when Android can load it and it contains the original glyphs. */
    fun match(source:File,pageIndex:Int,lines:List<EditorOcrLine>):List<EditorOcrLine> {
        if(pageIndex<0 || lines.isEmpty()) return lines
        return runCatching { PDDocument.load(source).use { pdf->
            val page=pdf.getPage(pageIndex)
            if(page.rotation%360!=0) return@use lines
            val width=page.cropBox.width;val height=page.cropBox.height
            val positions=mutableListOf<TextPosition>()
            val stripper=object:PDFTextStripper() {
                override fun processTextPosition(text:TextPosition) { positions.add(text);super.processTextPosition(text) }
            }
            stripper.startPage=pageIndex+1;stripper.endPage=pageIndex+1;stripper.getText(pdf)
            val fonts=File(source.parentFile,"fonts").apply{mkdirs()}
            val cache=mutableMapOf<String,String>()
            lines.map { line->
                val m=line.mark
                val candidates=positions.filter { pos->
                    val x=pos.xDirAdj/width;val y=pos.yDirAdj/height
                    x in (m.left-.01f)..(m.right+.01f) && y in (m.top-.01f)..(m.bottom+.02f)
                }
                val pos=candidates.firstOrNull() ?: return@map line
                val name=pos.font.name.orEmpty();val lower=name.lowercase()
                val family=when { "times" in lower || "serif" in lower && "sans" !in lower -> "serif";"courier" in lower || "mono" in lower -> "monospace";else->"sans-serif" }
                val key=cache.getOrPut(name) {
                    runCatching {
                        val descriptor=pos.font.fontDescriptor
                        val stream=descriptor?.fontFile2 ?: descriptor?.fontFile3 ?: return@runCatching ""
                        val bytes=stream.createInputStream().use { BoundedInput.read(it,8_000_000) }
                        val id=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
                        val file=File(fonts,"$id.font");if(!file.exists()) file.writeBytes(bytes)
                        val typeface=Typeface.createFromFile(file)
                        val paint=Paint().apply { this.typeface=typeface }
                        if(m.text.filterNot{it.isWhitespace()}.all { paint.hasGlyph(it.toString()) }) id else ""
                    }.getOrDefault("")
                }
                line.copy(mark=m.copy(family=family,bold="bold" in lower,italic="italic" in lower||"oblique" in lower,
                    size=(pos.fontSizeInPt/height).coerceIn(.001f,.2f),fontKey=key))
            }
        } }.getOrDefault(lines)
    }
}
