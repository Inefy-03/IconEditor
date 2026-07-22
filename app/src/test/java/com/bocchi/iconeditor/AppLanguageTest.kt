package com.bocchi.iconeditor

import com.bocchi.iconeditor.ui.locale.AppLanguage
import com.bocchi.iconeditor.ui.locale.appLanguageFromOverride
import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageTest {
    @Test
    fun mapsEmptyOverrideToFollowSystem() {
        assertEquals(AppLanguage.FollowSystem, appLanguageFromOverride(null))
        assertEquals(AppLanguage.FollowSystem, appLanguageFromOverride(""))
        assertEquals(AppLanguage.SimplifiedChinese, appLanguageFromOverride("zh"))
        assertEquals(AppLanguage.English, appLanguageFromOverride("en"))
    }
}
