package com.bocchi.iconeditor

import com.bocchi.iconeditor.data.ArchiveService
import com.bocchi.iconeditor.data.ThemePackAssets
import com.bocchi.iconeditor.data.sync.ProjectSyncPackager
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectSyncSpecialAssetsTest {
    @Test
    fun metadataBundleReplacesManagedSpecialAssetsAndMaskConfig() {
        val root = Files.createTempDirectory("iconeditor-sync-special-assets").toFile()
        val sourceProject = File(root, "source-project").apply { mkdirs() }
        val sourceWork = File(sourceProject, "work")
        ArchiveService.createDefaultWorkspace(sourceWork)
        val sourceMask = File(sourceWork, ThemePackAssets.MtzMaskLayer.Mask.relativePath).apply {
            writeBytes(byteArrayOf(1, 2, 3))
        }
        val sourceWifi = File(sourceWork, ThemePackAssets.SpecialIcon.WifiOff.relativePath).apply {
            writeBytes(byteArrayOf(4, 5, 6))
        }
        ArchiveService.syncIconMaskTransformConfig(sourceWork)

        val targetProject = File(root, "target-project").apply { mkdirs() }
        val targetWork = File(targetProject, "work")
        ArchiveService.createDefaultWorkspace(targetWork)
        val staleWifi = File(targetWork, ThemePackAssets.SpecialIcon.WifiOn.relativePath).apply {
            writeBytes(byteArrayOf(9))
        }

        ProjectSyncPackager.applyMetaBytes(
            data = ProjectSyncPackager.packMetaBytes(sourceProject, sourceWork),
            projectDir = targetProject,
            workDir = targetWork,
        )

        assertArrayEquals(
            sourceMask.readBytes(),
            File(targetWork, ThemePackAssets.MtzMaskLayer.Mask.relativePath).readBytes(),
        )
        assertArrayEquals(
            sourceWifi.readBytes(),
            File(targetWork, ThemePackAssets.SpecialIcon.WifiOff.relativePath).readBytes(),
        )
        assertFalse(staleWifi.exists())
        val transformConfig = File(targetWork, ThemePackAssets.TRANSFORM_CONFIG_PATH).readText()
        assertTrue(transformConfig.contains("<PointsMapping>"))
        assertFalse(transformConfig.contains("name=\"ConfigIconMask\""))
    }
}
