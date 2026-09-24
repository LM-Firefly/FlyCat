<div align="center">

**English** | [简体中文](README_ZH_HANS.md)

<img src="logo.webp" width="96" alt="FlyCat logo">

# FlyCat

[![Latest release](https://img.shields.io/github/v/release/LM-Firefly/FlyCat?label=Release&logo=github)](https://github.com/LM-Firefly/FlyCat/releases/latest)
[![GitHub License](https://img.shields.io/github/license/LM-Firefly/FlyCat?logo=gnu)](/LICENSE)
![Downloads](https://img.shields.io/github/downloads/LM-Firefly/FlyCat/total)
[![Codacy Badge](https://app.codacy.com/project/badge/Grade/eb25acf5d27a4fe2af39cb94aeaff3b0)](https://app.codacy.com/gh/LM-Firefly/FlyCat/dashboard?utm_source=gh&utm_medium=referral&utm_content=&utm_campaign=Badge_grade)

**An open-source Android client based on the [mihomo](https://github.com/MetaCubeX/mihomo) kernel**

[Documentation](https://lm-firefly.github.io/FlyCat/) · [Download](https://github.com/LM-Firefly/FlyCat/releases) · [Feedback](https://github.com/LM-Firefly/FlyCat/issues)

</div>

## Features

- **Modular architecture**: Fully modularized into `core` / `ui` / `data` / `runtime:{api,client,service}` / 10 feature modules
- **Override system**: YAML + JavaScript override with field modifiers (`start`/`end`/`merge`/`force`), binding chains, and Rust-native compilation engine
- **Multiple kernel channels**: `alpha`, `meta`, `smart` — switchable via kernel manager with per-channel ABI support
- **Root TUN**: VPN-free traffic interception with automatic routing and DNS mode (RedirHost / FakeIP)
- **Moe Home**: Custom home page with wallpaper, proxy chain topology visualization, speed chart, and traffic display
- **Connection management**: Detailed connection view with speed display and one-tap close all
- **Traffic statistics**: Time-based stacked bar charts with per-app traffic ranking
- **Web dashboard**: Built-in MetaCubeXD / Zashboard / Yacd panel support
- **SubStore**: Optional SubStore integration for subscription management
- **Backup & restore**: WebDAV backup with Age encryption support
- **Multi-language**: English, 简体中文, 繁體中文, 日本語, Русский
- **Deep links**: `flycat://` scheme for direct navigation to pages and screens
- **WiFi automation**: Automatic profile switching based on WiFi SSID
- **Access control**: Per-app and per-domain proxy routing rules
- **Native layer**: Rust JNI engine (override compiler + JS runtime) + Go native (tunnel/config/proxy) + Rust lib.rs loader

## Usage

FlyCat currently only supports **Android 8.0 (API 26) and above**.

- Download the installation package for your architecture from the [Release](https://github.com/LM-Firefly/FlyCat/releases) page
- For more information, visit the official documentation: [lm-firefly.github.io/FlyCat](https://lm-firefly.github.io/FlyCat/)
- Override configuration syntax reference: [Override document](https://lm-firefly.github.io/FlyCat/override/override)

If this project is helpful to you, please give it a Star — it is the motivation for continuous updates.

### Feedback and suggestions

If you encounter a bug, or have ideas and suggestions for improvements, please submit them on the [Issues](https://github.com/LM-Firefly/FlyCat/issues) page.

### Contributing

If you want to make FlyCat better, please refer to [CONTRIBUTING](../CONTRIBUTING.md).

If you want to translate FlyCat into more languages, or improve the existing translations, fork this project and create or update the corresponding translation file in the `locale/lang` directory.

### Notices

> ~~The author knows nothing about the code in this project. The code is either available or unavailable, there is no third case.~~

See [ThirdParty](ThirdParty.md) for the third-party libraries used in this project.

1. **Icon copyright**: The copyright of the FlyCat application icon and brand assets belongs to the project owners.
2. **Fork release restrictions**:
   - Releases must not use the FlyCat project name.
   - Releases must not use the original FlyCat icon.
   - Releases must not include FlyCat's official issue feedback channels.

## Build

FlyCat does not use Git submodules. A clean checkout needs the mihomo source and generated assets before Gradle can build the app.

1. Install **OpenJDK 24**, **Android SDK 37**, **NDK 30.0.14904198**, **CMake 3.22.1**, **Kotlin CLI**, **Go 1.26**, **Rust nightly**, Git, and `patch`.

   ```bash
   sdkmanager "platforms;android-37" "ndk;30.0.14904198" "cmake;3.22.1"
   ```

2. Create `local.properties` in the project root:

   ```properties
   sdk.dir=/path/to/android-sdk
   # ndk.dir=/path/to/android-sdk/ndk/30.0.14904198
   ```

3. Fetch the mihomo source. Available channels are `alpha`, `meta`, and `smart`.

   ```bash
   chmod +x scripts/sync-kernel.sh
   ./scripts/sync-kernel.sh alpha
   ```

4. Prepare Rust:

   ```bash
   rustup toolchain install nightly --component rust-src
   rustup target add --toolchain nightly \
     armv7-linux-androideabi \
     aarch64-linux-android \
     i686-linux-android \
     x86_64-linux-android
   cargo install cargo-ndk
   ```

5. Generate locale sources, native libraries, and bundled Geo assets:

   ```bash
   kotlin scripts/generate-locale.main.kts .
   kotlin scripts/native-build.main.kts --all
   ```

   Run the native script with `--help` to build Go, Rust, C++, or Geo assets separately.

6. Optionally sign release builds. Place the keystore at `release.keystore` in the project root, then create `signing.properties`:

   ```properties
   keystore.password=<keystore password>
   key.alias=<key alias>
   key.password=<key password>
   ```

7. Build the APK:

   ```bash
   # arm64-v8a debug APK (local default)
   ./gradlew :app:assembleDebug

   # Release APKs for every configured ABI plus a universal APK
   ./gradlew -Pbuild.allAbis=true :app:assembleRelease

   # Release APK with extension (arm64-v8a and x86_64, includes Javet libs)
   ./gradlew assembleReleaseWithExtension
   ```

   APKs are written to `app/build/outputs/apk/<build-type>/`. Geo assets (geoip.metadb, geosite.dat, ASN.mmdb, BundleMRS.7z) are always bundled. On Windows, use `gradlew.bat`.
