package com.bocchi.iconeditor.data.packagename

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

@Entity(tableName = "package_names")
data class PackageNameEntity(
    @PrimaryKey
    @ColumnInfo(name = "package_name")
    val packageName: String,
    @ColumnInfo(name = "app_name")
    val appName: String,
)

@Entity(tableName = "package_name_metadata")
data class PackageNameMetadataEntity(
    @PrimaryKey
    val id: Int,
    @ColumnInfo(name = "data_hash")
    val dataHash: String,
    @ColumnInfo(name = "entry_count")
    val entryCount: Int,
)

@Dao
interface PackageNameDao {
    @Query("SELECT package_name, app_name FROM package_names")
    suspend fun loadAll(): List<PackageNameEntity>

    @Query("SELECT id, data_hash, entry_count FROM package_name_metadata WHERE id = 1")
    suspend fun loadMetadata(): PackageNameMetadataEntity?
}

@Database(
    entities = [PackageNameEntity::class, PackageNameMetadataEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class PackageNameDatabase : RoomDatabase() {
    abstract fun packageNameDao(): PackageNameDao
}

internal object PackageNameDatabaseProvider {
    const val DATABASE_ASSET_PATH = "databases/package_names.db"
    const val DATA_HASH_ASSET_PATH = "databases/package_names.sha256"
    private const val DATABASE_NAME = "package_names.db"

    @Volatile
    private var instance: PackageNameDatabase? = null

    fun get(context: Context): PackageNameDatabase {
        return instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                PackageNameDatabase::class.java,
                DATABASE_NAME,
            ).createFromAsset(DATABASE_ASSET_PATH)
                .build()
                .also { instance = it }
        }
    }

    @Synchronized
    fun replaceFromAsset(context: Context) {
        val applicationContext = context.applicationContext
        instance?.close()
        instance = null
        applicationContext.deleteDatabase(DATABASE_NAME)
        check(!applicationContext.getDatabasePath(DATABASE_NAME).exists()) {
            "Unable to replace package name database"
        }
    }
}
