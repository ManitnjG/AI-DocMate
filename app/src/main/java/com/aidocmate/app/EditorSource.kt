package com.aidocmate.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

internal fun loadEditorSource(context: Context, uri: Uri): File {
    val raw = File.createTempFile("editor-input", ".bin", context.cacheDir)
    val pdfFile = File.createTempFile("editor-source", ".pdf", context.cacheDir)
    try {
        context.contentResolver.openInputStream(uri)?.use { input -> raw.outputStream().use { out ->
            val buffer = ByteArray(8192); var total = 0
            while (true) { val n = input.read(buffer); if (n < 0) break; total += n
                require(total <= 25 * 1024 * 1024) { "Maximum file size is 25 MB" }; out.write(buffer, 0, n) }
        } } ?: error("Cannot read file")
        val header = ByteArray(5); raw.inputStream().use { it.read(header) }
        if (String(header, Charsets.US_ASCII) == "%PDF-") {
            PDDocument.load(raw).use { require(it.numberOfPages in 1..100) { "Choose 1–100 pages" }; require(!it.isEncrypted) { "Unlock the PDF before editing" } }
            raw.copyTo(pdfFile, overwrite = true)
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }; BitmapFactory.decodeFile(raw.path, bounds)
            require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Choose PDF, JPG or PNG" }
            var sample = 1; while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2400) sample *= 2
            val bitmap = BitmapFactory.decodeFile(raw.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: error("Cannot decode image")
            try { PDDocument().use { pdf ->
                val page = PDPage(PDRectangle(bitmap.width.toFloat(), bitmap.height.toFloat())); pdf.addPage(page)
                PDPageContentStream(pdf, page).use { it.drawImage(LosslessFactory.createFromImage(pdf, bitmap), 0f, 0f, page.mediaBox.width, page.mediaBox.height) }; pdf.save(pdfFile)
            } } finally { bitmap.recycle() }
        }
        return pdfFile
    } catch (e: Exception) { pdfFile.delete(); throw e } finally { raw.delete() }
}

