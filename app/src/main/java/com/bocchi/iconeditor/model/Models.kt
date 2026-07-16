package com.bocchi.iconeditor.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

@Serializable(with = SourceTypeSerializer::class)
enum class SourceType { Universal, Mtz, Module, Apk }

object SourceTypeSerializer : KSerializer<SourceType> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("SourceType", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: SourceType) = encoder.encodeString(value.name)

    override fun deserialize(decoder: Decoder): SourceType = when (val value = decoder.decodeString()) {
        "Magisk" -> SourceType.Module
        else -> SourceType.valueOf(value)
    }
}

@Serializable
enum class ExportFormat { Mtz, ModuleZip, Apk }

@Serializable
enum class ThemeMode { System, Light, Dark }

@Serializable
enum class SortField { AppName, PackageName }

@Serializable
enum class ProjectSortField { CreatedAt, UpdatedAt, Name }

enum class InfoTab { Mtz, Module, Apk }

enum class ImportPhase { Copying, Extracting, ParsingIcons, Finishing }

enum class ExportPhase { Preparing, PackagingIcons, WritingArchive, Signing, Finishing }

data class ImportProgress(
    val phase: ImportPhase,
    val current: Int = 0,
    val total: Int = 0,
) {
    val fraction: Float
        get() = when (phase) {
            ImportPhase.Copying -> 0.05f
            ImportPhase.Extracting -> 0.15f
            ImportPhase.ParsingIcons -> {
                if (total <= 0) 0.2f
                else 0.15f + 0.8f * (current.toFloat() / total.toFloat())
            }
            ImportPhase.Finishing -> 0.98f
        }
}

data class ExportProgress(
    val phase: ExportPhase,
    val current: Int = 0,
    val total: Int = 0,
    val detail: String = "",
    val logs: List<String> = emptyList(),
    val finished: Boolean = false,
    val success: Boolean = false,
) {
    val fraction: Float
        get() = when {
            finished -> 1f
            phase == ExportPhase.Preparing -> 0.08f
            phase == ExportPhase.PackagingIcons -> {
                if (total <= 0) 0.25f
                else 0.08f + 0.62f * (current.toFloat() / total.toFloat())
            }
            phase == ExportPhase.WritingArchive -> 0.78f
            phase == ExportPhase.Signing -> 0.9f
            phase == ExportPhase.Finishing -> 0.98f
            else -> 0f
        }
}

/** Accepts integer or floating epoch millis (Mac may historically emit Doubles). */
object EpochMillisSerializer : KSerializer<Long> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("EpochMillis", PrimitiveKind.LONG)

    override fun serialize(encoder: Encoder, value: Long) = encoder.encodeLong(value)

    override fun deserialize(decoder: Decoder): Long {
        val json = decoder as? JsonDecoder
            ?: return decoder.decodeLong()
        val primitive = json.decodeJsonElement() as? JsonPrimitive
            ?: error("Expected JSON number for epoch millis")
        return primitive.longOrNull
            ?: primitive.doubleOrNull?.toLong()
            ?: error("Invalid epoch millis: ${primitive.content}")
    }
}

@Serializable
data class ProjectSummary(
    val id: String,
    val name: String,
    val sourceType: SourceType = SourceType.Universal,
    val sourceFileName: String = "",
    @Serializable(with = EpochMillisSerializer::class)
    val createdAt: Long = System.currentTimeMillis(),
    @Serializable(with = EpochMillisSerializer::class)
    val updatedAt: Long = System.currentTimeMillis(),
    val dirty: Boolean = false,
)

@Serializable
data class MtzInfo(
    val version: String = "",
    val author: String = "",
    val designer: String = "",
    val title: String = "",
    val description: String = "",
    val uiVersion: String = "",
)

@Serializable
data class ModuleInfo(
    val id: String = "",
    val name: String = "",
    val author: String = "",
    val description: String = "",
    val version: String = "",
    val theme: String = "",
    val themeId: String = "",
    val installMessages: List<String> = emptyList(),
)

@Serializable
data class ApkInfo(
    val packageName: String = "",
    val versionName: String = "1.0",
    val versionCode: Int = 1,
    val label: String = "",
    val author: String = "",
)

