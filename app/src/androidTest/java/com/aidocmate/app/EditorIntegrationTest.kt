package com.aidocmate.app

import android.graphics.*
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.*
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.junit.*
import org.junit.Assert.*
import java.io.File

class EditorIntegrationTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    @Before fun setup() { PDFBoxResourceLoader.init(context);EditorDraftStore(context).clear() }
    private fun source():File=File.createTempFile("editor-test",".pdf",context.cacheDir).also { file->
        PDDocument().use { pdf->val page=PDPage(PDRectangle(400f,600f));pdf.addPage(page)
            PDPageContentStream(pdf,page).use { it.beginText();it.setFont(PDType1Font.HELVETICA,20f);it.newLineAtOffset(40f,500f);it.showText("Original invoice 500");it.endText() };pdf.save(file)
        }
    }
    @Test fun reopeningDraftRestoresLayersAndHistoryAndDoesNotNeedOriginal() {
        val file=source();val store=EditorDraftStore(context);val draft=store.adopt(file,1)
        draft.edits.putMark(0,EditorMark(text="தமிழ்",bold=true));draft.edits.rotate(0);draft.edits.undo();store.save(draft)
        file.delete()
        val restored=EditorDraftStore(context).load()!!
        assertTrue(restored.source.isFile);assertEquals("தமிழ்",restored.edits.pages[0].marks.single().text)
        assertTrue(restored.edits.canRedo);restored.edits.redo();assertEquals(90,restored.edits.pages[0].rotation)
        store.clear();assertNull(store.load())
    }
    @Test fun editedExportRemovesOriginalTextAndRendersAnnotations() {
        val file=source();val output=File.createTempFile("edited",".pdf",context.cacheDir)
        try {
            val marks=listOf(EditorMark(kind="replace",left=.05f,top=.1f,right=.95f,bottom=.3f,text="Updated invoice",size=.04f),
                EditorMark(kind="rectangle",left=.1f,top=.5f,right=.8f,bottom=.8f,color=Color.RED,size=.006f),
                EditorMark(kind="ink",points=listOf(EditorPoint(.2f,.7f),EditorPoint(.7f,.9f))))
            EditorRenderer.export(file,listOf(EditorPage(0,90,marks)),output)
            PDDocument.load(output).use { pdf->assertEquals(1,pdf.numberOfPages);assertEquals(90,pdf.getPage(0).rotation);assertFalse(PDFTextStripper().getText(pdf).contains("Original")) }
            ParcelFileDescriptor.open(output,ParcelFileDescriptor.MODE_READ_ONLY).use { fd->PdfRenderer(fd).use { renderer->renderer.openPage(0).use { page->
                assertTrue(page.width>page.height)
                val bitmap=Bitmap.createBitmap(page.width,page.height,Bitmap.Config.ARGB_8888)
                try { bitmap.eraseColor(Color.WHITE);page.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    var red=0
                    for(y in 0 until bitmap.height) for(x in 0 until bitmap.width) {val c=bitmap.getPixel(x,y);if(Color.red(c)>160&&Color.green(c)<90&&Color.blue(c)<90) red++}
                    assertTrue("Red annotation missing from export",red>100)
                } finally { bitmap.recycle() }
            } } }
        } finally { file.delete();output.delete() }
    }
    @Test fun importWorkerPersistsResultAndDoesNotDuplicateOnRetry() = kotlinx.coroutines.runBlocking {
        val file=source()
        val id=AssistantJobStore(context).use { it.create("",android.net.Uri.fromFile(file).toString(),"eng",false,"import") }
        try {
            val worker=androidx.work.testing.TestListenableWorkerBuilder<AssistantWorker>(context)
                .setInputData(androidx.work.workDataOf("job" to id)).build()
            assertEquals(androidx.work.ListenableWorker.Result.success(),worker.doWork())
            assertEquals(androidx.work.ListenableWorker.Result.success(),worker.doWork())
            AssistantJobStore(context).use { db->assertEquals("completed",db.get(id)!!.status);assertEquals(id,db.get(id)!!.docId) }
            val documents=DocumentStore(context)
            assertEquals(1,documents.list().count { it.id==id })
            assertTrue(documents.get(id)!!.pages.first().text.contains("Original invoice"))
            documents.delete(documents.get(id)!!)
        } finally { AssistantJobStore(context).use { it.deleteDocument(id) };file.delete() }
    }
    @Test fun conversationAndCancellationSurviveDatabaseReopen() {
        val docId=java.util.UUID.randomUUID().toString()
        val id=AssistantJobStore(context).use { it.create(docId,"When?","English",false) }
        AssistantJobStore(context).use { db->db.set(id,"completed","Tomorrow [p.1]") }
        AssistantJobStore(context).use { db->assertEquals("Tomorrow [p.1]",db.get(id)!!.answer);db.set(id,"cancelled");db.set(id,"completed","Late result");assertEquals("cancelled",db.get(id)!!.status);db.deleteDocument(docId) }
    }
}
