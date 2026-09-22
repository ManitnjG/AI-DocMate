package com.aidocmate.app

/** Immutable page references keep the source untouched and make history inexpensive. */
data class EditorPage(val source: Int, val rotation: Int = 0, val marks: List<EditorMark> = emptyList())
class PageEdits(count: Int) {
    init { require(count in 1..100) { "Keep between 1 and 100 pages" } }
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
        while (past.size > 1 && past.sumOf { encode(it).length.toLong() } > 6_000_000) past.removeAt(0)
    }
    /** Compact saved-instance snapshot, including undo and redo history. */
    fun snapshot(): List<String> = listOf(encode(pages), past.size.toString()) + past.map(::encode) + future.map(::encode)
    private fun encode(value: List<EditorPage>) = EditorPageCodec.encode(value)
    companion object {
        fun restore(snapshot: List<String>): PageEdits {
            require(snapshot.size in 2..102)
            fun decode(value: String) = EditorPageCodec.decode(value)
            val pastCount = snapshot[1].toInt()
            require(pastCount in 0..50 && pastCount <= snapshot.size - 2)
            require(snapshot.size - 2 - pastCount <= 50)
            return PageEdits(1).apply {
                pages = decode(snapshot[0])
                past.addAll(snapshot.drop(2).take(pastCount).map(::decode))
                future.addAll(snapshot.drop(2 + pastCount).map(::decode))
            }
        }
    }
    fun putMark(index: Int, mark: EditorMark) {
        mark.validate()
        change(pages.mapIndexed { i,p -> if (i != index) p else {
            val marks = p.marks.filterNot { it.id == mark.id } + mark
            require(marks.size <= 100) { "Maximum 100 annotations per page" }; p.copy(marks = marks)
        } })
    }
    fun removeMark(index: Int, id: String) = change(pages.mapIndexed { i,p -> if (i == index) p.copy(marks = p.marks.filterNot { it.id == id }) else p })
    fun insertBlank(index: Int) = change(pages.toMutableList().apply { add(index + 1, EditorPage(-1)) })
    fun rotate(index: Int) = change(pages.mapIndexed { i, p -> if (i == index) p.copy(rotation = (p.rotation + 90) % 360) else p })
    fun delete(index: Int) = change(pages.filterIndexed { i, _ -> i != index })
    fun duplicate(index: Int) = change(pages.toMutableList().apply { add(index + 1, pages[index]) })
    fun move(index: Int, target: Int) { require(target in pages.indices); change(pages.toMutableList().apply { add(target, removeAt(index)) }) }
    fun undo() { if (canUndo) { future.add(pages); pages = past.removeAt(past.lastIndex) } }
    fun redo() { if (canRedo) { past.add(pages); pages = future.removeAt(future.lastIndex) } }
}
