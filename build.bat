@echo off
setlocal EnableDelayedExpansion

:: =============================================================================
:: KnightLauncher Android Build Script for Windows
:: =============================================================================
:: This script builds the KnightLauncher Android APK.
:: Requirements:
::   - JDK 21 (Temurin recommended)
::   - Git with submodules cloned
::   - Internet connection (for downloading JRE artifacts)
::   - PowerShell (for downloading files)
:: =============================================================================

title KnightLauncher Android Build

:: Get script directory and navigate there
set "SCRIPT_DIR=%~dp0"
cd /d "%SCRIPT_DIR%"

:: Configuration
set "JRE_REPO=AngelAuraMC/angelauramc-openjdk-build"
set "ASSETS_DIR=app_pojavlauncher\src\main\assets\components"

echo.
echo ==============================================
echo   KnightLauncher Android Build Script
echo ==============================================
echo.

:: =============================================================================
:: Check Requirements
:: =============================================================================

echo [INFO] Checking requirements...

:: Check Java
where java >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Java is not installed. Please install JDK 21 Temurin.
    goto :error_exit
)

:: Simple Java version display
echo [INFO] Java found:
java -version 2>&1 | findstr /i "version"
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
:: Download JRE Components
:: =============================================================================

echo [INFO] Setting up JRE components...

:: Create directories
if not exist "%ASSETS_DIR%\jre" mkdir "%ASSETS_DIR%\jre"
if not exist "%ASSETS_DIR%\jre-new" mkdir "%ASSETS_DIR%\jre-new"
if not exist "%ASSETS_DIR%\jre-21" mkdir "%ASSETS_DIR%\jre-21"

:: Check and download JRE 8
call :check_and_download_jre jre jre8-pojav

:: Check and download JRE 17
call :check_and_download_jre jre-new jre17-pojav

:: Check and download JRE 21
call :check_and_download_jre jre-21 jre21-pojav

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

:: Skip x86/x86_64 builds (only ARM devices are supported)
powershell -Command "$file = 'MobileGlues\build.gradle.kts'; $content = Get-Content $file -Raw; $newContent = $content -replace 'ndkVersion = \"27.3.13750724\"', ('ndkVersion = \"27.3.13750724\"' + \"`r`n        ndk {`r`n            // Only build for ARM architectures (skip x86/x86_64 which are for emulators)`r`n            abiFilters += listOf(\"arm64-v8a\", \"armeabi-v7a\")`r`n        }\"); Set-Content $file $newContent -NoNewline"

echo [SUCCESS] MobileGlues patched for ARM-only build.
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

call gradlew.bat :app_pojavlauncher:assembleDebug --build-cache
if errorlevel 1 (
    echo [ERROR] Failed to build APK.
    goto :error_exit
)

:: Create output directory and copy APK
if not exist "out" mkdir "out"
copy /y "app_pojavlauncher\build\outputs\apk\debug\app_pojavlauncher-debug.apk" "out\KnightLauncher.apk" >nul

echo [SUCCESS] APK built successfully!
echo.
echo ==============================================
echo   Build completed successfully!
echo ==============================================
echo.
echo The APK is located at: %SCRIPT_DIR%out\KnightLauncher.apk
echo.

goto :success_exit

:: =============================================================================
:: Functions
:: =============================================================================

:check_and_download_jre
:: %1 = JRE directory name (jre, jre-new, jre-21)
:: %2 = JRE artifact name (jre8-pojav, jre17-pojav, jre21-pojav)
set "JRE_DIR_NAME=%~1"
set "JRE_ARTIFACT=%~2"
set "FULL_PATH=%ASSETS_DIR%\%JRE_DIR_NAME%"

:: Check if directory has any files
dir /b "%FULL_PATH%\*" >nul 2>&1
if not errorlevel 1 (
    echo [WARNING] %JRE_ARTIFACT% directory already has files. Skipping download.
    goto :eof
)

:: Download JRE
echo [INFO] Downloading %JRE_ARTIFACT%...

set "DOWNLOAD_URL=https://github.com/AngelAuraMC/openjdk-build-multiarch/releases/download/nightly/%JRE_ARTIFACT%.zip"
set "ALT_URL=https://github.com/%JRE_REPO%/releases/download/nightly/%JRE_ARTIFACT%.zip"
set "TEMP_ZIP=%TEMP%\%JRE_ARTIFACT%.zip"

:: Try primary URL using PowerShell
powershell -Command "$ProgressPreference = 'SilentlyContinue'; try { Invoke-WebRequest -Uri '%DOWNLOAD_URL%' -OutFile '%TEMP_ZIP%' -ErrorAction Stop } catch { exit 1 }"
if not errorlevel 1 (
    echo [SUCCESS] Downloaded %JRE_ARTIFACT% from openjdk-build-multiarch
    goto :do_extract
)

:: Try alternate URL
powershell -Command "$ProgressPreference = 'SilentlyContinue'; try { Invoke-WebRequest -Uri '%ALT_URL%' -OutFile '%TEMP_ZIP%' -ErrorAction Stop } catch { exit 1 }"
if not errorlevel 1 (
    echo [SUCCESS] Downloaded %JRE_ARTIFACT% from %JRE_REPO%
    goto :do_extract
)

echo [WARNING] Could not download %JRE_ARTIFACT% automatically.
echo [WARNING] Please manually download from: https://github.com/AngelAuraMC/openjdk-build-multiarch/actions
echo [WARNING] Extract to: %FULL_PATH%
goto :eof

:do_extract
echo [INFO] Extracting %JRE_ARTIFACT%...
powershell -Command "$ProgressPreference = 'SilentlyContinue'; Expand-Archive -Path '%TEMP_ZIP%' -DestinationPath '%FULL_PATH%' -Force"
if exist "%TEMP_ZIP%" del /q "%TEMP_ZIP%"
echo [SUCCESS] Extracted %JRE_ARTIFACT% to %FULL_PATH%
goto :eof

:error_exit
echo.
echo [ERROR] Build failed!
echo.
pause
exit /b 1

:success_exit
pause
exit /b 0
