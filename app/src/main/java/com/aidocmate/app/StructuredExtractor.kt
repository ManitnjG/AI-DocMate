package com.aidocmate.app

data class InvoiceFields(
    val invoiceNumber: String? = null, val date: String? = null, val gstin: String? = null,
    val taxableValue: String? = null, val cgst: String? = null, val sgst: String? = null,
    val igst: String? = null, val total: String? = null
) {
    fun display(): String = listOf(
        "Invoice number" to invoiceNumber, "Date" to date, "GSTIN" to gstin,
        "Taxable value" to taxableValue, "CGST" to cgst, "SGST" to sgst,
        "IGST" to igst, "Total" to total
    ).filter { !it.second.isNullOrBlank() }.joinToString("\n") { "${it.first}: ${it.second}" }
}

object StructuredExtractor {
    private fun first(pattern: String, text: String) =
        Regex(pattern, setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)).find(text)?.groupValues?.getOrNull(1)?.trim()

    fun invoice(text: String): InvoiceFields {
        val amount = """(?:₹|Rs\.?|INR)?\s*([0-9][0-9,]*(?:\.\d{1,2})?)"""
        return InvoiceFields(
            invoiceNumber = first("""(?:invoice\s*(?:no|number|#)|bill\s*(?:no|number))\s*[:#-]?\s*([A-Z0-9/_-]+)""", text),
            date = first("""(?:invoice\s*date|date)\s*[:#-]?\s*(\d{1,2}[-/.]\d{1,2}[-/.]\d{2,4})""", text),
            gstin = first("""\bGSTIN\s*[:#-]?\s*([0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][A-Z0-9]Z[A-Z0-9])""", text),
            taxableValue = first("""taxable\s*(?:value|amount)\s*[:#-]?\s*$amount""", text),
            cgst = first("""CGST(?:\s*@?\s*[0-9.]+%)?\s*[:#-]?\s*$amount""", text),
            sgst = first("""SGST(?:\s*@?\s*[0-9.]+%)?\s*[:#-]?\s*$amount""", text),
            igst = first("""IGST(?:\s*@?\s*[0-9.]+%)?\s*[:#-]?\s*$amount""", text),
            total = first("""(?:grand\s*total|invoice\s*total|total\s*amount|amount\s*payable)\s*[:#-]?\s*$amount""", text)
        )
    }

    fun csv(text: String): String {
        val f = invoice(text)
        fun q(v: String?) = "\"" + (v ?: "").replace("\"", "\"\"") + "\""
        return "field,value\n" + listOf(
            "Invoice number" to f.invoiceNumber, "Date" to f.date, "GSTIN" to f.gstin,
            "Taxable value" to f.taxableValue, "CGST" to f.cgst, "SGST" to f.sgst,
            "IGST" to f.igst, "Total" to f.total
        ).joinToString("\n") { q(it.first) + "," + q(it.second) }
    }
}
