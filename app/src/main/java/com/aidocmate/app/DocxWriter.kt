package com.aidocmate.app

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Minimal genuine OOXML document, with Unicode text and explicit paragraph breaks. */
object DocxWriter {
    private fun xml(text: String): String = buildString {
        text.codePoints().forEach { cp ->
            when (cp) {
                38 -> append("&amp;")
                60 -> append("&lt;")
                62 -> append("&gt;")
                34 -> append("&quot;")
                else -> if (cp == 9 || cp == 10 || cp == 13 || cp in 32..0xD7FF || cp in 0xE000..0xFFFD || cp in 0x10000..0x10FFFF) appendCodePoint(cp)
            }
        }
    }
    fun write(output: OutputStream, title: String, text: String) {
        ZipOutputStream(output).use { zip ->
            fun entry(name: String, value: String) {
                zip.putNextEntry(ZipEntry(name)); zip.write(value.toByteArray(Charsets.UTF_8)); zip.closeEntry()
            }
            entry("[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>""")
            entry("_rels/.rels", """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>""")
            val paragraphs = text.lineSequence().joinToString("") { line ->
                val runs = line.split('\t').joinToString("<w:r><w:tab/></w:r>") { part -> "<w:r><w:t xml:space=\"preserve\">${xml(part)}</w:t></w:r>" }
                "<w:p>$runs</w:p>"
            }
            entry("word/document.xml", """<?xml version="1.0" encoding="UTF-8"?><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body><w:p><w:r><w:rPr><w:b/><w:sz w:val="32"/></w:rPr><w:t xml:space="preserve">${xml(title)}</w:t></w:r></w:p>$paragraphs<w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1134" w:right="1134" w:bottom="1134" w:left="1134"/></w:sectPr></w:body></w:document>""")
        }
    }
}
