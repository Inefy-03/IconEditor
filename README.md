<div align="center">

<img src="app/src/main/ic_launcher-playstore.png" alt="IconEditor 应用图标" width="128">

# IconEditor

**基于 [Miuix](https://github.com/compose-miuix-ui/miuix) 的 Android 图标包编辑器**

[![AGP](https://img.shields.io/badge/AGP-9.2.1-green?logo=gradle&logoColor=white)](https://developer.android.com/build)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.0-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![minSdk](https://img.shields.io/badge/minSdk-33-3DDC84?logo=android&logoColor=white)](#)
[![Miuix](https://img.shields.io/badge/Miuix-0.9.3-blue)](https://github.com/compose-miuix-ui/miuix)

[English](README.en.md)

</div>

---

## 简介

IconEditor 用于创建、导入、编辑和导出图标包项目，面向 MIUI / HyperOS `.mtz` 主题、Module `.zip` 图标包，以及 Nova、Lawnchair 等第三方桌面使用的标准图标包 `.apk`。

应用采用 Miuix + Jetpack Compose 构建，项目数据保存在本机私有目录中，导入导出流程尽量保留原包内未编辑内容。

## 特性

**项目与格式**

- 支持 MTZ 主题、Module 图标包、标准图标包 APK 的导入与导出
- 分别编辑 MTZ、Module、APK 项目信息
- 导入项目可通过系统文件选择器、文件管理器“打开方式”和系统分享进入
- Module 压缩包要求包含顶层 `icons` 文件

**图标编辑**

- 添加、替换、删除和切换图标样式
- 支持多样式图标、未适配图标、本地应用和系统应用筛选
- 支持新增图标与额外包名，并将额外包名写入独立图标文件
- 支持 APK 三层遮罩、MTZ / Module遮罩、桌面快捷功能图标等资源编辑
- 支持从外部图标包批量导入图标和遮罩资源

**导入导出**

- 导出 MTZ、Module ZIP、标准图标包 APK
- 同格式导出会保留导入包中未修改的顶层文件和嵌套 `icons` 内容
- APK 导出使用内置 `aapt2` 与 Android framework 资源在设备端编译
- 导出进度显示实时日志，导出完成后可查看完整日志

**同步与管理**

- 可与Mac端同步，Mac端软件：【 [IconEditor-macOS](https://github.com/hanchuan8/IconEditor-macOS-Releases) 】
- 支持局域网项目同步、二维码配对、差异对比和选择性推送 / 拉取
- 支持项目回收站，可恢复或彻底删除项目

## 支持格式

| 格式 | 导入 | 导出 | 说明 |
| --- | --- | --- | --- |
| MTZ 主题（`.mtz`） | 支持 | 支持 | 用于 MIUI / HyperOS 主题混搭 |
| Module 图标包（`.zip`） | 支持 | 支持 | 需要顶层 `icons` 文件 |
| 图标包 APK（`.apk`） | 支持 | 支持 | 用于 Nova、Lawnchair 等第三方桌面 |

## APK 导出说明

- 打包结构参考 CandyBar / IconPackSupporter 等开源图标包项目：`res/drawable-nodpi`、`res/xml`、`assets` 与主题 `intent-filter`。
- APK 导出在手机端使用内置工具链完成，不依赖电脑。
- APK 图标包适用于第三方桌面；HyperOS 主题混搭请继续导出 MTZ。
- APK 包名必须是合法 Android 包名，例如 `com.example.iconpack`；不合法的单段名称会在导出时自动规范化。

## 技术栈

- **语言**：Kotlin
- **界面**：Jetpack Compose + [Miuix](https://github.com/compose-miuix-ui/miuix) + Navigation3
- **数据**：Kotlin Serialization + Room
- **导入导出**：ZIP / XML 处理、设备端 `aapt2` 编译、APK 签名
- **同步**：局域网 HTTP 服务 + QR 配对
- **二维码**：ML Kit Barcode Scanning

## 构建

### 环境要求

- JDK 17 或更新版本
- Android SDK，`compileSdk 37`
export_toolchain.sh` 检查导出 APK 所需的 `aapt2` 与 Android framework 资源。

## 致谢

- [Miuix](https://github.com/compose-miuix-ui/miuix) - UI 组件与设计体系
- [ML Kit](https://developers.google.com/ml-kit/vision/barcode-scanning) - 二维码扫描
- CandyBar / IconPackSupporter 等图标包项目 - APK 图标包结构参考

## 项目状态

IconEditor 仍在持续开发中，欢迎提交问题和建议。
