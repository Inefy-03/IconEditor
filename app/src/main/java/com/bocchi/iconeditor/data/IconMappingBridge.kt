package com.bocchi.iconeditor.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.bocchi.iconeditor.model.IconAsset
import com.bocchi.iconeditor.model.IconMappingEntry
import com.bocchi.iconeditor.model.IconMappingIndex
import java.io.File
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.InputStream
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

data class AppfilterItem(
    val component: String,
    val drawable: String,
)

data class DedupedMapping(
    val packageName: String,
    val drawableName: String,
    val components: List<String>,
)

object IconMappingBridge {
    const val DEFAULT_ICON_PACK_SCALE = "1"

    private val componentRegex = Regex(
        """ComponentInfo\{([^/}]+)/([^}]+)\}""",
    )
    private val variantSuffixRegex = Regex("""_\d+$""")
    private val androidPackageRegex = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$")

    fun isValidAndroidPackageName(packageName: String): Boolean {
        return packageName.matches(androidPackageRegex)
    }

    fun packageToDrawableName(packageName: String): String {
        val sanitized = packageName.replace('.', '_').replace(Regex("[^a-zA-Z0-9_]"), "_")
        return if (sanitized.firstOrNull()?.isDigit() == true) "_$sanitized" else sanitized
    }

    fun inferPackageName(iconFileName: String): String {
        return iconFileName.replace(variantSuffixRegex, "")
    }

    fun normalizeAliasPackageNames(aliases: List<String>, primary: String): List<String> {
        val seen = linkedSetOf(primary)
        val result = mutableListOf<String>()
        for (raw in aliases) {
            val trimmed = raw.trim()
            if (trimmed.isEmpty() || trimmed in seen) continue
            seen += trimmed
            result += trimmed
        }
        return result.sorted()
    }

    fun appfilterItems(forMapping: IconMappingEntry): List<Pair<String, String>> {
        if (forMapping.drawableName.isBlank()) return emptyList()
        val items = forMapping.components.map { it to forMapping.drawableName }.toMutableList()
        val existing = items.map { it.first }.toMutableSet()
        for (alias in normalizeAliasPackageNames(forMapping.aliasPackageNames, forMapping.packageName)) {
            val component = fallbackComponent(alias)
            if (component !in existing) {
                items += component to forMapping.drawableName
                existing += component
            }
        }
        return items
    }

    fun fallbackComponent(packageName: String): String {
        return "ComponentInfo{$packageName/$packageName}"
    }

    fun isPackageDerivedDrawableName(drawableName: String, packageName: String): Boolean {
        if (drawableName.isBlank()) return true
        if (drawableName == packageName) return true
        if (drawableName == packageToDrawableName(packageName)) return true
        if (drawableName == packageName.replace('.', '_')) return true
        return false
    }

    fun generateApkDrawableName(packageName: String, used: MutableSet<String>): String {
        var candidate = packageToDrawableName(packageName).ifBlank { "icon" }
        if (candidate.firstOrNull()?.isDigit() == true) {
            candidate = "_$candidate"
        }
        var name = candidate
        var suffix = 0
        while (name in used) {
            suffix++
            name = "${candidate}_$suffix"
        }
        used += name
        return name
    }

    /** 旧版导出用的短哈希名（如 i3a5f2b1c），导出时应改回可读包名资源名。 */
    fun isLegacyHashedDrawableName(drawableName: String): Boolean {
        return drawableName.matches(Regex("""^i[0-9a-f]{8}(_\d+)?$"""))
    }

    fun prepareApkExportMapping(
        context: Context,
        icons: List<IconAsset>,
        existing: IconMappingIndex = IconMappingIndex(),
    ): IconMappingIndex {
        if (icons.isEmpty()) return existing
        val pm = context.packageManager
        val merged = if (existing.entries.isEmpty()) {
            buildDefaultMappings(context, icons)
        } else {
            mergeMappingsWithIcons(existing, icons, pm)
        }
        val usedDrawables = ApkPackAssets.MaskLayer.resourceNames.toMutableSet()
        return IconMappingIndex(
            entries = merged.entries.map { entry ->
                val drawableName = resolveApkDrawableName(entry.packageName, entry.drawableName, usedDrawables)
                entry.copy(drawableName = drawableName)
            },
        )
    }

    private fun resolveApkDrawableName(
        packageName: String,
        existingDrawable: String,
        used: MutableSet<String>,
    ): String {
        if (
            existingDrawable.isNotBlank() &&
            !isPackageDerivedDrawableName(existingDrawable, packageName) &&
            !isLegacyHashedDrawableName(existingDrawable)
        ) {
            used += existingDrawable
            return existingDrawable
        }
        return generateApkDrawableName(packageName, used)
    }

