@echo off
setlocal EnableDelayedExpansion

:: =============================================================================
:: KnightLauncher Android Build Script for Windows
:: =============================================================================
:: This script builds the KnightLauncher Android APK.
:: Requirements:
::   - JDK 21 or 25 (Temurin recommended)
::   - Git with submodules cloned
::   - Internet connection (for downloading JRE artifacts)
::   - PowerShell (for downloading files)
:: =============================================================================

title KnightLauncher Android Build
cls

:: Get script directory and navigate there
set "SCRIPT_DIR=%~dp0"
cd /d "%SCRIPT_DIR%"

:: Configuration
set "ASSETS_DIR=app_pojavlauncher\src\main\assets\components\jre-25"

echo.
echo ==============================================
echo   KnightLauncher Android Build Script
echo ==============================================
echo.

:: =============================================================================
:: Check Requirements
:: =============================================================================

echo [INFO] Checking requirements...

:: Check JAVA_HOME first (prefer explicit JDK 21 or 25 path)
if defined JAVA_HOME (
    echo [INFO] JAVA_HOME is set to: %JAVA_HOME%
    set "PATH=%JAVA_HOME%\bin;%PATH%"
)

:: Check Java
where java >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Java is not installed. Please install JDK 21 or 25 Temurin.
    echo [INFO]  Download from: https://adoptium.net/temurin/releases/
    goto :error_exit
)

:: Check Java version - require Java 21 or 25
echo [INFO] Checking Java version...
for /f "tokens=3" %%v in ('java -version 2^>^&1 ^| findstr /i "version"') do (
    set "JAVA_VER_STRING=%%~v"
)
:: Extract major version (handles "21.0.x" or "25.0.x" format)
for /f "tokens=1 delims=." %%m in ("!JAVA_VER_STRING!") do (
    set "JAVA_MAJOR=%%m"
)

echo [INFO] Java version detected: !JAVA_VER_STRING! (major: !JAVA_MAJOR!)

if not "!JAVA_MAJOR!"=="21" if not "!JAVA_MAJOR!"=="25" (
    echo.
    echo [ERROR] JDK 21 or 25 is REQUIRED but found JDK !JAVA_MAJOR!.
    echo [ERROR] KnightLauncher Android requires JDK 21 or 25 to build.
    echo.
    echo [INFO] Solutions:
    echo        1. Install JDK 21 or 25 Temurin from: https://adoptium.net/temurin/releases/
    echo        2. Set JAVA_HOME to your JDK installation:
    echo           set JAVA_HOME=C:\path\to\jdk-21  or  set JAVA_HOME=C:\path\to\jdk-25
    echo        3. Or run this script from a shell with JDK 21 or 25 in PATH
    echo.
    goto :error_exit
)

echo [SUCCESS] JDK !JAVA_MAJOR! detected.

:: Enable native access for JDK 25+ (restricted methods are blocked by default)
if !JAVA_MAJOR! GEQ 25 (
    echo [INFO] JDK 25+ detected, enabling native access for Gradle and subprocesses...
    set "GRADLE_OPTS=%GRADLE_OPTS% --enable-native-access=ALL-UNNAMED"
    set "JAVA_TOOL_OPTIONS=%JAVA_TOOL_OPTIONS% --enable-native-access=ALL-UNNAMED"
)
echo.

:: Check PowerShell
where powershell >nul 2>&1
if errorlevel 1 (
    echo [ERROR] PowerShell is not installed. Required for downloading files.
    goto :error_exit
)

echo [SUCCESS] All requirements met.
echo.

:: =============================================================================
:: Check JRE Components
:: =============================================================================

echo [INFO] Checking JRE 25 component...

:: Create directory if needed
if not exist "%ASSETS_DIR%" mkdir "%ASSETS_DIR%"

:: Check if both required JRE files exist
set "JRE_ARM=%ASSETS_DIR%\bin-arm.tar.xz"
set "JRE_ARM64=%ASSETS_DIR%\bin-arm64.tar.xz"

if exist "!JRE_ARM!" if exist "!JRE_ARM64!" (
    echo [SUCCESS] JRE 25 component found.
    goto :jre_ok
)

echo.
echo [ERROR] JRE 25 component is missing!
echo.
echo [INFO] Required files:
echo        - bin-arm.tar.xz
echo        - bin-arm64.tar.xz
echo.
echo [INFO] Please manually download jre25-multiarch from:
echo        https://github.com/FCL-Team/Android-OpenJDK-Build/actions?query=branch%%3ABuild_JRE_25
echo.
echo [INFO] After downloading, extract the contents to:
echo        %ASSETS_DIR%
echo.
echo [INFO] Then run this script again.
echo.
goto :error_exit

:jre_ok
echo.

:: =============================================================================
:: Update Language List
:: =============================================================================

echo [INFO] Updating language list...

if exist "scripts\languagelist_updater.bat" (
    call scripts\languagelist_updater.bat
    if errorlevel 1 (
        echo [WARNING] Language list updater had issues but continuing...
    ) else (
        echo [SUCCESS] Language list updated.
    )
) else (
    echo [WARNING] Language list updater script not found. Skipping.
)

echo.

:: =============================================================================
:: Patch MobileGlues for ARM-only build
:: =============================================================================

echo [INFO] Patching MobileGlues for ARM-only build...

:: Use external PowerShell script to avoid CMD escaping issues
powershell -ExecutionPolicy Bypass -File "scripts\patch_mobileglues.ps1"

echo.

:: =============================================================================
:: Build GLFW Stub
:: =============================================================================

echo [INFO] Building GLFW stub jre_lwjgl3glfw...

call gradlew.bat :jre_lwjgl3glfw:build --build-cache
if errorlevel 1 (
    echo [ERROR] Failed to build GLFW stub.
    goto :error_exit
)
echo [SUCCESS] GLFW stub built.
echo.

:: =============================================================================
:: Build APK
:: =============================================================================

echo [INFO] Building KnightLauncher APK...

call gradlew.bat :app_pojavlauncher:assembleRelease --build-cache
if errorlevel 1 (
    echo [ERROR] Failed to build APK.
    goto :error_exit
)

:: Create output directory and copy APK
if not exist "out" mkdir "out"
copy /y "app_pojavlauncher\build\outputs\apk\release\app_pojavlauncher-release.apk" "build\KnightLauncher.apk" >nul

echo [SUCCESS] APK built successfully!
echo.
echo ==============================================
echo   Build completed successfully!
echo ==============================================
echo.
echo The APK is located at: %SCRIPT_DIR%build\KnightLauncher.apk
echo.

goto :success_exit

:: =============================================================================
:: Exit handlers
:: =============================================================================

:error_exit
echo.
echo [ERROR] Build failed!
echo.
pause
exit /b 1

:success_exit
pause
exit /b 0
