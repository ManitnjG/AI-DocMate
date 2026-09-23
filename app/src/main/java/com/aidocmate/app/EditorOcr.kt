package com.aidocmate.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import com.aidocmate.app.scan.OcrModels
import com.aidocmate.app.scan.OcrLanguages
import com.googlecode.tesseract.android.TessBaseAPI
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

data class EditorOcrLine(val mark: EditorMark, val confidence: Int)
object EditorOcr {
    suspend fun lines(context: Context, bitmap: Bitmap, language: String): List<EditorOcrLine> {
        if(language == "eng") {
            val recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            try { return recognizer.process(InputImage.fromBitmap(bitmap,0)).await().textBlocks.flatMap { it.lines }.mapNotNull { line ->
                line.boundingBox?.let { box -> EditorOcrLine(estimate(bitmap,box,line.text),-1) }
            }.take(200) } finally { recognizer.close() }
        }
        val models=OcrModels(context); check(models.ready(language)) { "Download the selected OCR language packs first" }
        val api=TessBaseAPI()
        try {
            check(api.init(models.root.path,OcrLanguages.codes(language).joinToString("+"),TessBaseAPI.OEM_LSTM_ONLY))
            api.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO); api.setImage(bitmap); api.getUTF8Text()
            val result=mutableListOf<EditorOcrLine>(); val iterator=api.resultIterator ?: return result
            try {
                iterator.begin()
                do {
                    val level=TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE
                    val text=iterator.getUTF8Text(level)?.trim().orEmpty(); val box=iterator.getBoundingBox(level)
                    if(text.isNotBlank() && box != null) result.add(EditorOcrLine(estimate(bitmap,Rect(box[0],box[1],box[2],box[3]),text),iterator.confidence(level).toInt()))
                } while(result.size < 200 && iterator.next(TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE))
            } finally { iterator.delete() }
            return result
        } finally { api.recycle() }
    }
    private fun estimate(bitmap: Bitmap, box: Rect, text: String): EditorMark {
        val w=bitmap.width; val h=bitmap.height
        val left=(box.left-2).coerceIn(0,w-1); val top=(box.top-2).coerceIn(0,h-1)
        val right=(box.right+2).coerceIn(left+1,w); val bottom=(box.bottom+3).coerceIn(top+1,h)
        val background=bitmap.getPixel(left,(top-2).coerceAtLeast(0))
        var darkest=Color.BLACK; var min=Int.MAX_VALUE
        for(y in top until bottom step 3) for(x in left until right step 3) {
            val c=bitmap.getPixel(x,y); val brightness=Color.red(c)+Color.green(c)+Color.blue(c)
            if(brightness<min) { min=brightness; darkest=c }
        }
        return EditorMark(kind="replace",left=left.toFloat()/w,top=top.toFloat()/h,right=right.toFloat()/w,bottom=bottom.toFloat()/h,
            text=text.take(4000),size=((bottom-top).toFloat()/h*.88f).coerceIn(.001f,.2f),color=darkest,background=background,eraseBox=listOf(left.toFloat()/w,top.toFloat()/h,right.toFloat()/w,bottom.toFloat()/h))
    }
}
