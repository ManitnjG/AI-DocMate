package com.aidocmate.app

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class EditorDraft(val source: File, val edits: PageEdits, val selected: Int)
class EditorDraftStore(context: Context) {
    private val dir = File(context.filesDir, "editor-draft").apply { mkdirs() }
    private val state = AtomicFile(File(dir, "state.json"))
    fun load(): EditorDraft? {
        if (!state.baseFile.exists() && !File(dir,"state.json.bak").exists()) return null
        val bytes = state.openRead().use { BoundedInput.read(it, 14_000_000) }
        val json = JSONObject(bytes.toString(Charsets.UTF_8))
        val name = json.getString("source"); require(Regex("[a-f0-9-]+\\.pdf").matches(name))
        val source = File(dir,name); check(source.isFile) { "Saved editor source is missing" }
        val a = json.getJSONArray("history"); val edits = PageEdits.restore((0 until a.length()).map { a.getString(it) })
        return EditorDraft(source,edits,json.getInt("selected").coerceIn(0,edits.pages.lastIndex))
    }
    fun adopt(file: File, count: Int): EditorDraft {
        val previous = runCatching { load()?.source }.getOrNull()
        val destination = File(dir, "${UUID.randomUUID()}.pdf")
        try {
            file.inputStream().use { input -> destination.outputStream().use { input.copyTo(it); it.fd.sync() } }
            val draft = EditorDraft(destination, PageEdits(count),0); save(draft)
            previous?.takeIf { it != destination }?.delete()
            return draft
        } catch (e: Exception) { destination.delete(); throw e }
    }
    fun save(draft: EditorDraft) {
        require(draft.source.parentFile?.canonicalFile == dir.canonicalFile)
        val json = JSONObject().put("source",draft.source.name).put("selected",draft.selected).put("history",JSONArray(draft.edits.snapshot()))
        val bytes = json.toString().toByteArray(Charsets.UTF_8)
        require(bytes.size <= 14_000_000) { "Draft is too large; export and start a new draft" }
        val out = state.startWrite()
        try { out.write(bytes); state.finishWrite(out) } catch (e: Exception) { state.failWrite(out); throw e }
    }
    fun clear() { state.delete(); dir.listFiles()?.filter { it.extension == "pdf" }?.forEach { check(it.delete()) { "Cannot remove draft" } } }
}
