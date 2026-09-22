package com.aidocmate.app

object ResultFormatter {
    fun clean(raw: String): String = raw
        .replace(Regex("(?i)<br\\s*/?>"), "\n")
        .replace(Regex("\\[p\\.(\\d+)]", RegexOption.IGNORE_CASE), "Page $1")
        .replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
        .replace(Regex("(?m)^\\s*\\|?\\s*:?-{3,}:?\\s*(\\|\\s*:?-{3,}:?\\s*)+\\|?\\s*$"), "")
        .lineSequence()
        .map { line ->
            val t = line.trim()
            if (t.startsWith("|") && t.endsWith("|")) t.trim('|').split('|').joinToString("  •  ") { it.trim() } else line
        }
        .joinToString("\n")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()

    fun suggestedQuestions(text: String): List<String> {
        val base = mutableListOf("Summarize this document", "List important dates", "Extract all amounts", "What actions are required?")
        if (Regex("(?i)invoice|gst|cgst|sgst|igst|tax").containsMatchIn(text)) {
            base.add(0, "Extract invoice and GST details")
        }
        if (Regex("(?i)agreement|contract|terms|clause").containsMatchIn(text)) {
            base.add(0, "Explain important clauses and obligations")
        }
        return base.distinct().take(5)
    }

    fun classify(text: String): String = when {
        Regex("(?i)invoice|gstin|cgst|sgst|igst").containsMatchIn(text) -> "Invoice / Bill"
        Regex("(?i)resume|curriculum vitae|education|experience").containsMatchIn(text) -> "Resume"
        Regex("(?i)agreement|contract|terms and conditions").containsMatchIn(text) -> "Agreement"
        Regex("(?i)bank statement|account statement|opening balance|closing balance").containsMatchIn(text) -> "Statement"
        Regex("(?i)dear sir|dear madam|subject:|yours faithfully").containsMatchIn(text) -> "Letter"
        else -> "Document"
    }
}