    fun resolveIconFile(workDir: File, entry: IconMappingEntry, icons: List<IconAsset>): File? {
        val iconRoot = File(workDir, "icons/res/drawable-xxhdpi")
        val candidates = buildList {
            icons.filter { it.packageName == entry.packageName || inferPackageName(it.variantKey) == entry.packageName }
                .sortedBy { if (it.variantKey.removeSuffixRegex() == entry.packageName) 0 else 1 }
                .forEach { add(File(workDir, it.archivePath)) }
            add(File(iconRoot, "${entry.packageName}.png"))
            add(File(iconRoot, "${entry.drawableName}.png"))
        }
        return candidates.firstOrNull { it.isFile }
    }

    private fun String.removeSuffixRegex(): String = replace(variantSuffixRegex, "")

    fun parseComponentInfo(raw: String): Pair<String, String>? {
        val match = componentRegex.find(raw.trim()) ?: return null
        val packageName = match.groupValues[1]
        val activity = match.groupValues[2]
        val normalized = if (activity.startsWith(".")) {
            "$packageName$activity"
        } else {
            activity
        }
        return packageName to "ComponentInfo{$packageName/$normalized}"
    }

    fun parseAppfilterXml(xml: String): List<AppfilterItem> {
        if (xml.isBlank()) return emptyList()
        val doc = parseXml(xml.byteInputStream())
        val items = mutableListOf<AppfilterItem>()
        val nodes = doc.getElementsByTagName("item")
        repeat(nodes.length) { index ->
            val node = nodes.item(index) as? Element ?: return@repeat
            val component = node.getAttribute("component").trim()
            val drawable = node.getAttribute("drawable").trim()
            if (component.isNotBlank() && drawable.isNotBlank()) {
                items += AppfilterItem(component = component, drawable = drawable)
            }
        }
        return items
    }

    fun dedupeMappings(items: List<AppfilterItem>): List<DedupedMapping> {
        return items.mapNotNull { item ->
            val packageName = parseComponentInfo(item.component)?.first ?: return@mapNotNull null
            Triple(packageName, item.drawable, item.component)
        }.groupBy { it.first }
            .map { (packageName, group) ->
                val drawableVotes = group.groupingBy { it.second }.eachCount()
                val drawableName = drawableVotes.maxWithOrNull(
                    compareBy<Map.Entry<String, Int>> { it.value }.thenBy { it.key },
                )?.key ?: group.first().second
                DedupedMapping(
                    packageName = packageName,
                    drawableName = drawableName,
                    components = group.map { it.third }.distinct(),
                )
            }
            .sortedBy { it.packageName }
    }

    fun buildDefaultMappings(
        context: Context,
        icons: List<IconAsset>,
    ): IconMappingIndex {
        val packageNames = icons.map { inferPackageName(it.variantKey) }.distinct().sorted()
        val pm = context.packageManager
        return IconMappingIndex(
            entries = packageNames.map { packageName ->
                val component = resolveLauncherComponent(pm, packageName) ?: fallbackComponent(packageName)
                IconMappingEntry(
                    packageName = packageName,
                    drawableName = "",
                    components = listOf(component),
                )
            },
        )
    }

    fun mergeMappingsWithIcons(
        existing: IconMappingIndex,
        icons: List<IconAsset>,
        pm: PackageManager,
    ): IconMappingIndex {
        val existingByPackage = existing.entries.associateBy { it.packageName }
        val packageNames = icons.map { inferPackageName(it.variantKey) }.distinct().sorted()
        return IconMappingIndex(
            entries = packageNames.map { packageName ->
                val previous = existingByPackage[packageName]
                IconMappingEntry(
                    packageName = packageName,
                    drawableName = previous?.drawableName.orEmpty(),
                    components = previous?.components?.takeIf { it.isNotEmpty() }
                        ?: listOf(
                            resolveLauncherComponent(pm, packageName) ?: fallbackComponent(packageName),
                        ),
                    resourceZipPath = previous?.resourceZipPath,
                    aliasPackageNames = previous?.aliasPackageNames.orEmpty(),
                )
            },
        )
    }

