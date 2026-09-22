package com.aidocmate.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResultFormatterTest {
    private val pages = listOf(
        DocPage(1, "First page has the contract introduction and parties."),
        DocPage(2, "Payment is due within thirty days from the invoice date."),
        DocPage(3, "Termination requires written notice from either party.")
    )

    @Test fun citationsResolveBracketPageReferences() {
        val result = ResultFormatter.citations("Payment terms are on [p.2].", pages)
        assertEquals(1, result.size)
        assertEquals(2, result.first().page)
        assertTrue(result.first().snippet.contains("Payment"))
    }

    @Test fun citationsResolveReadablePageReferencesWithoutDuplicates() {
        val result = ResultFormatter.citations("See Page 3 and [p.3] for termination.", pages)
        assertEquals(1, result.size)
        assertEquals(3, result.first().page)
    }

    @Test fun citationsIgnoreUnknownPagesAndLimitOutput() {
        val manyPages = (1..20).map { DocPage(it, "Page $it source text") }
        val refs = (1..20).joinToString(" ") { "[p.$it]" } + " [p.999]"
        val result = ResultFormatter.citations(refs, manyPages)
        assertEquals(12, result.size)
        assertTrue(result.none { it.page == 999 })
    }

    @Test fun cleanConvertsCitationAndMarkdownForMobileDisplay() {
        val cleaned = ResultFormatter.clean("**Answer**<br>[p.2]")
        assertEquals("Answer\nPage 2", cleaned)
    }

    @Test fun classifyRecognizesCommonDocumentTypes() {
        assertEquals("Invoice / Bill", ResultFormatter.classify("GSTIN 33AAAAA0000A1Z5"))
        assertEquals("Agreement", ResultFormatter.classify("This contract contains terms and conditions"))
        assertEquals("Resume", ResultFormatter.classify("Resume experience education"))
    }
}
