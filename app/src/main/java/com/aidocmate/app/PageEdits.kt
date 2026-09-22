package com.aidocmate.app

/** Immutable page references keep the source untouched and make history inexpensive. */
data class EditorPage(val source: Int, val rotation: Int = 0)
class PageEdits(count: Int) {
    var pages = (0 until count).map { EditorPage(it) }; private set
    private val past = mutableListOf<List<EditorPage>>()
    private val future = mutableListOf<List<EditorPage>>()
    val canUndo get() = past.isNotEmpty()
    val canRedo get() = future.isNotEmpty()
    fun change(next: List<EditorPage>) {
        require(next.isNotEmpty() && next.size <= 100) { "Keep between 1 and 100 pages" }
        if (next == pages) return
        past.add(pages); if (past.size > 50) past.removeAt(0)
        pages = next.toList(); future.clear()
    }
    fun insertBlank(index: Int) = change(pages.toMutableList().apply { add(index + 1, EditorPage(-1)) })
    fun rotate(index: Int) = change(pages.mapIndexed { i, p -> if (i == index) p.copy(rotation = (p.rotation + 90) % 360) else p })
    fun delete(index: Int) = change(pages.filterIndexed { i, _ -> i != index })
    fun duplicate(index: Int) = change(pages.toMutableList().apply { add(index + 1, pages[index]) })
    fun move(index: Int, target: Int) { require(target in pages.indices); change(pages.toMutableList().apply { add(target, removeAt(index)) }) }
    fun undo() { if (canUndo) { future.add(pages); pages = past.removeAt(past.lastIndex) } }
    fun redo() { if (canRedo) { past.add(pages); pages = future.removeAt(future.lastIndex) } }
}
