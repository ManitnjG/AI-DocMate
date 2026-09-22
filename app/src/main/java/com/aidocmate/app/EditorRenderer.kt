package com.aidocmate.app

import android.graphics.*
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.tom_roush.pdfbox.pdmodel.*
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import java.io.File

object EditorRenderer {
    fun render(source: File, page: EditorPage, edge: Int = 1800, marks: Boolean = true): Bitmap {
        val bitmap = if (page.source < 0) Bitmap.createBitmap((edge * 595f / 842).toInt(), edge, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        else ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY).use { fd -> PdfRenderer(fd).use { renderer -> renderer.openPage(page.source).use { p ->
            val scale = edge.toFloat() / maxOf(p.width,p.height)
            Bitmap.createBitmap((p.width*scale).toInt().coerceAtLeast(1),(p.height*scale).toInt().coerceAtLeast(1),Bitmap.Config.ARGB_8888).also {
                it.eraseColor(Color.WHITE); p.render(it,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            }
        } } }
        if (marks) drawMarks(Canvas(bitmap), bitmap.width, bitmap.height, page.marks)
        return bitmap
    }
    fun drawMarks(canvas: Canvas, width: Int, height: Int, marks: List<EditorMark>) {
        val w = width.toFloat(); val h = height.toFloat()
        marks.forEach { mark ->
            val rect = RectF(mark.left*w,mark.top*h,mark.right*w,mark.bottom*h)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = mark.color; strokeWidth = mark.size*h }
            when(mark.kind) {
                "ink" -> {
                    paint.style = Paint.Style.STROKE; paint.strokeCap = Paint.Cap.ROUND; paint.strokeJoin = Paint.Join.ROUND
                    val path = Path(); mark.points.forEachIndexed { i,p -> if(i==0) path.moveTo(p.x*w,p.y*h) else path.lineTo(p.x*w,p.y*h) }; canvas.drawPath(path,paint)
                }
                "highlight" -> { paint.alpha = 80; canvas.drawRect(rect,paint) }
                "rectangle" -> { paint.style = Paint.Style.STROKE; canvas.drawRect(rect,paint) }
                else -> {
                    if(mark.kind == "replace") canvas.drawRect(rect,Paint().apply { color = mark.background })
                    val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = mark.color; textSize = mark.size*h
                        typeface = Typeface.create(mark.family,when { mark.bold && mark.italic -> Typeface.BOLD_ITALIC; mark.bold -> Typeface.BOLD; mark.italic -> Typeface.ITALIC; else -> Typeface.NORMAL })
                    }
                    val layout = StaticLayout.Builder.obtain(mark.text,0,mark.text.length,textPaint,rect.width().toInt().coerceAtLeast(1))
                        .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false).build()
                    canvas.save(); canvas.clipRect(rect); canvas.translate(rect.left,rect.top); layout.draw(canvas); canvas.restore()
                }
            }
        }
    }
    /** Modified pages are flattened, so replaced source text is not left in a hidden text layer. */
    fun export(source: File, pages: List<EditorPage>, destination: File) {
        PDDocument.load(source).use { original -> PDDocument().use { result ->
            pages.forEach { spec ->
                if (spec.marks.isEmpty()) {
                    val imported = if(spec.source < 0) PDPage(PDRectangle.A4).also(result::addPage) else result.importPage(original.getPage(spec.source)).apply {
                        resources=original.getPage(spec.source).resources; cropBox=original.getPage(spec.source).cropBox; mediaBox=original.getPage(spec.source).mediaBox
                    }
                    imported.rotation = (imported.rotation+spec.rotation)%360
                } else {
                    val bitmap=render(source,spec,2600)
                    try {
                        val box = if(spec.source < 0) PDRectangle.A4 else original.getPage(spec.source).cropBox
                        val intrinsic = if(spec.source < 0) 0 else original.getPage(spec.source).rotation
                        val width = if(intrinsic%180==0) box.width else box.height
                        val height = if(intrinsic%180==0) box.height else box.width
                        val page=PDPage(PDRectangle(width,height)); result.addPage(page)
                        PDPageContentStream(result,page).use { it.drawImage(LosslessFactory.createFromImage(result,bitmap),0f,0f,width,height) }
                        page.rotation=spec.rotation
                    } finally { bitmap.recycle() }
                }
            }
            result.save(destination)
        } }
    }
}
