<div align="center">

**English** | [简体中文](README_ZH_HANS.md)

<img src="logo.webp" width="96" alt="FlyCat logo">

# FlyCat

[![Latest release](https://img.shields.io/github/v/release/LM-Firefly/FlyCat?label=Release&logo=github)](https://github.com/LM-Firefly/FlyCat/releases/latest)
[![GitHub License](https://img.shields.io/github/license/LM-Firefly/FlyCat?logo=gnu)](/LICENSE)
![Downloads](https://img.shields.io/github/downloads/LM-Firefly/FlyCat/total)
[![Codacy Badge](https://app.codacy.com/project/badge/Grade/eb25acf5d27a4fe2af39cb94aeaff3b0)](https://app.codacy.com/gh/LM-Firefly/FlyCat/dashboard?utm_source=gh&utm_medium=referral&utm_content=&utm_campaign=Badge_grade)

**An open-source Android client based on the [mihomo](https://github.com/MetaCubeX/mihomo) kernel**

[Documentation](https://yumebox.gal.tf) · [Download](https://github.com/LM-Firefly/FlyCat/releases) · [Feedback](https://github.com/LM-Firefly/FlyCat/issues) · [Telegram Group](https://t.me/OOM_Group)

</div>

## Usage

FlyCat currently only supports **Android 8.0 (API 26) and above**.

- Download the installation package for your architecture from the [Release](https://github.com/LM-Firefly/FlyCat/releases) page
- For more information, visit the official documentation: [yumebox.gal.tf](https://yumebox.gal.tf)
- Override configuration syntax reference: [Override document](https://yumebox.gal.tf/override/override)

If this project is helpful to you, please give it a Star — it is the motivation for continuous updates.

### Feedback and suggestions

If you encounter a bug, or have ideas and suggestions for improvements, please submit them on the [Issues](https://github.com/LM-Firefly/FlyCat/issues) page.

For more discussion and feedback, join the group: [@OOM_WG](https://t.me/OOM_Group)

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

YumeBox does not use Git submodules. A clean checkout needs the mihomo source and generated assets before Gradle can build the app.

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
   # arm64-v8a debug APK without bundled Geo data (local default)
   ./gradlew :app:assembleDebug

   # arm64-v8a debug APK with Geo databases and BundleMRS.7z
   ./gradlew -Pgeo.bundle=true :app:assembleDebug

   # release APKs for every configured ABI plus a universal APK
   ./gradlew -Pbuild.allAbis=true -Pgeo.bundle=true :app:assembleRelease
   ```

   APKs are written to `app/build/outputs/apk/<build-type>/`. External builds omit Geo assets and `BundleMRS.7z`; mihomo downloads them when needed. On Windows, use `gradlew.bat`.
