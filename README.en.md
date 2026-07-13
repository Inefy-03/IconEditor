<p align="center">
  <img src="app/src/main/ic_launcher-playstore.png" alt="IconEditor app icon" width="128">
</p>

<h1 align="center">IconEditor</h1>

<p align="center">
  An MTZ, Module, and APK icon package editor
</p>

This repository continues development from upstream [Inefy-03/IconEditor](https://github.com/Inefy-03/IconEditor) (fork: [mrhhcc/IconEditor](https://github.com/mrhhcc/IconEditor)).

IconEditor uses [Miuix](https://github.com/compose-miuix-ui/miuix) to create, import, edit, and export icon-pack projects for MIUI/HyperOS `.mtz` themes, Module `.zip` packages, and standard icon-pack `.apk` files used by third-party launchers.

## Features

- Import and edit MTZ themes, Module icon packages, and standard icon-pack APKs
- Manage MTZ / Module / APK project metadata
- Add, replace, and switch icons
- Export MTZ, Module Zip, and APK
- Export progress with detailed logs (close manually when finished)
- Default export to the system Downloads folder
- Support Simplified Chinese and English

### Supported formats

| Format | Import | Export |
| --- | --- | --- |
| MTZ theme (`.mtz`) | Yes | Yes |
| Module icon package (`.zip`) | Yes | Yes |
| Icon-pack APK (`.apk`) | Yes | Yes |

Module packages must contain a top-level `icons` file.

## Differences from the original project

The upstream project focuses on **MTZ + Module**. This fork keeps those features and adds a full **APK icon-pack** workflow plus export UX improvements.

| Area | Upstream (Inefy-03) | This fork (mrhhcc) |
| --- | --- | --- |
| Import | MTZ, Module | MTZ, Module, **APK icon packs** |
| Export | MTZ, Module Zip | MTZ, Module Zip, **APK icon packs** |
| APK packaging | — | On-device **aapt2 compile/link** + apksig signing |
| Mapping | Mostly package-named files | **`appfilter.xml` / `drawable.xml`** (both `res` and `assets` for launcher compatibility) |
| Package name checks | Lenient | aapt2-compatible (e.g. `com.example.iconpack`) |
| Export UI | Basic toast/dialog | Progress, live logs, **manual close** when done |
| Export location | Picker / user choice | Defaults to system **Download** with correct MediaStore finalization |

### APK export notes

- Packaging follows open-source icon-pack layouts (CandyBar / IconPackSupporter style): `res/drawable-nodpi`, `res/xml`, `assets`, and theme intent-filters in the Manifest.
- The app bundles an export toolchain (`aapt2` + `android.jar`) and compiles on the device—no PC required.
- APK icon packs are for third-party launchers such as **Nova** and **Lawnchair**. For **HyperOS theme mixing**, keep exporting **MTZ**; installing an icon-pack APK will not replace system icons.
- Package names must be valid Android IDs (at least two segments, e.g. `com.glossy.iconpack`). Invalid single-segment names are normalized on export (e.g. `com.iconeditor.xxx`).

### Build notes

The first build runs `scripts/fetch_export_toolchain.sh` to prepare aapt2 and `android-35.jar`. You can also run that script manually.

## Project status

IconEditor is still under active development. Feedback and suggestions are welcome.
