package com.bocchi.iconeditor

import com.bocchi.iconeditor.ui.navigation.predictiveBackHandlerEnabled
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PredictiveBackTransitionsTest {
    @Test
    fun enablesPredictiveBackOnlyForASecondaryPage() {
        assertTrue(predictiveBackHandlerEnabled(predictiveBackEnabled = true, hasPreviousEntries = true))
        assertFalse(predictiveBackHandlerEnabled(predictiveBackEnabled = true, hasPreviousEntries = false))
        assertFalse(predictiveBackHandlerEnabled(predictiveBackEnabled = false, hasPreviousEntries = true))
    }
}
