package com.aidocmate.app.scan

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class ScanPage(val id: String, val rotation: Int = 0, val mode: ScanMode = ScanMode.COLOUR, val warnings: List<String> = emptyList())
data class ScanDraft(val pages: List<ScanPage> = emptyList(), val language: String = "eng", val quality: ExportQuality = ExportQuality.BALANCED, val searchable: Boolean = false)

class ScanDraftStore(private val context: Context) {
    private val dir = File(context.filesDir, "scan-draft").apply { mkdirs() }
    private val manifest = AtomicFile(File(dir, "draft.json"))
    fun image(page: ScanPage): File {
        require(page.id.matches(Regex("[a-f0-9-]{36}"))) { "Invalid scan page" }
        return File(dir, page.id + ".jpg")
    }
    @Synchronized fun load(): ScanDraft {
        if (!manifest.baseFile.exists() && !File(dir, "draft.json.bak").exists()) return ScanDraft()
        val o = JSONObject(manifest.openRead().bufferedReader().use { it.readText() }); val pages = o.getJSONArray("pages")
        return ScanDraft((0 until pages.length()).map { i -> val p = pages.getJSONObject(i)
            val a = p.optJSONArray("warnings") ?: JSONArray()
            ScanPage(p.getString("id"), p.optInt("rotation"), ScanMode.valueOf(p.optString("mode", "COLOUR")), (0 until a.length()).map { a.getString(it) }).also { require(image(it).isFile) { "A saved scan page is missing" } }
        }, o.optString("language", "eng"), ExportQuality.valueOf(o.optString("quality", "BALANCED")), o.optBoolean("searchable"))
    }
    @Synchronized fun save(draft: ScanDraft) {
        require(draft.pages.size <= 30) { "A draft can contain at most 30 pages" }
        val o = JSONObject().put("language", draft.language).put("quality", draft.quality.name).put("searchable", draft.searchable)
            .put("pages", JSONArray().apply { draft.pages.forEach { p -> put(JSONObject().put("id", p.id).put("rotation", p.rotation).put("mode", p.mode.name).put("warnings", JSONArray(p.warnings))) } })
        val out = manifest.startWrite()
        try { out.write(o.toString().toByteArray()); manifest.finishWrite(out) } catch (e: Exception) { manifest.failWrite(out); throw e }
        val keep = draft.pages.map { it.id + ".jpg" }.toSet()
        dir.listFiles()?.filter { it.extension == "jpg" && it.name !in keep }?.forEach { it.delete() }
    }
    @Synchronized fun importPages(uris: List<Uri>, replaceId: String? = null): ScanDraft {
        val old = load(); require(uris.isNotEmpty()) { "Scanner returned no pages" }
        require(old.pages.size + uris.size - (if (replaceId != null) 1 else 0) <= 30) { "Maximum 30 pages per draft" }
        if (replaceId != null) require(uris.size == 1 && old.pages.any { it.id == replaceId }) { "Retake requires one page" }
        val added = mutableListOf<ScanPage>()
        try {
            uris.forEach { uri ->
                val page = ScanPage(UUID.randomUUID().toString()); added.add(page)
                val file = image(page)
                context.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { output ->
                    val buffer = ByteArray(8192); var size = 0L
                    while (true) { val n = input.read(buffer); if (n < 0) break; size += n; require(size <= 25L * 1024 * 1024) { "Scan page exceeds 25 MB" }; output.write(buffer, 0, n) }
                } } ?: error("Cannot read scanned page")
                require((dir.listFiles()?.filter { it.extension == "jpg" }?.sumOf { it.length() } ?: 0) <= 250L * 1024 * 1024) { "Draft exceeds 250 MB" }
                added[added.lastIndex] = page.copy(warnings = ScanImages.quality(file).warnings())
            }
            val next = old.copy(pages = if (replaceId == null) old.pages + added else old.pages.map { if (it.id == replaceId) added.single() else it })
            save(next); return next
        } catch (e: Exception) { added.forEach { image(it).delete() }; throw e }
    }
}
