package com.aidocmate.app

/** Extractive utilities, deliberately never presented as generative AI. */
object LocalAssistant {
    fun answer(pages: List<DocPage>, question: String, summary: Boolean): String {
        val pieces = pages.flatMap { page -> page.text.split(Regex("(?<=[.!?])\\s+|\\n+")).filter { it.trim().length > 15 }.map { page.number to it.trim() } }
        if (pieces.isEmpty()) return "No readable passages were found. Try View source text."
        if (summary) {
            // Spread excerpts across the entire document instead of only its opening.
            val count = minOf(10, pieces.size)
            val chosen = (0 until count).map { pieces[it * pieces.size / count] }
            return "Offline overview — selected source excerpts, not an AI summary.\n\n" + chosen.joinToString("\n\n") { "[p.${it.first}] ${it.second}" }
        }
        val stop = setOf("what", "when", "where", "the", "this", "document", "does", "and", "are", "is", "of", "in")
        val words = Regex("[\\p{L}\\p{N}]+").findAll(question.lowercase()).map { it.value }.filter { it.length > 1 && it !in stop }.toSet()
        val ranked = pieces.map { p -> p to words.count { p.second.lowercase().contains(it) } }.filter { it.second > 0 }.sortedByDescending { it.second }.take(6)
        if (ranked.isEmpty()) return "Offline source search: no matching passages found. Try words used in your document."
        return "Offline source search — matching excerpts, not an AI answer.\n\n" + ranked.joinToString("\n\n") { "[p.${it.first.first}] ${it.first.second}" }
    }
}
