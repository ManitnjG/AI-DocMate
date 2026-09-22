package com.aidocmate.app

import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Enforces actual decompressed bytes, including archives with missing or dishonest sizes. */
object BoundedInput {
    fun read(input: InputStream, limit: Int): ByteArray {
        require(limit >= 0)
        val output = ByteArrayOutputStream(minOf(limit, 8192))
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            require(count <= limit - total) { "Document content exceeds the safe processing limit" }
            total += count; output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
}
