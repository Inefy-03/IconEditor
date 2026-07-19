<p align="left">
  <a href="README.en.md">English</a>
</p>

<p align="center">
  <img src="app/src/main/ic_launcher-playstore.png" alt="IconEditor 应用图标" width="128">
</p>

<h1 align="center">IconEditor</h1>

<p align="center">
  一个 MTZ、Module 与 APK 图标包编辑器
</p>

IconEditor 采用 [Miuix](https://github.com/compose-miuix-ui/miuix) 设计，用于创建、导入、编辑和导出图标包项目，支持 MIUI/HyperOS `.mtz` 主题、Module `.zip` 图标包，以及第三方桌面使用的标准图标包 `.apk`。

## 主要功能

- 导入和编辑 MTZ 主题、Module 图标包、标准图标包 APK
- 管理 MTZ / Module / APK 三类项目信息
- 添加、替换和切换图标
- 导出 MTZ、Module Zip、APK
- 默认导出到系统「下载」目录
- 支持简体中文和英文

### 支持的格式

| 格式 | 导入 | 导出 |
| --- | --- | --- |
| MTZ 主题（`.mtz`） | 支持 | 支持 |
| Module 图标包（`.zip`） | 支持 | 支持 |
| 图标包 APK（`.apk`） | 支持 | 支持 |

Module 压缩包必须包含顶层 `icons` 文件。

### APK 导出说明

- 打包方式参考 CandyBar / IconPackSupporter 等开源图标包结构：`res/drawable-nodpi`、`res/xml`、`assets`、含主题 intent-filter 的 Manifest。
- 运行时使用内置工具链（`aapt2` + `android.jar`），在手机上直接编译，不依赖电脑。
- APK 格式用于 **Nova、Lawnchair** 等第三方桌面选图标包；**HyperOS 主题混搭请继续导出 MTZ**，不要指望安装 APK 图标包即可替换系统图标。
- 包名须为合法 Android 包名（至少两段，如 `com.glossy.iconpack`）；非法单段名会在导出时自动规范为 `com.iconeditor.xxx`。

## 项目状态

项目仍在持续完善中，欢迎提交问题和建议。
