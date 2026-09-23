package com.aidocmate.app

import android.graphics.*
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.test.platform.app.InstrumentationRegistry
import com.aidocmate.app.scan.*
import org.junit.Test
import org.junit.Assert.*
import java.io.File

/** Synthetic script smoke tests; real photos and handwriting still need device/field evaluation. */
class MultilingualOcrTest {
    @Test fun everySupportedIndicPackRecognizesItsScript() = kotlinx.coroutines.runBlocking {
        val instrumentation=InstrumentationRegistry.getInstrumentation();val context=instrumentation.targetContext
        val samples=linkedMapOf("tam" to "தமிழ் ஆவணம்", "hin" to "भारत सरकार", "ben" to "বাংলা ভাষা", "tel" to "తెలుగు భాష", "mar" to "मराठी भाषा", "guj" to "ગુજરાતી ભાષા", "kan" to "ಕನ್ನಡ ಭಾಷೆ", "mal" to "മലയാള ഭാഷ", "pan" to "ਪੰਜਾਬੀ ਭਾਸ਼ਾ", "ori" to "ଓଡ଼ିଆ ଭାଷା", "asm" to "অসমীয়া ভাষা", "urd" to "اردو زبان", "san" to "संस्कृत भाषा")
        val modelDir=File(context.filesDir,"ocr-models/tessdata").apply{mkdirs()}
        for(code in samples.keys+"eng") instrumentation.context.assets.open("$code.traineddata").use { input->File(modelDir,"$code.traineddata").outputStream().use { input.copyTo(it) } }
        val failures=mutableListOf<String>()
        for((language,text) in samples) {
            assertTrue("Pack verification failed: $language",OcrModels(context).ready(language))
            val bitmap=Bitmap.createBitmap(1800,900,Bitmap.Config.ARGB_8888)
            try {
                val canvas=Canvas(bitmap);canvas.drawColor(Color.WHITE)
                val paint=TextPaint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.BLACK;textSize=100f;typeface=Typeface.create("sans-serif",Typeface.NORMAL)}
                val paragraph=List(4){text}.joinToString("\n")
                val layout=StaticLayout.Builder.obtain(paragraph,0,paragraph.length,paint,1500).setAlignment(Layout.Alignment.ALIGN_NORMAL).build()
                canvas.translate(100f,100f);layout.draw(canvas)
                val recognised=EditorOcr.lines(context,bitmap,language).joinToString(" "){it.mark.text}
                val expected=text.filter{it.isLetter()}.toSet()
                val overlap=expected.count{it in recognised}.toDouble()/expected.size
                if(overlap<.5) failures.add("$language: recognised '$recognised' (character coverage=$overlap)")
            } finally { bitmap.recycle() }
        }
        assertTrue(failures.joinToString("\n"),failures.isEmpty())
    }
}
