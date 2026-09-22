package com.aidocmate.app.scan

/** Advisory signals only: white paper and low-text pages can trigger false positives. */
data class QualityReport(val blur: Boolean, val glare: Boolean, val clipped: Boolean) {
    fun warnings() = buildList {
        if (blur) add("Possible blur or low detail — hold steady and check text.")
        if (glare) add("Possible glare — check bright areas for missing text.")
        if (clipped) add("Content near an edge — check all four corners before exporting.")
    }
}
object ScanQuality {
    fun analyze(gray: IntArray, width: Int, height: Int): QualityReport {
        require(width >= 3 && height >= 3 && gray.size == width * height)
        var sum = 0.0; var squared = 0.0; var count = 0; var dark = 0; var nearEdgeDark = 0; var edgeCount = 0
        val patchBright = IntArray(16); val patchSize = IntArray(16)
        for (y in 0 until height) for (x in 0 until width) {
            val v = gray[y * width + x]; if (v < 100) dark++
            val patch = minOf(3, y * 4 / height) * 4 + minOf(3, x * 4 / width)
            patchSize[patch]++; if (v >= 253) patchBright[patch]++
            if (x < 3 || y < 3 || x >= width - 3 || y >= height - 3) { edgeCount++; if (v < 100) nearEdgeDark++ }
            if (x in 1 until width - 1 && y in 1 until height - 1) {
                val lap = 4 * v - gray[y * width + x - 1] - gray[y * width + x + 1] - gray[(y - 1) * width + x] - gray[(y + 1) * width + x]
                sum += lap; squared += lap.toDouble() * lap; count++
            }
        }
        val variance = squared / count - (sum / count) * (sum / count)
        val brightRatios = patchBright.indices.map { patchBright[it].toDouble() / patchSize[it].coerceAtLeast(1) }
        return QualityReport(variance < 65, dark > gray.size / 100 && brightRatios.max() > .96 && brightRatios.min() < .65, nearEdgeDark.toDouble() / edgeCount > .07)
    }
}
enum class ScanMode { COLOUR, GRAYSCALE, ENHANCED, BLACK_WHITE }
enum class ExportQuality(val edge: Int, val jpeg: Int, val label: String) {
    SMALL(1200, 65, "Small"), BALANCED(1800, 82, "Balanced"), HIGH(2400, 94, "High")
}