@Serializable
data class ProjectMetadata(
    val mtz: MtzInfo = MtzInfo(),
    @OptIn(ExperimentalSerializationApi::class)
    @JsonNames("magisk")
    val module: ModuleInfo = ModuleInfo(),
    val apk: ApkInfo = ApkInfo(),
)

@Serializable
data class IconMappingEntry(
    val packageName: String,
    val drawableName: String,
    val components: List<String> = emptyList(),
    val resourceZipPath: String? = null,
    val aliasPackageNames: List<String> = emptyList(),
)

@Serializable
data class IconMappingIndex(
    val entries: List<IconMappingEntry> = emptyList(),
)

enum class IconImportMode {
    Overwrite,
    AddOnly,
}

data class IconImportCandidate(
    val packageName: String,
    val appName: String,
    val iconArchivePath: String,
    val conflict: Boolean,
    val selected: Boolean = true,
)

data class IconImportPreview(
    val stagingId: String,
    val sourceType: SourceType,
    val displayName: String,
    val items: List<IconImportCandidate>,
) {
    val totalIncoming: Int get() = items.size
    val conflictCount: Int get() = items.count { it.conflict }
    val newCount: Int get() = items.count { !it.conflict }
    val selectedCount: Int get() = items.count { it.selected }
    val selectedConflictCount: Int get() = items.count { it.selected && it.conflict }
    val selectedNewCount: Int get() = items.count { it.selected && !it.conflict }
}

data class MaskLayerImportCandidate(
    val layerName: String,
    val found: Boolean,
    val conflict: Boolean,
    val selected: Boolean = true,
)

data class MaskLayerImportPreview(
    val stagingId: String,
    val sourceType: SourceType,
    val displayName: String,
    val items: List<MaskLayerImportCandidate>,
) {
    val foundCount: Int get() = items.count { it.found }
    val conflictCount: Int get() = items.count { it.found && it.conflict }
    val selectedCount: Int get() = items.count { it.selected && it.found }
}

@Serializable
data class IconPreferences(
    val search: String = "",
    val sortField: SortField = SortField.AppName,
    val descending: Boolean = false,
    val showLocalApps: Boolean = false,
    val showSystemApps: Boolean = false,
    val onlyShowMultipleStyles: Boolean = false,
    val onlyShowUnadaptedIcons: Boolean = false,
    val selectedVariants: Map<String, String> = emptyMap(),
    val customAppNames: Map<String, String> = emptyMap(),
)

@Serializable
data class AppSettings(
    val floatingBottomBar: Boolean = false,
    val liquidGlass: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.System,
    val blurEnabled: Boolean = true,
    val predictiveBackEnabled: Boolean = false,
    val projectSortField: ProjectSortField = ProjectSortField.CreatedAt,
    val exportDirectoryUri: String = "",
    val syncPeerHost: String = "",
    val syncPeerPort: String = "18765",
    val syncPeerToken: String = "",
    /** Stable host token so peers don't need to re-pair after restart. */
    val syncServerToken: String = "",
    /** Auto-start LAN sync host on launch (set when user enables / after pairing). */
    val syncServerWanted: Boolean = false,
)

@Serializable
data class IconAsset(
    val packageName: String,
    val variantKey: String,
    val archivePath: String,
    val fileName: String,
    val width: Int = 0,
    val height: Int = 0,
    val lastModified: Long = 0L,
    val edited: Boolean = false,
)

data class LocalAppInfo(
    val packageName: String,
    val appName: String,
    val system: Boolean,
)

data class IconListItem(
    val packageName: String,
    val appName: String,
    val variants: List<IconAsset>,
    val selected: IconAsset?,
    val localApp: LocalAppInfo?,
    val adapted: Boolean,
    val aliasPackageNames: List<String> = emptyList(),
)

@Serializable
data class ProjectIndex(
    val projects: List<ProjectSummary> = emptyList(),
)

@Serializable
data class TrashEntry(
    val project: ProjectSummary,
    @Serializable(with = EpochMillisSerializer::class)
    val deletedAt: Long = System.currentTimeMillis(),
)

@Serializable
data class TrashIndex(
    val entries: List<TrashEntry> = emptyList(),
)

@Serializable
data class IconUndoMeta(
    val projectId: String = "",
    val packageName: String = "",
    val label: String = "",
    val selectedVariant: String? = null,
    val customAppName: String? = null,
    val mappingEntry: IconMappingEntry? = null,
    val archivePaths: List<String> = emptyList(),
)
