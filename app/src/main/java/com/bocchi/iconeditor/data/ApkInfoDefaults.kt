package com.bocchi.iconeditor.data

import com.bocchi.iconeditor.model.ApkInfo
import com.bocchi.iconeditor.model.ProjectMetadata

object ApkInfoDefaults {
    /** aapt2 要求至少两段、每段以字母开头（例如 com.example.pack）。 */
    private val PACKAGE_NAME_REGEX = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$")

    fun isValidPackageName(packageName: String): Boolean =
        packageName.isNotBlank() && PACKAGE_NAME_REGEX.matches(packageName)

    fun resolve(projectName: String, metadata: ProjectMetadata): ApkInfo {
        val apk = metadata.apk
        val label = apk.label.takeIf { it.isNotBlank() }
            ?: metadata.mtz.title.takeIf { it.isNotBlank() }
            ?: metadata.module.name.takeIf { it.isNotBlank() }
            ?: projectName.takeIf { it.isNotBlank() }
            ?: "Icon Pack"

        val author = apk.author.takeIf { it.isNotBlank() }
            ?: metadata.mtz.author.takeIf { it.isNotBlank() }
            ?: metadata.module.author.takeIf { it.isNotBlank() }
            ?: metadata.mtz.designer.takeIf { it.isNotBlank() }
            ?: ""

        val versionName = when {
            apk.packageName.isNotBlank() || apk.label.isNotBlank() || apk.author.isNotBlank() ->
                apk.versionName.ifBlank { "1.0" }
            else -> metadata.mtz.version.takeIf { it.isNotBlank() }
                ?: metadata.module.version.takeIf { it.isNotBlank() }
                ?: apk.versionName.ifBlank { "1.0" }
        }

        val packageName = apk.packageName.takeIf(::isValidPackageName)
            ?: apk.packageName.takeIf { it.isNotBlank() }?.let(::sanitizePackageName)
            ?: metadata.module.themeId.takeIf(::isValidPackageName)
            ?: metadata.module.id.takeIf { it.isNotBlank() }?.let(::sanitizePackageName)
            ?: sanitizePackageName(label)

        return ApkInfo(
            packageName = packageName,
            versionName = versionName,
            versionCode = apk.versionCode.coerceAtLeast(1),
            label = label,
            author = author,
        )
    }

    /** 每次导出 APK 前递增 versionCode，并同步递增 versionName 末位数字。 */
    fun bumpVersion(apk: ApkInfo): ApkInfo = apk.copy(
        versionCode = apk.versionCode.coerceAtLeast(1) + 1,
        versionName = bumpVersionName(apk.versionName),
    )

    fun bumpVersionName(versionName: String): String {
        val base = versionName.trim().ifBlank { "1.0" }
        val match = Regex("(\\d+)(?!.*\\d)").find(base) ?: return "$base.1"
        val current = match.value.toLongOrNull() ?: return "$base.1"
        return base.replaceRange(match.range, (current + 1).toString())
    }

    fun sanitizePackageName(input: String): String {
        val trimmed = input.trim()
        if (isValidPackageName(trimmed)) return trimmed

        // 已有点但格式不对：尽量清理各段
        if (trimmed.contains('.')) {
            val segments = trimmed.lowercase()
                .split('.')
                .map { segment -> segment.filter { it.isLetterOrDigit() || it == '_' } }
                .filter { it.isNotEmpty() }
                .map { segment ->
                    if (segment.first().isLetter()) segment else "p$segment"
                }
            if (segments.size >= 2) {
                val candidate = segments.joinToString(".")
                if (isValidPackageName(candidate)) return candidate
            }
        }

        val compact = trimmed.lowercase().filter { it.isLetterOrDigit() }
        if (compact.isEmpty()) return "com.iconeditor.iconpack"
        val withLetterPrefix = if (compact.first().isLetter()) compact else "p$compact"
        val candidate = "com.iconeditor.$withLetterPrefix"
        return candidate.takeIf(::isValidPackageName) ?: "com.iconeditor.iconpack"
    }
}
