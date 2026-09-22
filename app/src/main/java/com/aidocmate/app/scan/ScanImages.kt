package com.aidocmate.app.scan

import android.graphics.*
import java.io.File
import kotlin.math.roundToInt

object ScanImages {
    fun render(file: File, edge: Int, mode: ScanMode, rotation: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }; BitmapFactory.decodeFile(file.path, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Unreadable scan image" }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > edge * 2) sample *= 2
        var bitmap = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }) ?: error("Cannot decode scan")
        val ratio = minOf(1f, edge.toFloat() / maxOf(bitmap.width, bitmap.height))
        if (ratio < 1f) {
            val resized = Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).roundToInt().coerceAtLeast(1), (bitmap.height * ratio).roundToInt().coerceAtLeast(1), true)
            if (resized !== bitmap) bitmap.recycle(); bitmap = resized
        }
        if (rotation != 0) { val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(rotation.toFloat()) }, true); if (rotated !== bitmap) bitmap.recycle(); bitmap = rotated }
        if (mode == ScanMode.COLOUR) return bitmap
        val w = bitmap.width; val h = bitmap.height; val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val tile = 64; val cols = (w + tile - 1) / tile; val rows = (h + tile - 1) / tile
        val brightest = IntArray(cols * rows)
        for (y in 0 until h) for (x in 0 until w) {
            val p = pixels[y * w + x]; val gray = (Color.red(p) * 299 + Color.green(p) * 587 + Color.blue(p) * 114) / 1000
            pixels[y * w + x] = gray
            val t = y / tile * cols + x / tile; brightest[t] = maxOf(brightest[t], gray)
        }
        for (y in 0 until h) for (x in 0 until w) {
            val gray = pixels[y * w + x]
            // Local white-point normalisation reduces uneven lighting; retain the colour original.
            val normal = (gray * 255 / brightest[y / tile * cols + x / tile].coerceAtLeast(80)).coerceIn(0, 255)
            val value = when (mode) {
                ScanMode.GRAYSCALE -> gray
                ScanMode.BLACK_WHITE -> if (normal > 175) 255 else 0
                else -> ((normal - 25) * 255 / 230).coerceIn(0, 255)
            }
            pixels[y * w + x] = Color.rgb(value, value, value)
        }
        val result = Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888); bitmap.recycle(); return result
    }
    fun quality(file: File): QualityReport {
        val bitmap = render(file, 320, ScanMode.COLOUR, 0)
        try {
            val pixels = IntArray(bitmap.width * bitmap.height); bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            val gray = pixels.map { (Color.red(it) * 299 + Color.green(it) * 587 + Color.blue(it) * 114) / 1000 }.toIntArray()
            return ScanQuality.analyze(gray, bitmap.width, bitmap.height)
        } finally { bitmap.recycle() }
    }
}
