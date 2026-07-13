package com.bocchi.iconeditor.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonNames

@Serializable(with = SourceTypeSerializer::class)
enum class SourceType { Universal, Mtz, Module }

object SourceTypeSerializer : KSerializer<SourceType> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("SourceType", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: SourceType) = encoder.encodeString(value.name)

    override fun deserialize(decoder: Decoder): SourceType = when (val value = decoder.decodeString()) {
        "Magisk" -> SourceType.Module
        else -> SourceType.valueOf(value)
    }
}

@Serializable
enum class ExportFormat { Mtz, ModuleZip }

@Serializable
enum class ThemeMode { System, Light, Dark }

@Serializable
enum class SortField { AppName, PackageName }

@Serializable
enum class ProjectSortField { CreatedAt, UpdatedAt, Name }

enum class InfoTab { Mtz, Module }

@Serializable
data class ProjectSummary(
    val id: String,
    val name: String,
    val sourceType: SourceType = SourceType.Universal,
    val sourceFileName: String = "",
    val createdAt: Long = System.currentTimeMillis(),
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
data class ProjectMetadata(
    val mtz: MtzInfo = MtzInfo(),
    @OptIn(ExperimentalSerializationApi::class)
    @JsonNames("magisk")
    val module: ModuleInfo = ModuleInfo(),
)

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
)

@Serializable
data class AppSettings(
    val floatingBottomBar: Boolean = false,
    val liquidGlass: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.System,
    val blurEnabled: Boolean = true,
    val predictiveBackEnabled: Boolean = false,
    val projectSortField: ProjectSortField = ProjectSortField.CreatedAt,
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
)

@Serializable
data class ProjectIndex(
    val projects: List<ProjectSummary> = emptyList(),
)
