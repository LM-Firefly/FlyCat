<div align="center">

**简体中文** | [English](README.md)

<img src="logo.webp" width="96" alt="FlyCat logo">

# FlyCat

[![Latest release](https://img.shields.io/github/v/release/LM-Firefly/FlyCat?label=Release&logo=github)](https://github.com/LM-Firefly/FlyCat/releases/latest)
[![GitHub License](https://img.shields.io/github/license/LM-Firefly/FlyCat?logo=gnu)](/LICENSE)
![Downloads](https://img.shields.io/github/downloads/LM-Firefly/FlyCat/total)
[![Codacy Badge](https://app.codacy.com/project/badge/Grade/eb25acf5d27a4fe2af39cb94aeaff3b0)](https://app.codacy.com/gh/LM-Firefly/FlyCat/dashboard?utm_source=gh&utm_medium=referral&utm_content=&utm_campaign=Badge_grade)

**一个基于 [mihomo](https://github.com/MetaCubeX/mihomo) 内核的开源 Android 客户端**

[官方文档](https://yumebox.gal.tf) · [下载](https://github.com/LM-Firefly/FlyCat/releases) · [反馈](https://github.com/LM-Firefly/FlyCat/issues) · [Telegram 群组](https://t.me/OOM_Group)

</div>

## 使用

FlyCat 目前仅支持 **Android 8.0（API 26）及以上系统**。

- 前往 [Release](https://github.com/LM-Firefly/FlyCat/releases) 页面下载对应架构的安装包
- 更多内容请访问官方文档：[yumebox.gal.tf](https://yumebox.gal.tf)
- 覆写配置语法参考：[覆写文档](https://yumebox.gal.tf/override/override)

如果这个项目对你有帮助，请点一个 Star，这是持续更新的动力。

### 反馈与建议

如果遇到 Bug，或有想法与改进建议，请在 [Issues](https://github.com/LM-Firefly/FlyCat/issues) 页面提交。

更多讨论与反馈可加入群组：[@OOM_WG](https://t.me/OOM_Group)

### 参与贡献

如果您希望让 FlyCat 变得更好，请参阅 [CONTRIBUTING](../CONTRIBUTING.md)。

如果希望将 FlyCat 翻译为更多语言，或改进现有翻译，请 Fork 本项目，并在 `locale/lang` 目录下创建或更新对应的翻译文件。

### 特别声明

> ~~作者对这个项目中的代码一无所知。代码处于可用或不可用状态，没有第三种情况。~~

本项目使用的第三方库见 [ThirdParty](ThirdParty.md)。

1. **图标版权**：FlyCat 应用图标及品牌标识的版权归项目所有者所有
2. **Fork 发行版限制**：
   - 发行版不得使用 FlyCat 项目名称
   - 发行版不得沿用 FlyCat 原始图标
   - 发行版不得包含 FlyCat Issue 反馈渠道

## 构建

FlyCat 不使用 Git submodule。从干净检出开始构建前，需要先准备 Mihomo 源码和生成资源。

1. 安装 **OpenJDK 24**、**Android SDK 37**、**NDK 30.0.14904198**、**CMake 3.22.1**、**Kotlin CLI**、**Go 1.26**、**Rust nightly**、Git 和 `patch`。

   ```bash
   sdkmanager "platforms;android-37" "ndk;30.0.14904198" "cmake;3.22.1"
   ```

2. 在项目根目录创建 `local.properties`：

   ```properties
   sdk.dir=/path/to/android-sdk
   # ndk.dir=/path/to/android-sdk/ndk/30.0.14904198
   ```

3. 拉取 Mihomo 源码，可选择 `alpha`、`meta` 或 `smart`。

   ```bash
   chmod +x scripts/sync-kernel.sh
   ./scripts/sync-kernel.sh alpha
   ```

4. 准备 Rust：

   ```bash
   rustup toolchain install nightly --component rust-src
   rustup target add --toolchain nightly \
     armv7-linux-androideabi \
     aarch64-linux-android \
     i686-linux-android \
     x86_64-linux-android
   cargo install cargo-ndk
   ```

5. 生成本地化源码、原生库和内置 Geo 资源：

   ```bash
   kotlin scripts/generate-locale.main.kts .
   kotlin scripts/native-build.main.kts --all
   ```

   使用 `--help` 可以单独构建 Go、Rust、C++ 或 Geo 资源。

6. 可选：为 Release 构建签名。将密钥库放置为项目根目录下的 `release.keystore`，然后创建 `signing.properties`：

   ```properties
   keystore.password=<密钥库密码>
   key.alias=<密钥别名>
   key.password=<密钥密码>
   ```

7. 构建 APK：

   ```bash
   # 本地默认：不内置 Geo 数据的 arm64-v8a Debug APK
   ./gradlew :app:assembleDebug

   # 内置 Geo 数据库与 BundleMRS.7z 的 arm64-v8a Debug APK
   ./gradlew -Pgeo.bundle=true :app:assembleDebug

   # 为所有已配置 ABI 构建 Release APK，并额外生成通用 APK
   ./gradlew -Pbuild.allAbis=true -Pgeo.bundle=true :app:assembleRelease
   ```

   APK 输出到 `app/build/outputs/apk/<build-type>/`。外置版本不包含 Geo 资源和 `BundleMRS.7z`，Mihomo 会按需下载。Windows 请使用 `gradlew.bat`。
