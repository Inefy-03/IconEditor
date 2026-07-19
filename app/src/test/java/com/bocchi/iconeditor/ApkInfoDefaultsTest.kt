package com.bocchi.iconeditor

import com.bocchi.iconeditor.data.ApkInfoDefaults
import com.bocchi.iconeditor.model.ApkInfo
import com.bocchi.iconeditor.model.ModuleInfo
import com.bocchi.iconeditor.model.MtzInfo
import com.bocchi.iconeditor.model.ProjectMetadata
import org.junit.Assert.assertEquals
import org.junit.Test

class ApkInfoDefaultsTest {
    @Test
    fun resolveFillsFromMtzWhenApkBlank() {
        val metadata = ProjectMetadata(
            mtz = MtzInfo(
                version = "2.0",
                author = "Alice",
                title = "My Theme",
            ),
            apk = ApkInfo(),
        )
        val resolved = ApkInfoDefaults.resolve("Fallback", metadata)
        assertEquals("My Theme", resolved.label)
        assertEquals("Alice", resolved.author)
        assertEquals("2.0", resolved.versionName)
        assertEquals("com.iconeditor.mytheme", resolved.packageName)
    }

    @Test
    fun resolveKeepsExistingApkFields() {
        val metadata = ProjectMetadata(
            mtz = MtzInfo(title = "Ignored"),
            apk = ApkInfo(
                packageName = "com.example.pack",
                label = "Custom Pack",
                versionName = "3.1",
                author = "Bob",
            ),
        )
        val resolved = ApkInfoDefaults.resolve("Project", metadata)
        assertEquals("com.example.pack", resolved.packageName)
        assertEquals("Custom Pack", resolved.label)
        assertEquals("3.1", resolved.versionName)
        assertEquals("Bob", resolved.author)
    }

    @Test
    fun sanitizePackageNameProducesValidIdentifier() {
        assertEquals("com.iconeditor.iconpack", ApkInfoDefaults.sanitizePackageName("!!!"))
        assertEquals("com.iconeditor.myiconpack", ApkInfoDefaults.sanitizePackageName("My Icon Pack"))
        assertEquals("com.iconeditor.glossyicon", ApkInfoDefaults.sanitizePackageName("glossyicon"))
        assertEquals("com.example.pack", ApkInfoDefaults.sanitizePackageName("com.example.pack"))
    }

    @Test
    fun resolveRewritesSingleSegmentPackageName() {
        val metadata = ProjectMetadata(
            apk = ApkInfo(packageName = "glossyicon", label = "Glossy"),
        )
        val resolved = ApkInfoDefaults.resolve("Project", metadata)
        assertEquals("com.iconeditor.glossyicon", resolved.packageName)
    }

    @Test
    fun bumpVersionIncrementsCodeAndName() {
        val bumped = ApkInfoDefaults.bumpVersion(
            ApkInfo(versionName = "1.0", versionCode = 1),
        )
        assertEquals(2, bumped.versionCode)
        assertEquals("1.1", bumped.versionName)
        assertEquals("1.0.10", ApkInfoDefaults.bumpVersionName("1.0.9"))
        assertEquals("v3", ApkInfoDefaults.bumpVersionName("v2"))
        assertEquals("beta.1", ApkInfoDefaults.bumpVersionName("beta"))
        assertEquals("1.1", ApkInfoDefaults.bumpVersionName(""))
    }
}
