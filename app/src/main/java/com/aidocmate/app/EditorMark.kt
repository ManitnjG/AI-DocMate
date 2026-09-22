package com.aidocmate.app

import java.io.*
import java.util.Base64
import java.util.UUID

data class EditorPoint(val x: Float, val y: Float)
data class EditorMark(
    val id: String = UUID.randomUUID().toString(), val kind: String = "text",
    val left: Float = .1f, val top: Float = .1f, val right: Float = .8f, val bottom: Float = .2f,
    val text: String = "", val size: Float = .025f, val color: Int = 0xFF000000.toInt(),
    val background: Int = 0xFFFFFFFF.toInt(), val family: String = "sans-serif",
    val bold: Boolean = false, val italic: Boolean = false, val points: List<EditorPoint> = emptyList()
) {
    fun validate() {
        require(kind in listOf("text", "replace", "ink", "highlight", "rectangle"))
        require(listOf(left, top, right, bottom, size).all { it.isFinite() })
        require(left in 0f..1f && top in 0f..1f && right in left..1f && bottom in top..1f)
        require(size in .001f..0.2f && text.length <= 4000 && points.size <= 3000)
        require(family in listOf("sans-serif", "serif", "monospace", "cursive"))
        require(points.all { it.x.isFinite() && it.y.isFinite() && it.x in 0f..1f && it.y in 0f..1f })
    }
}

/** Versioned binary snapshots are bounded before allocation and kept on disk, never in a Bundle. */
object EditorPageCodec {
    fun encode(pages: List<EditorPage>): String {
        val buffer = ByteArrayOutputStream()
        DataOutputStream(buffer).use { out ->
            out.writeInt(2); out.writeInt(pages.size)
            pages.forEach { page ->
                out.writeInt(page.source); out.writeInt(page.rotation); out.writeInt(page.marks.size)
                page.marks.forEach { m ->
                    m.validate(); out.writeUTF(m.id); out.writeUTF(m.kind)
                    listOf(m.left,m.top,m.right,m.bottom,m.size).forEach(out::writeFloat)
                    out.writeUTF(m.text); out.writeInt(m.color); out.writeInt(m.background); out.writeUTF(m.family)
                    out.writeBoolean(m.bold); out.writeBoolean(m.italic); out.writeInt(m.points.size)
                    m.points.forEach { out.writeFloat(it.x); out.writeFloat(it.y) }
                }
            }
        }
        require(buffer.size() <= 2_000_000) { "Too many annotations; export and start a new draft" }
        return "v2:" + Base64.getEncoder().encodeToString(buffer.toByteArray())
    }
    fun decode(value: String): List<EditorPage> {
        require(value.length <= 2_700_000)
        if (!value.startsWith("v2:")) return value.split(';').map {
            val p = it.split(','); require(p.size == 2); EditorPage(p[0].toInt(), p[1].toInt())
        }.also(::validate)
        return DataInputStream(ByteArrayInputStream(Base64.getDecoder().decode(value.drop(3)))).use { input ->
            require(input.readInt() == 2)
            val count = input.readInt(); require(count in 1..100)
            val pages = List(count) {
                val source = input.readInt(); val rotation = input.readInt(); val marks = input.readInt(); require(marks in 0..100)
                EditorPage(source, rotation, List(marks) {
                    val id = input.readUTF(); val kind = input.readUTF()
                    val left = input.readFloat(); val top = input.readFloat(); val right = input.readFloat(); val bottom = input.readFloat(); val size = input.readFloat()
                    val text = input.readUTF(); val color = input.readInt(); val background = input.readInt(); val family = input.readUTF()
                    val bold = input.readBoolean(); val italic = input.readBoolean(); val points = input.readInt(); require(points in 0..3000)
                    EditorMark(id,kind,left,top,right,bottom,text,size,color,background,family,bold,italic,List(points) { EditorPoint(input.readFloat(),input.readFloat()) }).also { it.validate() }
                })
            }
            require(input.available() == 0); validate(pages); pages
        }
    }
    private fun validate(pages: List<EditorPage>) {
        require(pages.size in 1..100)
        require(pages.all { it.source in -1..99 && it.rotation in listOf(0,90,180,270) && it.marks.size <= 100 })
    }
}
