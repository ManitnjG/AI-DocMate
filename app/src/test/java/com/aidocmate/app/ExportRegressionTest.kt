package com.aidocmate.app

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

class ExportRegressionTest {
    @Test fun savedEditsRestoreUndoRedoAndBlankPages() {
        val original = PageEdits(3)
        original.rotate(0); original.insertBlank(0); original.move(3, 0); original.undo()
        val restored = PageEdits.restore(original.snapshot())
        assertEquals(original.pages, restored.pages)
        restored.redo(); original.redo(); assertEquals(original.pages, restored.pages)
        repeat(3) { restored.undo(); original.undo(); assertEquals(original.pages, restored.pages) }
    }
    @Test fun rangesKeepOrderAndRemoveDuplicates() {
        assertEquals(listOf(4, 0, 1, 2), PageRanges.parse("5, 1-3, 2", 5))
    }
    @Test fun invalidRangesAreRejected() {
        listOf("", "0", "6", "3-1", "1,", "1--2", "1-999", "-1").forEach {
            try { PageRanges.parse(it, 5); fail("Accepted $it") } catch (_: IllegalArgumentException) { }
        }
    }
    @Test fun docxContainsValidXmlAndPreservesIndicText() {
        val output = ByteArrayOutputStream()
        DocxWriter.write(output, "Title & <tag>", "தமிழ் हिन्दी\nSecond\tcolumn\u0001")
        val files = mutableMapOf<String, ByteArray>()
        ZipInputStream(output.toByteArray().inputStream()).use { zip ->
            while (true) { val entry = zip.nextEntry ?: break; files[entry.name] = zip.readBytes() }
        }
        assertEquals(setOf("[Content_Types].xml", "_rels/.rels", "word/document.xml"), files.keys)
        val parser = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        files.values.forEach { parser.parse(it.inputStream()) }
        val doc = parser.parse(files.getValue("word/document.xml").inputStream())
        assertTrue(doc.documentElement.textContent.contains("தமிழ் हिन्दी"))
        assertTrue(doc.documentElement.textContent.contains("Title & <tag>"))
        assertFalse(doc.documentElement.textContent.contains('\u0001'))
        assertEquals(1, doc.getElementsByTagName("w:tab").length)
    }
}
