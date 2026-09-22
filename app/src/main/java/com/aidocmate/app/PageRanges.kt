package com.aidocmate.app

/** One-based input, zero-based output. Preserves requested order without duplicates. */
object PageRanges {
    fun parse(input: String, count: Int): List<Int> {
        require(count in 1..100)
        require(input.isNotBlank() && input.length <= 1000) { "Enter pages such as 1-3, 5, 8" }
        val result = linkedSetOf<Int>()
        input.split(',').forEach { token ->
            val match = Regex("\\s*(\\d{1,3})\\s*(?:-\\s*(\\d{1,3})\\s*)?").matchEntire(token)
                ?: throw IllegalArgumentException("Use page numbers or ranges, such as 1-3, 5")
            val start = match.groupValues[1].toInt()
            val end = match.groupValues[2].toIntOrNull() ?: start
            require(start in 1..count && end in start..count) { "Choose ascending ranges between 1 and $count" }
            (start..end).forEach { result.add(it - 1) }
        }
        return result.toList()
    }
}
