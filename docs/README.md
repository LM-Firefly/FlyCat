<div align="center">



<img src="Yume.png" width="138" alt="YumeBox logo">

# YumeBox

[![Latest release](https://img.shields.io/github/v/release/YumeYucca/YumeBox?style=flat-square&label=Release&logo=github)](https://github.com/YumeYucca/YumeBox/releases/latest) [![GitHub License](https://img.shields.io/github/license/YumeYucca/YumeBox?style=flat-square&logo=gnu)](/LICENSE) ![Downloads](https://img.shields.io/github/downloads/YumeYucca/YumeBox/total?style=flat-square) [![Codacy Badge](https://app.codacy.com/project/badge/Grade/731d810dd0f6423bb61b7c140653bc32)](https://app.codacy.com/gh/YumeYucca/YumeBox/dashboard?utm_source=gh&utm_medium=referral&utm_content=&utm_campaign=Badge_grade)[![Quality gate status](https://sonarcloud.io/api/project_badges/measure?project=YumeYucca_YumeBox&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=YumeYucca_YumeBox)

**English** | [简体中文](README_ZH_HANS.md)

**An open-source Android client based on the [mihomo](https://github.com/MetaCubeX/mihomo) kernel**

[Documentation](https://yumebox.gal.tf) · [Download](https://github.com/YumeYucca/YumeBox/releases) · [Feedback](https://github.com/YumeYucca/YumeBox/issues) · [Telegram Group](https://t.me/OOM_Group)

</div>

## Usage

YumeBox currently only supports **Android 8.0 (API 26) and above**.

- Download the installation package for your architecture from
  the [Release](https://github.com/YumeYucca/YumeBox/releases) page
- For more information, visit the official documentation: [yumebox.gal.tf](https://yumebox.gal.tf)
- Override configuration syntax reference: [Override document](https://yumebox.gal.tf/override/override)

If this project is helpful to you, please give it a Star — it is the motivation for continuous updates.

### Feedback and suggestions

If you encounter a bug, or have ideas and suggestions for improvements, please submit them on
the [Issues](https://github.com/YumeYucca/YumeBox/issues) page.

For more discussion and feedback, join the group: [@OOM_WG](https://t.me/OOM_Group)

### Contributing

If you want to make YumeBox better, please refer to [CONTRIBUTING](../CONTRIBUTING.md).

If you want to translate YumeBox into more languages, or improve the existing translations, fork this project and create
or update the corresponding translation file in the `locale/lang` directory.

> [!TIP]
> YumeBox uses [FYTxt](https://shirosu.gal.tf/fyl) for localization,
> with [FVV](https://fvvlang.sbs/) used underneath to store multilingual text
>
> Please familiarize yourself with its syntax before contributing

### Notices

> ~~The author knows nothing about the code in this project. The code is either available or unavailable, there is no
third case.~~

See [ThirdParty](ThirdParty.md) for the third-party libraries used in this project.

1. **Icon copyright**: The copyright of the YumeBox application icon and brand assets belongs to the project owners.
2. **Fork release restrictions**:
    - Releases must not use the YumeBox **project name**.
    - Releases must not use the original YumeBox **icon**.
    - First-party content in releases must not include content related to the [OOM WG](https://oom-wg.dev/),
      including **domains** (such as `*.oom-wg.dev`, `*.gal.tf`), **channels**, or **groups**.

Forked releases must not use this content unless it is solely used to reasonably identify the source YumeBox project.

## Build

YumeBox does not use Git submodules. A clean checkout needs the mihomo source and generated assets before Gradle can
build the app.

1. Install **OpenJDK 24**, **Android SDK 37**, **NDK 30.0.14904198**, **CMake 3.22.1**, **Kotlin CLI**, **Go 1.26**,
   **Rust nightly**, Git, and `patch`.

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

4. Prepare Rust. Release builds should use MetaCubeX Go 1.26 with the patches in `.github/patch`, matching CI.

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
   kotlin scripts/native-build.main.kts --all
   ```

   Run the native script with `--help` to build Go, Rust, C++, or Geo assets separately.

6. Optionally sign release builds. Place the keystore at `release.keystore` in the project root, then create
   `signing.properties`:

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

   APKs are written to `app/build/outputs/apk/<build-type>/`. External builds omit Geo assets and `BundleMRS.7z`; mihomo
   downloads them when needed. On Windows, use `gradlew.bat`.
