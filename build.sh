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
JRE_REPO="AngelAuraMC/angelauramc-openjdk-build"
JRE8_BRANCH="buildjre8"
JRE17_BRANCH="buildjre17-21"
JRE21_BRANCH="buildjre17-21"
ASSETS_DIR="app_pojavlauncher/src/main/assets/components"

# =============================================================================
# Functions
# =============================================================================

check_requirements() {
    info "Checking requirements..."
    
    # Check Java version
    if ! command -v java &> /dev/null; then
        error "Java is not installed. Please install JDK 21 (Temurin recommended)."
    fi
    
    JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
    if [[ "$JAVA_VERSION" != "21" ]]; then
        warning "Java 21 is recommended. Current version: $JAVA_VERSION"
    else
        success "JDK 21 detected."
    fi
    
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

download_github_artifact() {
    local repo="$1"
    local branch="$2"
    local artifact_name="$3"
    local dest_dir="$4"
    
    info "Downloading $artifact_name from $repo ($branch branch)..."
    
    # Create destination directory
    mkdir -p "$dest_dir"
    
    # Get the latest workflow run ID
    local runs_url="https://api.github.com/repos/$repo/actions/workflows/build.yml/runs?branch=$branch&status=success&per_page=1"
    local run_id=$(curl -s "$runs_url" | grep -o '"id": [0-9]*' | head -n 1 | cut -d' ' -f2)
    
    if [[ -z "$run_id" ]]; then
        error "Failed to find successful workflow run for $artifact_name"
    fi
    
    # Get artifact download URL
    local artifacts_url="https://api.github.com/repos/$repo/actions/runs/$run_id/artifacts"
    local artifact_info=$(curl -s "$artifacts_url")
    local artifact_id=$(echo "$artifact_info" | grep -B5 "\"name\": \"$artifact_name\"" | grep -o '"id": [0-9]*' | head -n 1 | cut -d' ' -f2)
    
    if [[ -z "$artifact_id" ]]; then
        warning "Could not find artifact $artifact_name via API. Attempting nightly release download..."
        download_from_nightly "$artifact_name" "$dest_dir"
        return
    fi
    
    # Note: GitHub requires authentication to download artifacts via API
    # For public access, we'll try the nightly release approach
    warning "GitHub API requires authentication for artifact downloads."
    warning "Attempting nightly release download..."
    download_from_nightly "$artifact_name" "$dest_dir"
}

download_from_nightly() {
    local artifact_name="$1"
    local dest_dir="$2"
    
    # Try to download from nightly releases
    local release_url="https://github.com/AngelAuraMC/openjdk-build-multiarch/releases/download/nightly/$artifact_name.zip"
    local alt_url="https://github.com/$JRE_REPO/releases/download/nightly/$artifact_name.zip"
    
    local temp_zip="/tmp/$artifact_name.zip"
    
    info "Trying to download from nightly release..."
    
    if curl -L -f -o "$temp_zip" "$release_url" 2>/dev/null; then
        success "Downloaded $artifact_name from openjdk-build-multiarch"
    elif curl -L -f -o "$temp_zip" "$alt_url" 2>/dev/null; then
        success "Downloaded $artifact_name from $JRE_REPO"
    else
        warning "Could not download $artifact_name automatically."
        warning "Please manually download from: https://github.com/AngelAuraMC/openjdk-build-multiarch/actions"
        warning "Extract to: $dest_dir"
        return 1
    fi
    
    # Extract the artifact
    info "Extracting $artifact_name..."
    unzip -o "$temp_zip" -d "$dest_dir"
    rm -f "$temp_zip"
    success "Extracted $artifact_name to $dest_dir"
}

download_jres() {
    info "Setting up JRE components..."
    
    # Create directories
    mkdir -p "$ASSETS_DIR/jre"
    mkdir -p "$ASSETS_DIR/jre-new"
    mkdir -p "$ASSETS_DIR/jre-21"
    
    # Check if JREs already exist
    if [[ -d "$ASSETS_DIR/jre" && $(ls -A "$ASSETS_DIR/jre" 2>/dev/null) ]]; then
        warning "JRE 8 directory already exists. Skipping download."
    else
        download_github_artifact "$JRE_REPO" "$JRE8_BRANCH" "jre8-pojav" "$ASSETS_DIR/jre" || true
    fi
    
    if [[ -d "$ASSETS_DIR/jre-new" && $(ls -A "$ASSETS_DIR/jre-new" 2>/dev/null) ]]; then
        warning "JRE 17 directory already exists. Skipping download."
    else
        download_github_artifact "$JRE_REPO" "$JRE17_BRANCH" "jre17-pojav" "$ASSETS_DIR/jre-new" || true
    fi
    
    if [[ -d "$ASSETS_DIR/jre-21" && $(ls -A "$ASSETS_DIR/jre-21" 2>/dev/null) ]]; then
        warning "JRE 21 directory already exists. Skipping download."
    else
        download_github_artifact "$JRE_REPO" "$JRE21_BRANCH" "jre21-pojav" "$ASSETS_DIR/jre-21" || true
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

build_glfw_stub() {
    info "Building GLFW stub (jre_lwjgl3glfw)..."
    
    ./gradlew :jre_lwjgl3glfw:build --build-cache
    success "GLFW stub built."
}

build_apk() {
    info "Building KnightLauncher APK..."
    
    ./gradlew :app_pojavlauncher:assembleDebug --build-cache
    
    # Create output directory and copy APK
    mkdir -p out
    cp app_pojavlauncher/build/outputs/apk/debug/app_pojavlauncher-debug.apk out/KnightLauncher.apk
    
    success "APK built successfully!"
    success "Output: $(realpath out/KnightLauncher.apk)"
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
    download_jres
    update_language_list
    build_glfw_stub
    build_apk
    
    echo ""
    success "Build completed successfully!"
    echo ""
    echo "The APK is located at: out/KnightLauncher.apk"
    echo ""
}

main "$@"
