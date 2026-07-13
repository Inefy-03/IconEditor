package com.bocchi.iconeditor

import com.bocchi.iconeditor.ui.locale.AppLanguage
import com.bocchi.iconeditor.ui.locale.defaultAppLanguage
import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageTest {
    @Test
    fun mapsUnsupportedSystemLanguagesToEnglish() {
        assertEquals(AppLanguage.SimplifiedChinese, defaultAppLanguage("zh"))
        assertEquals(AppLanguage.English, defaultAppLanguage("en"))
        assertEquals(AppLanguage.English, defaultAppLanguage("ja"))
        assertEquals(AppLanguage.English, defaultAppLanguage(null))
    }
}
