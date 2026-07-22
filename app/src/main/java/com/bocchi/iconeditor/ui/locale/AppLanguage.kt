package com.bocchi.iconeditor.ui.locale

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList

enum class AppLanguage(val languageTag: String) {
    FollowSystem(""),
    SimplifiedChinese("zh-Hans"),
    English("en"),
}

fun currentAppLanguage(context: Context): AppLanguage {
    val localeManager = context.getSystemService(LocaleManager::class.java)
    val applicationLanguage = localeManager.applicationLocales
        .takeUnless { it.isEmpty }
        ?.get(0)
        ?.language
    return appLanguageFromOverride(applicationLanguage)
}

fun setAppLanguage(context: Context, language: AppLanguage) {
    context.getSystemService(LocaleManager::class.java).applicationLocales = when (language) {
        AppLanguage.FollowSystem -> LocaleList.getEmptyLocaleList()
        else -> LocaleList.forLanguageTags(language.languageTag)
    }
}

internal fun appLanguageFromOverride(language: String?): AppLanguage = when {
    language.isNullOrBlank() -> AppLanguage.FollowSystem
    language == "zh" -> AppLanguage.SimplifiedChinese
    else -> AppLanguage.English
}
