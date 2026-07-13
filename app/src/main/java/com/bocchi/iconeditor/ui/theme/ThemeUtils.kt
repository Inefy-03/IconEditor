package com.bocchi.iconeditor.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.luminance
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun isIconEditorDark(): Boolean = MiuixTheme.colorScheme.surface.luminance() < 0.5f
