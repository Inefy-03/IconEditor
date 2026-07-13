package com.bocchi.iconeditor.data

import com.bocchi.iconeditor.model.ApkInfo

/**
 * 生成标准图标包 AndroidManifest.xml（参考 CandyBar / IconPackSupporter 结构）。
 * hasCode=false，通过 Activity intent-filter 声明图标包能力。
 */
object IconPackManifestBuilder {
    fun build(apkInfo: ApkInfo, hasLauncherIcon: Boolean = false): String {
        val packageName = apkInfo.packageName.trim()
        require(packageName.isNotBlank()) { "APK 包名不能为空" }
        val activityName = "$packageName.IconPackActivity"
        val versionCode = apkInfo.versionCode.coerceAtLeast(1)
        val versionName = escapeXml(apkInfo.versionName.ifBlank { "1.0" })
        val iconAttrs = if (hasLauncherIcon) {
            """
                    android:icon="@drawable/ic_launcher"
                    android:roundIcon="@drawable/ic_launcher""""
        } else {
            ""
        }
        return """
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                package="$packageName"
                android:versionCode="$versionCode"
                android:versionName="$versionName">
                <uses-sdk
                    android:minSdkVersion="21"
                    android:targetSdkVersion="34" />
                <application
                    android:hasCode="false"
                    android:label="@string/app_name"$iconAttrs>
                    <activity
                        android:name="$activityName"
                        android:exported="true"
                        android:label="@string/app_name">
                        <intent-filter>
                            <action android:name="android.intent.action.MAIN" />
                            <category android:name="android.intent.category.LAUNCHER" />
                        </intent-filter>
                        <intent-filter>
                            <action android:name="com.novalauncher.THEME" />
                        </intent-filter>
                        <intent-filter>
                            <action android:name="org.adw.launcher.THEMES" />
                            <category android:name="android.intent.category.DEFAULT" />
                        </intent-filter>
                        <intent-filter>
                            <action android:name="org.adw.launcher.icons.ACTION_PICK_ICON" />
                            <category android:name="android.intent.category.DEFAULT" />
                        </intent-filter>
                        <intent-filter>
                            <action android:name="com.gau.go.launcherex.theme" />
                            <category android:name="android.intent.category.DEFAULT" />
                        </intent-filter>
                        <intent-filter>
                            <action android:name="android.intent.action.MAIN" />
                            <category android:name="com.fede.launcher.THEME_ICONPACK" />
                        </intent-filter>
                        <intent-filter>
                            <action android:name="com.anddoes.launcher.THEME" />
                        </intent-filter>
                        <intent-filter>
                            <action android:name="com.tsf.shell.themes" />
                            <category android:name="android.intent.category.DEFAULT" />
                        </intent-filter>
                        <intent-filter>
                            <action android:name="com.teslacoilsw.launcher.THEME" />
                        </intent-filter>
                        <intent-filter>
                            <action android:name="com.apex.launcher.THEMES" />
                            <category android:name="android.intent.category.DEFAULT" />
                        </intent-filter>
                        <intent-filter>
                            <action android:name="com.dlto.atom.launcher.THEME" />
                        </intent-filter>
                        <intent-filter>
                            <action android:name="android.intent.action.MAIN" />
                            <category android:name="com.novalauncher.category.CUSTOM_ICON_PICKER" />
                        </intent-filter>
                        <intent-filter>
                            <action android:name="app.lawnchair.icons.THEMED_ICON" />
                        </intent-filter>
                        <intent-filter>
                            <action android:name="ch.deletescape.lawnchair.THEME" />
                        </intent-filter>
                        <intent-filter>
                            <action android:name="com.dlto.atom.launcher.icons.THEMED_ICON" />
                        </intent-filter>
                    </activity>
                </application>
            </manifest>
        """.trimIndent()
    }

    fun buildStringsXml(apkInfo: ApkInfo): String {
        val label = escapeXml(apkInfo.label.ifBlank { "Icon Pack" })
        return """
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
                <string name="app_name">$label</string>
            </resources>
        """.trimIndent()
    }

    private fun escapeXml(value: String): String = buildString(value.length) {
        value.forEach { char ->
            when (char) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> append(char)
            }
        }
    }
}
