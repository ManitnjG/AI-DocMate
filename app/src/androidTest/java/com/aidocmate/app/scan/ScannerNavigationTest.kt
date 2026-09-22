package com.aidocmate.app.scan

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.assertIsDisplayed
import com.aidocmate.app.MainActivity
import org.junit.Rule
import org.junit.Test

class ScannerNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun scannerOpenedFromEditorSurvivesActivityRecreation() {
        compose.onNodeWithText("Smart Editor • PDF & images").performScrollTo().performClick()
        compose.onNodeWithText("Document scanner").performClick()
        compose.onNodeWithText("Document Scanner").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        compose.onNodeWithText("Document Scanner").assertIsDisplayed()
    }
}
