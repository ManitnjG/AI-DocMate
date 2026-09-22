package com.aidocmate.app.scan
import org.junit.Assert.*
import org.junit.Test
class ScanQualityTest {
    @Test fun lowDetailPhotoWarnsButDoesNotClaimClipping() {
        val report = ScanQuality.analyze(IntArray(10000) { 180 }, 100, 100)
        assertTrue(report.blur); assertFalse(report.glare); assertFalse(report.clipped)
    }
    @Test fun sharpPatternIsNotClassifiedAsBlur() {
        val pixels = IntArray(10000) { if ((it % 100 / 4 + it / 100 / 4) % 2 == 0) 255 else 0 }
        assertFalse(ScanQuality.analyze(pixels, 100, 100).blur)
    }
    @Test fun edgeContentRequiresReview() {
        val pixels = IntArray(10000) { if (it % 100 < 3) 0 else 220 }
        assertTrue(ScanQuality.analyze(pixels, 100, 100).clipped)
    }
    @Test fun localSaturationWithDocumentDetailWarnsAboutGlare() {
        val pixels = IntArray(10000) { val x = it % 100; val y = it / 100; if (x < 25 && y < 25) 255 else if (y % 8 < 2) 30 else 210 }
        assertTrue(ScanQuality.analyze(pixels, 100, 100).glare)
    }
}
