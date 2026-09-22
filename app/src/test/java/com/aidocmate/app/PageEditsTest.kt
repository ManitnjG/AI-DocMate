package com.aidocmate.app
import org.junit.Assert.*
import org.junit.Test
class PageEditsTest {
    @Test fun editsPreserveSourceAndHistory() {
        val edits = PageEdits(3)
        edits.rotate(0); edits.duplicate(0); edits.move(0, 3)
        assertEquals(listOf(0, 1, 2, 0), edits.pages.map { it.source })
        assertEquals(90, edits.pages.last().rotation)
        edits.undo(); assertEquals(listOf(0, 0, 1, 2), edits.pages.map { it.source })
        edits.redo(); assertEquals(0, edits.pages.last().source)
        edits.undo(); edits.delete(0); assertFalse(edits.canRedo)
    }
    @Test(expected = IllegalArgumentException::class) fun cannotDeleteLastPage() { PageEdits(1).delete(0) }
    @Test fun blankPageCanBeUndone() { val edits = PageEdits(1); edits.insertBlank(0); assertEquals(-1, edits.pages[1].source); edits.undo(); assertEquals(1, edits.pages.size) }
    @Test fun fourRotationsRestoreOrientation() { val edits = PageEdits(1); repeat(4) { edits.rotate(0) }; assertEquals(0, edits.pages[0].rotation) }
}
