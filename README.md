<h1 align="center">KnightLauncher (Android)</h1>

<p align="center">
  <a href="https://github.com/SirDank/KnightLauncher-Android/actions/workflows/android.yml">
    <img src="https://github.com/SirDank/KnightLauncher-Android/actions/workflows/android.yml/badge.svg" alt="Build Status">
  </a>
  <a href="https://github.com/SirDank/KnightLauncher-Android/releases/latest">
    <img src="https://img.shields.io/github/v/release/SirDank/KnightLauncher-Android?include_prereleases&label=latest%20release" alt="Latest Release">
  </a>
  <a href="https://github.com/SirDank/KnightLauncher-Android/releases">
    <img src="https://img.shields.io/github/downloads/SirDank/KnightLauncher-Android/total" alt="Total Downloads">
  </a>
  <a href="LICENSE">
    <img src="https://img.shields.io/github/license/SirDank/KnightLauncher-Android" alt="License">
  </a>
</p>

KnightLauncher is a launcher that allows you to play **Spiral Knights** on your Android device. It is a fork of [Amethyst](https://github.com/AngelAuraMC/Amethyst-Android)

## 📥 Download & Installation

### For Users (Recommended)

1. **Download the latest APK** from the [Releases Page](https://github.com/SirDank/KnightLauncher-Android/releases/latest)
2. **Enable installation from unknown sources**:
   - Go to `Settings` → `Security` → Enable `Unknown Sources`
   - Or on newer Android versions: `Settings` → `Apps` → `Special access` → `Install unknown apps` → Choose your browser/file manager → Allow
3. **Install the APK**:
   - Open the downloaded `.apk` file
   - Tap `Install` and wait for installation to complete
4. **Launch KnightLauncher** and start playing Spiral Knights!

## ⚠️ Public Beta Notice

KnightLauncher for Android is currently in **public beta**. You may encounter bugs or issues. Please report them on our [Issues page](https://github.com/SirDank/KnightLauncher-Android/issues).

## ✨ Features

- KnightLauncher is a dedicated launcher for the Java-based MMO **Spiral Knights**.
- It handles the downloading, installation, and execution of the game on Android.
- OpenJDK 8/11/17/21 Support
- Virtual Controls
- Optimization for Touch Screens

## Building

### Quick Build (Recommended)

The easiest way to build KnightLauncher is to use the pre-built JREs provided by our CI.

1. Clone the repository: `git clone --recursive https://github.com/SirDank/KnightLauncher-Android.git`
2. Build the launcher: `./gradlew :app_pojavlauncher:assembleDebug` (Use `gradlew.bat` on Windows)

The built APK will be located in `app_pojavlauncher/build/outputs/apk/debug/`.

### Detailed Build

If you need more control over the build process, follow these steps:

1. **Java Runtime Environment (JRE):** Download the `jre8-pojav` artifact from our [CI auto builds](https://github.com/AngelAuraMC/openjdk-build-multiarch/actions). This package contains pre-built JREs for all supported architectures. If you need to build the JRE yourself, follow the instructions in the [android-openjdk-build-multiarch](https://github.com/AngelAuraMC/openjdk-build-multiarch) repository.

2. **LWJGL:** The build instructions for the custom LWJGL are available over the [LWJGL repository](https://github.com/AngelAuraMC/lwjgl3).

3. **Language List:** Because languages are auto-added by Crowdin, you need to run the language list generator before building. In the project directory, run:
    - Linux/macOS:

        ```bash
        chmod +x scripts/languagelist_updater.sh
        bash scripts/languagelist_updater.sh
        ```

    - Windows:

        ```batch
        scripts\languagelist_updater.bat
        ```

4. **Build GLFW stub:** `./gradlew :jre_lwjgl3glfw:build`

5. **Build the launcher:** `./gradlew :app_pojavlauncher:assembleDebug`

## Credits & Dependencies

- [PojavLauncher](https://github.com/PojavLauncherTeam/PojavLauncher): The base of this project.
- [Amethyst](https://github.com/AngelAuraMC/Amethyst-Android): The immediate parent fork.
- [Spiral Knights](https://www.spiralknights.com/): The game itself (owned by Grey Havens).
- [Boardwalk](https://github.com/zhuowei/Boardwalk): The original Android Java launcher.
- [GL4ES](https://github.com/ptitSeb/gl4es), [MobileGlues](https://github.com/MobileGL-Dev/MobileGlues), [OpenAL-Soft](https://github.com/kcat/openal-soft): Native libraries for graphics and audio.

## License

KnightLauncher is licensed under [GNU LGPLv3](LICENSE). Spiral Knights is a trademark of Grey Havens.
