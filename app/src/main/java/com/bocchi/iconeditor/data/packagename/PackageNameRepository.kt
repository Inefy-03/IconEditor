package com.bocchi.iconeditor.data.packagename

import android.content.Context
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class PackageNameRepository(context: Context) {
    private val applicationContext = context.applicationContext
    private val loadMutex = Mutex()

    @Volatile
    private var loaded = false

    @Volatile
    private var appNames: Map<String, String> = emptyMap()

    suspend fun ensureLoaded() {
        if (loaded) return
        loadMutex.withLock {
            if (loaded) return

            val expectedDataHash = loadPackagedDataHash()
            var database = PackageNameDatabaseProvider.get(applicationContext)
            var metadata = database.packageNameDao().loadMetadata()
            if (metadata?.dataHash != expectedDataHash) {
                PackageNameDatabaseProvider.replaceFromAsset(applicationContext)
                database = PackageNameDatabaseProvider.get(applicationContext)
                metadata = database.packageNameDao().loadMetadata()
            }

            check(metadata?.dataHash == expectedDataHash) {
                "Package name database data hash does not match its packaged metadata"
            }
            val entries = database.packageNameDao().loadAll()
            check(entries.size == metadata.entryCount) {
                "Package name database entry count does not match its packaged metadata"
            }
            appNames = entries.associate { it.packageName to it.appName }
            loaded = true
        }
    }

    fun resolveAppName(packageName: String, localAppName: String?): String {
        return resolvePackageDisplayName(
            packageName = packageName,
            localAppName = localAppName,
            databaseAppName = appNames[packageName],
        )
    }

    private fun loadPackagedDataHash(): String {
        val dataHash = applicationContext.assets
            .open(PackageNameDatabaseProvider.DATA_HASH_ASSET_PATH)
            .bufferedReader()
            .use { it.readText().trim() }
        require(dataHash.matches(Regex("[0-9a-f]{64}"))) {
            "Invalid packaged package name database hash"
        }
        return dataHash
    }
}

internal fun resolvePackageDisplayName(
    packageName: String,
    localAppName: String?,
    databaseAppName: String?,
): String {
    return localAppName?.takeIf { it.isNotBlank() }
        ?: databaseAppName?.takeIf { it.isNotBlank() }
        ?: packageName
}
