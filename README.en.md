<div align="center">

<img src="app/src/main/ic_launcher-playstore.png" alt="IconEditor app icon" width="128">

# IconEditor

**An Android icon-pack editor built with [Miuix](https://github.com/compose-miuix-ui/miuix)**

[![AGP](https://img.shields.io/badge/AGP-9.2.1-green?logo=gradle&logoColor=white)](https://developer.android.com/build)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.0-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![minSdk](https://img.shields.io/badge/minSdk-33-3DDC84?logo=android&logoColor=white)](#)
[![Miuix](https://img.shields.io/badge/Miuix-0.9.3-blue)](https://github.com/compose-miuix-ui/miuix)

[简体中文](README.md)

</div>

---

## Overview

IconEditor creates, imports, edits, and exports icon-pack projects for MIUI / HyperOS `.mtz` themes, Module `.zip` packages, and standard icon-pack `.apk` files used by third-party launchers such as Nova and Lawnchair.

The app is built with Miuix and Jetpack Compose. Project data is stored in app-private storage, and import/export flows are designed to preserve unedited package content when the format allows it.

## Features

**Projects and formats**

- Import and export MTZ themes, Module icon packages, and standard icon-pack APKs
- Edit MTZ, Module, and APK metadata separately
- Import projects through the system file picker, file-manager Open with, and Android sharing
- Require Module packages to contain a top-level `icons` file

**Icon editing**

- Add, replace, delete, and switch icon variants
- Filter multiple-style icons, unadapted icons, local apps, and system apps
- Create new icons with extra package names, materialized as independent icon files
- Edit APK mask layers, MTZ / Module mask resources, and desktop shortcut assets
- Batch-import icons and mask assets from external icon packs

**Import and export**

- Export MTZ, Module ZIP, and standard icon-pack APK files
- Preserve unedited top-level entries and nested `icons` content during same-format exports
- Compile APK exports on-device with the bundled `aapt2` and Android framework resources
- Show live export logs and keep complete logs available after export

**Sync and management**

- Sync with the Mac app: [IconEditor-macOS](https://github.com/hanchuan8/IconEditor-macOS-Releases)
- Sync projects over LAN with QR pairing, diff review, and selective push / pull
- Restore or permanently delete projects from Trash

## Supported Formats

| Format | Import | Export | Notes |
| --- | --- | --- | --- |
| MTZ theme (`.mtz`) | Yes | Yes | For MIUI / HyperOS theme mixing |
| Module icon package (`.zip`) | Yes | Yes | Requires a top-level `icons` file |
| Icon-pack APK (`.apk`) | Yes | Yes | For third-party launchers such as Nova and Lawnchair |

## APK Export Notes

- Packaging follows common open-source icon-pack layouts such as CandyBar and IconPackSupporter: `res/drawable-nodpi`, `res/xml`, `assets`, and theme `intent-filter` entries.
- APK export runs on the phone with the bundled toolchain; no PC is required.
- APK icon packs are for third-party launchers. For HyperOS theme mixing, export MTZ instead.
- APK package names must be valid Android package IDs, such as `com.example.iconpack`; invalid single-segment names are normalized during export.

## Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose + [Miuix](https://github.com/compose-miuix-ui/miuix) + Navigation3
- **Data**: Kotlin Serialization + Room
- **Import/export**: ZIP / XML handling, on-device `aapt2` compilation, APK signing
- **Sync**: LAN HTTP service + QR pairing
- **QR scanning**: ML Kit Barcode Scanning

## Build

### Requirements

- JDK 17 or newer
- Android SDK with `compileSdk 37`

The first build checks the bundled APK-export `aapt2` and Android framework resources through `scripts/fetch_export_toolchain.sh`.

## Credits

- [Miuix](https://github.com/compose-miuix-ui/miuix) - UI components and design system
- [ML Kit](https://developers.google.com/ml-kit/vision/barcode-scanning) - QR scanning
- CandyBar / IconPackSupporter icon-pack projects - APK icon-pack layout references

## Project Status

IconEditor is still under active development. Feedback and suggestions are welcome.
