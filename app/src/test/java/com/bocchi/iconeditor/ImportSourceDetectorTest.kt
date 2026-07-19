package com.bocchi.iconeditor

import com.bocchi.iconeditor.model.SourceType
import org.junit.Assert.assertEquals
import org.junit.Test

class ImportSourceDetectorTest {
    @Test
    fun detectsApkFromFileName() {
        assertEquals(SourceType.Apk, detectFromFileName("Arcticons.apk"))
    }

    @Test
    fun detectsMtzFromFileName() {
        assertEquals(SourceType.Mtz, detectFromFileName("theme.mtz"))
    }

    private fun detectFromFileName(name: String): SourceType? = when (
        name.substringAfterLast('.', "").lowercase()
    ) {
        "apk" -> SourceType.Apk
        "mtz" -> SourceType.Mtz
        "zip" -> null
        else -> null
    }
}
