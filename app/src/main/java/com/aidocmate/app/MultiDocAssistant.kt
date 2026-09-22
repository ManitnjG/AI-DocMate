package com.aidocmate.app

object MultiDocAssistant {
    fun compare(docs: List<SavedDoc>, query: String): String {
        require(docs.size >= 2) { "Select at least two documents" }
        val q = query.trim()
        return buildString {
            append("Document comparison")
            if (q.isNotBlank()) append(" — ").append(q)
            append("\n\n")
            docs.forEachIndexed { index, doc ->
                append(index + 1).append(". ").append(doc.name).append("\n")
                val answer = LocalAssistant.answer(doc.pages, q, q.isBlank())
                append(answer.trim()).append("\n\n")
            }
            append("Comparison is extractive and source-grounded. Review the cited page/slide excerpts before making decisions.")
        }
    }
}
