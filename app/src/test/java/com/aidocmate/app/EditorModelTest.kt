package com.aidocmate.app

import org.junit.Assert.*
import org.junit.Test

class EditorModelTest {
    @Test fun unicodeAnnotationsSurviveHistoryAndPageOperations() {
        val model=PageEdits(2)
        val mark=EditorMark(kind="replace",text="தமிழ் हिन्दी اردو",bold=true,italic=true,background=0xFFCCCCCC.toInt())
        model.putMark(0,mark);model.duplicate(0);model.move(1,2);model.rotate(2)
        val restored=PageEdits.restore(model.snapshot())
        assertEquals(model.pages,restored.pages)
        assertEquals(mark,restored.pages[2].marks.single())
        restored.undo();assertEquals(0,restored.pages[2].rotation)
        restored.redo();assertEquals(90,restored.pages[2].rotation)
        restored.removeMark(2,mark.id);assertTrue(restored.pages[2].marks.isEmpty())
        assertEquals(mark,restored.pages[0].marks.single())
    }
    @Test fun legacyDraftSnapshotsRemainReadable() {
        val edits=PageEdits.restore(listOf("1,90;-1,0","0"))
        assertEquals(listOf(EditorPage(1,90),EditorPage(-1)),edits.pages)
    }
    @Test fun inkPathsRoundTripWithoutPrecisionLoss() {
        val mark=EditorMark(kind="ink",points=listOf(EditorPoint(.12f,.25f),EditorPoint(.85f,.9f)))
        val pages=listOf(EditorPage(0,270,listOf(mark)))
        assertEquals(pages,EditorPageCodec.decode(EditorPageCodec.encode(pages)))
    }
    @Test(expected=IllegalArgumentException::class) fun invalidCoordinatesRejected() { EditorMark(left=Float.NaN).validate() }
    @Test(expected=IllegalArgumentException::class) fun corruptSnapshotRejected() { EditorPageCodec.decode("v2:AAAAAgAAAGU=") }
}
