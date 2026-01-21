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

<p align="center">
  <b>Play Spiral Knights on your Android device! 🎮</b>
</p>

KnightLauncher is a dedicated launcher that allows you to play **Spiral Knights** on your Android device. It is a fork of [Amethyst](https://github.com/AngelAuraMC/Amethyst-Android), tailored specifically for the Spiral Knights experience.

---

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

> **Note:** The APK is signed for easy installation. No need to worry about unsigned APK warnings!

---

## ⚠️ Public Beta Notice

KnightLauncher for Android is currently in **public beta**. You may encounter bugs or issues. Please report them on our [Issues page](https://github.com/SirDank/KnightLauncher-Android/issues).

---

## ✨ Features

### Core Features

- 🎮 **Dedicated Spiral Knights Launcher**
- ☕ **OpenJDK 8 Support**
- 🕹️ **Virtual Controls**

### Mods & Customization

- 📥 **Mods Downloader** — Download mods directly from within the app with progress tracking
- 🔄 **Incremental Mod Sync** — Smart synchronization using SHA hash comparison to minimize downloads and remove deprecated mods
- 🔧 **Apply Mods** — Rebuild game resources and overlay downloaded mods with one tap

### App Management

- 🔄 **In-App Updates** — Automatic update checker that fetches the latest release from GitHub and prompts you to update
- 🔃 **Update / Reset Game** — Reinstall or update the game with a single tap
- 🎛️ **Reset Controls** — Restore custom controls to their default state

---

## 📋 Supported Devices

| Architecture | Support |
|-------------|---------|
| `arm64-v8a` | ✅ Full Support |
| `armeabi-v7a` | ✅ Full Support |
| `x86` | ❌ Not Supported (Emulators) |
| `x86_64` | ❌ Not Supported (Emulators) |

> **Note:** KnightLauncher is built exclusively for ARM devices. x86/x86_64 architectures (typically used in emulators) are not supported.

---

## 🛠️ Building

### Prerequisites

- **JDK 21** (Temurin recommended)
- **Git** with submodules support
- **Internet connection** (for downloading JRE artifacts)
- Platform-specific tools:
  - **Linux/macOS:** `curl`, `unzip`
  - **Windows:** PowerShell

### Quick Build (Using Build Scripts)

We provide convenient build scripts that handle everything automatically:

#### Linux/macOS

```bash
# Clone the repository with submodules
git clone --recursive https://github.com/SirDank/KnightLauncher-Android.git
cd KnightLauncher-Android

# Run the build script
chmod +x build.sh
./build.sh
```

#### Windows

```batch
:: Clone the repository with submodules
git clone --recursive https://github.com/SirDank/KnightLauncher-Android.git
cd KnightLauncher-Android

:: Run the build script
build.bat
```

The build scripts will:

1. ✅ Check requirements (JDK 21, curl/PowerShell, etc.)
2. 📥 Download JRE components automatically
3. 🌐 Update the language list
4. 🔧 Patch MobileGlues for ARM-only builds
5. 🏗️ Build the GLFW stub
6. 📦 Build the signed release APK

**Output:** `out/KnightLauncher.apk`

### Manual Build

If you prefer manual control over the build process:

1. **Clone the repository:**

   ```bash
   git clone --recursive https://github.com/SirDank/KnightLauncher-Android.git
   cd KnightLauncher-Android
   ```

2. **Download JRE 8:**
   Download the `jre8-pojav` artifact from [CI auto builds](https://github.com/AngelAuraMC/openjdk-build-multiarch/actions) and extract to `app_pojavlauncher/src/main/assets/components/jre/`

3. **Update language list:**

   ```bash
   # Linux/macOS
   chmod +x scripts/languagelist_updater.sh
   bash scripts/languagelist_updater.sh

   # Windows
   scripts\languagelist_updater.bat
   ```

4. **Build GLFW stub:**

   ```bash
   ./gradlew :jre_lwjgl3glfw:build --build-cache
   ```

5. **Build the APK:**

   ```bash
   # Debug build
   ./gradlew :app_pojavlauncher:assembleDebug

   # Release build (signed)
   ./gradlew :app_pojavlauncher:assembleRelease
   ```

**APK Locations:**

- Debug: `app_pojavlauncher/build/outputs/apk/debug/app_pojavlauncher-debug.apk`
- Release: `app_pojavlauncher/build/outputs/apk/release/app_pojavlauncher-release.apk`

---

## 🔄 CI/CD Workflows

| Workflow | Trigger | Description |
|----------|---------|-------------|
| **Android CI (Testing)** | Manual | Builds test APK for development validation |
| **Android Release (Public Beta)** | Manual (with version input) | Creates GitHub release with signed APK |

Both workflows:

- Use JDK 21 and Gradle 8.11
- Download JRE 8 from [AngelAuraMC/angelauramc-openjdk-build](https://github.com/AngelAuraMC/angelauramc-openjdk-build)
- Patch MobileGlues for ARM-only builds
- Build signed release APKs

---

## 🙏 Credits & Dependencies

| Project | Description |
|---------|-------------|
| [PojavLauncher](https://github.com/PojavLauncherTeam/PojavLauncher) | The base of this project |
| [Amethyst](https://github.com/AngelAuraMC/Amethyst-Android) | The immediate parent fork |
| [Spiral Knights](https://www.spiralknights.com/) | The game itself (owned by Grey Havens) |
| [Boardwalk](https://github.com/zhuowei/Boardwalk) | The original Android Java launcher |
| [GL4ES](https://github.com/ptitSeb/gl4es) | OpenGL to OpenGL ES translation |
| [MobileGlues](https://github.com/MobileGL-Dev/MobileGlues) | Mobile OpenGL utilities |
| [OpenAL-Soft](https://github.com/kcat/openal-soft) | Cross-platform audio library |

---

## 📄 License

KnightLauncher is licensed under [GNU LGPLv3](LICENSE).

Spiral Knights is a trademark of Grey Havens.
