package com.bocchi.iconeditor.data

object ThemePackAssets {
    const val TRANSFORM_CONFIG_PATH = "icons/transform_config.xml"
    private const val DRAWABLE_DIRECTORY = "icons/res/drawable-xxhdpi"

    sealed interface DrawableAsset {
        val resourceName: String

        val relativePath: String
            get() = "$DRAWABLE_DIRECTORY/$resourceName.png"
    }

    enum class MtzMaskLayer(override val resourceName: String) : DrawableAsset {
        Pattern("icon_pattern"),
        Mask("icon_mask"),
        Border("icon_border"),
    }

    enum class SpecialIcon(override val resourceName: String) : DrawableAsset {
        WifiOff("status_bar_toggle_wifi_off"),
        WifiOn("status_bar_toggle_wifi_on"),
        DataOff("status_bar_toggle_data_off"),
        DataOn("status_bar_toggle_data_on"),
        BluetoothOff("status_bar_toggle_bluetooth_off"),
        BluetoothOn("status_bar_toggle_bluetooth_on"),
        HotspotOff("status_bar_toggle_wifi_ap_off"),
        HotspotOn("status_bar_toggle_wifi_ap_on"),
        TorchOff("status_bar_toggle_torch_off"),
        TorchOn("status_bar_toggle_torch_on"),
        FlightModeOff("status_bar_toggle_flight_mode_off"),
        FlightModeOn("status_bar_toggle_flight_mode_on"),
        Lock("status_bar_toggle_lock"),
    }

    val drawableAssets: List<DrawableAsset> = MtzMaskLayer.entries + SpecialIcon.entries

    val reservedDrawableNames: Set<String> = buildSet {
        addAll(ApkPackAssets.MaskLayer.resourceNames)
        addAll(drawableAssets.map(DrawableAsset::resourceName))
    }

    val syncRelativePaths: List<String> =
        listOf(TRANSFORM_CONFIG_PATH) + drawableAssets.map(DrawableAsset::relativePath)
}
