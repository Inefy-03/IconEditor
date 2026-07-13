package com.bocchi.iconeditor.ui.locale

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList

enum class AppLanguage(val languageTag: String) {
    SimplifiedChinese("zh-Hans"),
    English("en"),
}

fun currentAppLanguage(context: Context): AppLanguage {
    val localeManager = context.getSystemService(LocaleManager::class.java)
    return localeManager.applicationLocales.get(0)?.language
        ?.let(::defaultAppLanguage)
        ?: defaultAppLanguage(localeManager.systemLocales.get(0)?.language)
}

fun ensureInitialAppLanguage(context: Context) {
    val localeManager = context.getSystemService(LocaleManager::class.java)
    if (!localeManager.applicationLocales.isEmpty) return
    val language = defaultAppLanguage(localeManager.systemLocales.get(0)?.language)
    localeManager.applicationLocales = LocaleList.forLanguageTags(language.languageTag)
}

fun setAppLanguage(context: Context, language: AppLanguage) {
    context.getSystemService(LocaleManager::class.java).applicationLocales =
        LocaleList.forLanguageTags(language.languageTag)
}

internal fun defaultAppLanguage(systemLanguage: String?): AppLanguage =
    if (systemLanguage == "zh") AppLanguage.SimplifiedChinese else AppLanguage.English
