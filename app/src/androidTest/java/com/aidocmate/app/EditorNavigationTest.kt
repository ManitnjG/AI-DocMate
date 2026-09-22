package com.aidocmate.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.tom_roush.pdfbox.pdmodel.*
import org.junit.*
import org.junit.Assert.*
import java.io.File

class EditorNavigationTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun signatureLayerAndDraftSurviveLeavingAndRecreation() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        EditorDraftStore(context).clear()
        val source=File.createTempFile("editor-ui",".pdf",context.cacheDir)
        PDDocument().use { pdf->pdf.addPage(PDPage());pdf.save(source) }
        lateinit var vm:EditorViewModel
        compose.onNodeWithText("Smart Editor • PDF & images").performScrollTo().performClick()
        compose.runOnIdle { vm=ViewModelProvider(compose.activity)[EditorViewModel::class.java];vm.open(listOf(android.net.Uri.fromFile(source))) }
        compose.waitUntil(15000) { !vm.busy && vm.draft!=null }
        compose.onNodeWithText("Signature / pen").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Page 1").performTouchInput { swipe(centerLeft,centerRight,500) }
        compose.waitUntil(15000) { !vm.busy && vm.draft!!.edits.pages[0].marks.isNotEmpty() }
        compose.onNodeWithText("Back").performClick()
        compose.onNodeWithText("Smart Editor • PDF & images").performScrollTo().performClick()
        compose.activityRule.scenario.recreate();compose.waitForIdle()
        compose.onNodeWithText("Smart Editor").assertIsDisplayed()
        assertEquals("ink",EditorDraftStore(context).load()!!.edits.pages[0].marks.single().kind)
        compose.runOnIdle { ViewModelProvider(compose.activity)[EditorViewModel::class.java].discard() }
        compose.waitUntil(15000) { !vm.busy && vm.draft==null }
        source.delete()
    }
}
