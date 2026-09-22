package com.aidocmate.app
import org.junit.Assert.assertArrayEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
class BoundedInputTest {
    @Test fun acceptsExactLimit() { val bytes = "தமிழ்".toByteArray(); assertArrayEquals(bytes, BoundedInput.read(ByteArrayInputStream(bytes), bytes.size)) }
    @Test(expected = IllegalArgumentException::class) fun rejectsExpandedZipBomb() {
        val packed = ByteArrayOutputStream()
        ZipOutputStream(packed).use { it.putNextEntry(ZipEntry("word/document.xml")); it.write(ByteArray(100_000)); it.closeEntry() }
        ZipInputStream(ByteArrayInputStream(packed.toByteArray())).use { it.nextEntry; BoundedInput.read(it, 4096) }
    }
    @Test fun emptyInputIsSafe() { assertArrayEquals(byteArrayOf(), BoundedInput.read(ByteArrayInputStream(byteArrayOf()), 0)) }
}
