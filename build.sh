#!/bin/bash

# =============================================================================
# KnightLauncher Android Build Script for Linux
# =============================================================================
# This script builds the KnightLauncher Android APK.
# Requirements:
#   - JDK 21 (Temurin recommended)
#   - Git with submodules cloned
#   - Internet connection (for downloading JRE artifacts)
#   - curl, unzip
# =============================================================================

set -e  # Exit on error

# Clear console
clear

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Print colored messages
info() { echo -e "${BLUE}[INFO]${NC} $1"; }
success() { echo -e "${GREEN}[SUCCESS]${NC} $1"; }
warning() { echo -e "${YELLOW}[WARNING]${NC} $1"; }
error() { echo -e "${RED}[ERROR]${NC} $1"; exit 1; }

# Get script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Configuration
ASSETS_DIR="app_pojavlauncher/src/main/assets/components/jre-21"

# =============================================================================
# Functions
# =============================================================================

check_requirements() {
    info "Checking requirements..."
    
    # Use JAVA_HOME if set
    if [[ -n "$JAVA_HOME" ]]; then
        info "JAVA_HOME is set to: $JAVA_HOME"
        export PATH="$JAVA_HOME/bin:$PATH"
    fi
    
    # Check Java version
    if ! command -v java &> /dev/null; then
        error "Java is not installed. Please install JDK 21 (Temurin recommended).
        Download from: https://adoptium.net/temurin/releases/?version=21"
    fi
    
    JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
    info "Java version detected: $JAVA_VERSION"
    
    if [[ "$JAVA_VERSION" != "21" ]]; then
        echo ""
        error "JDK 21 is REQUIRED but found JDK $JAVA_VERSION.
        Android Gradle Plugin 8.7.2 requires JDK 21.
        
        Solutions:
        1. Install JDK 21 Temurin from: https://adoptium.net/temurin/releases/?version=21
        2. Set JAVA_HOME to your JDK 21 installation:
           export JAVA_HOME=/path/to/jdk-21
        3. Or run this script from a shell with JDK 21 in PATH"
    fi
    
    success "JDK 21 detected."
    
    # Check curl
    if ! command -v curl &> /dev/null; then
        error "curl is not installed. Please install curl."
    fi
    
    # Check unzip
    if ! command -v unzip &> /dev/null; then
        error "unzip is not installed. Please install unzip."
    fi
    
    success "All requirements met."
}

check_jre() {
    info "Checking JRE 21 component..."
    
    # Create directory if needed
    mkdir -p "$ASSETS_DIR"
    
    # Check if both required JRE files exist
    local jre_arm="$ASSETS_DIR/bin-arm.tar.xz"
    local jre_arm64="$ASSETS_DIR/bin-arm64.tar.xz"
    
    if [[ -f "$jre_arm" && -f "$jre_arm64" ]]; then
        success "JRE 21 component found."
    else
        echo ""
        error "JRE 21 component is missing!

Required files:
    - bin-arm.tar.xz
    - bin-arm64.tar.xz

Please manually download jre21-pojav from:
    https://github.com/AngelAuraMC/angelauramc-openjdk-build/actions?query=branch%3Abuildjre17-21

After downloading, extract the contents to:
    $ASSETS_DIR

Then run this script again."
    fi
}

update_language_list() {
    info "Updating language list..."
    
    if [[ -f "scripts/languagelist_updater.sh" ]]; then
        chmod +x scripts/languagelist_updater.sh
        bash scripts/languagelist_updater.sh
        success "Language list updated."
    else
        warning "Language list updater script not found. Skipping."
    fi
}

patch_mobileglues() {
    info "Patching MobileGlues for ARM-only build..."
    
    # Check if already patched
    if grep -q "abiFilters" MobileGlues/build.gradle.kts 2>/dev/null; then
        info "MobileGlues already patched for ARM-only build. Skipping."
        return 0
    fi
    
    # Skip x86/x86_64 builds (only ARM devices are supported)
    sed -i 's/ndkVersion = "27.3.13750724"/ndkVersion = "27.3.13750724"\n        ndk {\n            \/\/ Only build for ARM architectures (skip x86\/x86_64 which are for emulators)\n            abiFilters += listOf("arm64-v8a", "armeabi-v7a")\n        }/' MobileGlues/build.gradle.kts
    
    success "MobileGlues patched for ARM-only build."
}

build_glfw_stub() {
    info "Building GLFW stub (jre_lwjgl3glfw)..."
    
    ./gradlew :jre_lwjgl3glfw:build --build-cache
    success "GLFW stub built."
}

build_apk() {
    info "Building KnightLauncher APK..."
    
    ./gradlew :app_pojavlauncher:assembleRelease --build-cache
    
    # Create output directory and copy APK
    mkdir -p out
    cp app_pojavlauncher/build/outputs/apk/release/app_pojavlauncher-release.apk build/KnightLauncher.apk
    
    success "APK built successfully!"
    success "Output: $(realpath build/KnightLauncher.apk)"
}

# =============================================================================
# Main
# =============================================================================

main() {
    echo ""
    echo "=============================================="
    echo "  KnightLauncher Android Build Script"
    echo "=============================================="
    echo ""
    
    check_requirements
    check_jre
    update_language_list
    patch_mobileglues
    build_glfw_stub
    build_apk
    
    echo ""
    success "Build completed successfully!"
    echo ""
    echo "The APK is located at: build/KnightLauncher.apk"
    echo ""
}

main "$@"
