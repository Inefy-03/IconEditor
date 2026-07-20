package com.bocchi.iconeditor.ui.page

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bocchi.iconeditor.R
import com.bocchi.iconeditor.data.ExportDirectoryHelper
import com.bocchi.iconeditor.model.AppSettings
import com.bocchi.iconeditor.model.ThemeMode
import com.bocchi.iconeditor.ui.locale.AppLanguage
import com.bocchi.iconeditor.ui.locale.currentAppLanguage
import com.bocchi.iconeditor.ui.locale.setAppLanguage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowSpinnerPreference
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun SettingsPage(
    settings: AppSettings,
    contentPadding: PaddingValues = PaddingValues(top = 12.dp, bottom = 12.dp),
    onSettings: (AppSettings) -> Unit,
    onTheme: () -> Unit,
    onProjectSync: () -> Unit,
    onTrash: () -> Unit,
    onAbout: () -> Unit,
) {
    val context = LocalContext.current
    val exportDirectoryLabel = remember(settings.exportDirectoryUri, context) {
        ExportDirectoryHelper.displayLabel(context, settings)
    }
    val exportDirectoryLauncher = rememberLauncherForActivityResult(
        contract = object : ActivityResultContracts.OpenDocumentTree() {
            override fun createIntent(context: android.content.Context, input: Uri?): Intent =
                super.createIntent(context, input).apply {
                    putExtra(DocumentsContract.EXTRA_INITIAL_URI, ExportDirectoryHelper.defaultTreeInitialUri())
                }
        },
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        onSettings(settings.copy(exportDirectoryUri = uri.toString()))
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .scrollEndHaptic()
            .overScrollVertical(),
        contentPadding = contentPadding,
        overscrollEffect = null,
    ) {
        item { SmallTitle(stringResource(R.string.personalization)) }
        item {
            PreferenceCard(bottom = 6.dp) {
                LanguageSpinner()
                SettingRow(
                    stringResource(R.string.screen_theme_settings),
                    stringResource(R.string.theme_settings_summary),
                    onTheme,
                )
            }
        }
        item { SmallTitle(stringResource(R.string.settings_file_handling)) }
        item {
            PreferenceCard(bottom = 6.dp) {
                ArrowPreference(
                    title = stringResource(R.string.screen_project_sync),
                    onClick = onProjectSync,
                )
                ArrowPreference(
                    title = stringResource(R.string.export_directory_title),
                    summary = exportDirectoryLabel,
                    onClick = { exportDirectoryLauncher.launch(null) },
                )
                if (settings.exportDirectoryUri.isNotBlank()) {
                    ArrowPreference(
                        title = stringResource(R.string.export_directory_reset),
                        onClick = { onSettings(settings.copy(exportDirectoryUri = "")) },
                    )
                }
                ArrowPreference(
                    title = stringResource(R.string.screen_trash),
                    onClick = onTrash,
                )
            }
        }
        item { SmallTitle(stringResource(R.string.settings_other)) }
        item {
            PreferenceCard(bottom = 6.dp) {
                ArrowPreference(
                    title = stringResource(R.string.about_title),
                    onClick = onAbout,
                )
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
fun PreferenceCard(bottom: androidx.compose.ui.unit.Dp = 12.dp, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .padding(bottom = bottom),
    ) {
        content()
    }
}

@Composable
fun SettingRow(title: String, summary: String, onClick: () -> Unit) {
    ArrowPreference(
        title = title,
        summary = summary,
        onClick = onClick,
    )
}

@Composable
fun ThemeSettingsPage(
    settings: AppSettings,
    contentPadding: PaddingValues = PaddingValues(top = 12.dp, bottom = 12.dp),
    onSettings: (AppSettings) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .scrollEndHaptic()
            .overScrollVertical(),
        contentPadding = contentPadding,
        overscrollEffect = null,
    ) {
        item { SmallTitle(stringResource(R.string.appearance)) }
        item {
            PreferenceCard {
                ThemeModeSpinner(
                    currentThemeMode = settings.themeMode,
                    onThemeModeChange = {
                        onSettings(settings.copy(themeMode = it))
                    },
                )
                SwitchRow(stringResource(R.string.blur_title), stringResource(R.string.blur_summary), settings.blurEnabled) {
                    onSettings(settings.copy(blurEnabled = it))
                }
                SwitchRow(
                    stringResource(R.string.floating_bottom_bar_title),
                    stringResource(R.string.floating_bottom_bar_summary),
                    settings.floatingBottomBar,
                ) {
                    onSettings(settings.copy(floatingBottomBar = it, liquidGlass = if (it) settings.liquidGlass else false))
                }
                AnimatedVisibility(
                    visible = settings.floatingBottomBar,
                    enter = fadeIn(animationSpec = tween(200)) + expandVertically(animationSpec = tween(250)),
                    exit = fadeOut(animationSpec = tween(150)) + shrinkVertically(animationSpec = tween(200)),
                ) {
                    SwitchRow(
                        stringResource(R.string.liquid_glass_title),
                        stringResource(R.string.liquid_glass_summary),
                        settings.liquidGlass,
                    ) {
                        onSettings(settings.copy(liquidGlass = it))
                    }
                }
            }
        }
        item { SmallTitle(stringResource(R.string.navigation_and_animation)) }
        item {
            PreferenceCard {
                SwitchRow(
                    title = stringResource(R.string.predictive_back_title),
                    summary = stringResource(R.string.predictive_back_summary),
                    checked = settings.predictiveBackEnabled,
                ) {
                    onSettings(settings.copy(predictiveBackEnabled = it))
                }
            }
        }
        item { Spacer(Modifier.height(12.dp).navigationBarsPadding()) }
    }
}

@Composable
fun ThemeModeSpinner(
    currentThemeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
) {
    val system = stringResource(R.string.theme_system_option)
    val light = stringResource(R.string.theme_light_option)
    val dark = stringResource(R.string.theme_dark_option)
    val themeModeOptions = remember(system, light, dark) {
        mapOf(
            ThemeMode.System to system,
            ThemeMode.Light to light,
            ThemeMode.Dark to dark,
        )
    }
    val spinnerEntries = remember(themeModeOptions) {
        themeModeOptions.values.map { DropdownItem(text = it) }
    }
    val selectedIndex = remember(currentThemeMode, themeModeOptions) {
        themeModeOptions.keys.indexOf(currentThemeMode).coerceAtLeast(0)
    }
    WindowSpinnerPreference(
        title = stringResource(R.string.theme_mode_title),
        items = spinnerEntries,
        selectedIndex = selectedIndex,
        onSelectedIndexChange = { newIndex ->
            val newMode = themeModeOptions.keys.elementAt(newIndex)
            if (currentThemeMode != newMode) onThemeModeChange(newMode)
        },
    )
}

@Composable
fun LanguageSpinner() {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val scope = rememberCoroutineScope()
    val currentLanguage = remember(context, configuration) { currentAppLanguage(context) }
    val chinese = stringResource(R.string.language_simplified_chinese)
    val english = stringResource(R.string.language_english)
    val options = remember(chinese, english) {
        mapOf(
            AppLanguage.SimplifiedChinese to chinese,
            AppLanguage.English to english,
        )
    }
    val entries = remember(options) { options.values.map { DropdownItem(text = it) } }
    val selectedIndex = options.keys.indexOf(currentLanguage).coerceAtLeast(0)
    WindowSpinnerPreference(
        title = stringResource(R.string.language_title),
        items = entries,
        selectedIndex = selectedIndex,
        onSelectedIndexChange = { index ->
            val language = options.keys.elementAt(index)
            if (language != currentLanguage) {
                scope.launch {
                    delay(150)
                    setAppLanguage(context, language)
                }
            }
        },
    )
}

@Composable
fun SwitchRow(
    title: String,
    summary: String = "",
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    SwitchPreference(
        title = title,
        summary = summary,
        checked = checked,
        onCheckedChange = onChecked,
    )
}
