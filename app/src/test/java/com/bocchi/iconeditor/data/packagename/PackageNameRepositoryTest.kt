package com.bocchi.iconeditor.data.packagename

import org.junit.Assert.assertEquals
import org.junit.Test

class PackageNameRepositoryTest {
    @Test
    fun localAppNameTakesPriority() {
        assertEquals(
            "Local name",
            resolvePackageDisplayName(
                packageName = "com.example.app",
                localAppName = "Local name",
                databaseAppName = "Database name",
            ),
        )
    }

    @Test
    fun databaseNameIsUsedWhenLocalNameIsBlank() {
        assertEquals(
            "Database name",
            resolvePackageDisplayName(
                packageName = "com.example.app",
                localAppName = " ",
                databaseAppName = "Database name",
            ),
        )
    }

    @Test
    fun packageNameIsUsedWhenNoNameExists() {
        assertEquals(
            "com.example.app",
            resolvePackageDisplayName(
                packageName = "com.example.app",
                localAppName = null,
                databaseAppName = "",
            ),
        )
    }
}