    /**
     * Prefer mapping details from an incoming pack (especially APK drawable names / components)
     * for packages that were actually imported, then keep other existing entries untouched.
     */
    fun preferIncomingMappings(
        existing: IconMappingIndex,
        incoming: IconMappingIndex,
        packages: Set<String>,
    ): IconMappingIndex {
        if (packages.isEmpty()) return existing
        val merged = existing.entries.associateBy { it.packageName }.toMutableMap()
        val incomingByPackage = incoming.entries.associateBy { it.packageName }
        for (packageName in packages) {
            val next = incomingByPackage[packageName] ?: continue
            val previous = merged[packageName]
            merged[packageName] = IconMappingEntry(
                packageName = packageName,
                drawableName = next.drawableName.ifBlank { previous?.drawableName.orEmpty() },
                components = next.components.takeIf { it.isNotEmpty() }
                    ?: previous?.components.orEmpty(),
                resourceZipPath = next.resourceZipPath ?: previous?.resourceZipPath,
                aliasPackageNames = next.aliasPackageNames.ifEmpty {
                    previous?.aliasPackageNames.orEmpty()
                },
            )
        }
        return IconMappingIndex(entries = merged.values.sortedBy { it.packageName })
    }

    fun buildAppfilterXml(
        mappings: List<IconMappingEntry>,
        maskLayerDrawables: Collection<String> = emptyList(),
        scale: String? = DEFAULT_ICON_PACK_SCALE,
    ): String {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument()
        val root = doc.createElement("resources")
        doc.appendChild(root)
        // Nova / Apex / Lawnchair 标准（见 Example_NovaTheme）：
        //   <iconback img1="iconback" />
        //   <iconmask img1="iconmask" />
        //   <iconupon img1="iconupon" />
        //   <scale factor="1" />
        // 错误写法 <item component="iconback" drawable="..."/> 不会被识别。
        val layers = maskLayerDrawables.map { it.trim() }.filter { it.isNotBlank() }.toSet()
        listOf("iconback", "iconmask", "iconupon").forEach { tag ->
            if (tag in layers) {
                val element = doc.createElement(tag)
                element.setAttribute("img1", tag)
                root.appendChild(element)
            }
        }
        if (layers.isNotEmpty() && !scale.isNullOrBlank()) {
            val scaleItem = doc.createElement("scale")
            scaleItem.setAttribute("factor", scale)
            root.appendChild(scaleItem)
        }
        mappings.forEach { mapping ->
            appfilterItems(mapping).forEach { (component, drawable) ->
                val item = doc.createElement("item")
                item.setAttribute("component", component)
                item.setAttribute("drawable", drawable)
                root.appendChild(item)
            }
        }
        return documentToString(doc)
    }

    fun buildDrawableXml(mappings: List<IconMappingEntry>): String {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument()
        val root = doc.createElement("resources")
        doc.appendChild(root)
        val version = doc.createElement("version")
        version.appendChild(doc.createTextNode("1"))
        root.appendChild(version)
        mappings.map { it.drawableName }.filter { it.isNotBlank() }.distinct().sorted().forEach { drawable ->
            val item = doc.createElement("item")
            item.setAttribute("drawable", drawable)
            root.appendChild(item)
        }
        return documentToString(doc)
    }

    fun toMappingIndex(deduped: List<DedupedMapping>, zipPaths: Map<Pair<String, String>, String> = emptyMap()): IconMappingIndex {
        return IconMappingIndex(
            entries = deduped.map { mapping ->
                IconMappingEntry(
                    packageName = mapping.packageName,
                    drawableName = mapping.drawableName,
                    components = mapping.components,
                    resourceZipPath = zipPaths[mapping.packageName to mapping.drawableName],
                )
            },
        )
    }

    private fun resolveLauncherComponent(pm: PackageManager, packageName: String): String? {
        if (!isValidAndroidPackageName(packageName)) return null
        return runCatching {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(packageName)
            val resolve = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).firstOrNull() ?: return null
            val activity = resolve.activityInfo ?: return null
            val activityName = if (activity.name.startsWith(".")) {
                "${activity.packageName}${activity.name}"
            } else {
                activity.name
            }
            "ComponentInfo{${activity.packageName}/$activityName}"
        }.getOrNull()
    }

    private fun parseXml(input: InputStream): Document {
        return DocumentBuilderFactory.newInstance().apply {
            isIgnoringComments = true
            isCoalescing = true
        }.newDocumentBuilder().parse(input)
    }

    private fun documentToString(doc: Document): String {
        val output = java.io.ByteArrayOutputStream()
        TransformerFactory.newInstance().newTransformer().apply {
            setOutputProperty(OutputKeys.INDENT, "no")
            setOutputProperty(OutputKeys.ENCODING, "UTF-8")
            setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no")
        }.transform(DOMSource(doc), StreamResult(output))
        return output.toString(Charsets.UTF_8.name())
    }
}
