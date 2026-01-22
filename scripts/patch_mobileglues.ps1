# Patch MobileGlues for ARM-only build
# This script is called by build.bat to avoid CMD escaping issues

$file = 'MobileGlues\build.gradle.kts'

# Check if already patched
if (Select-String -Path $file -Pattern 'abiFilters' -Quiet) {
    Write-Host "[INFO] MobileGlues already patched for ARM-only build. Skipping."
    exit 0
}

# Read the file
$content = Get-Content $file -Raw

# Create the patch string (newlines with proper indentation)
$patch = @"

        ndk {
            // Only build for ARM architectures (skip x86/x86_64 which are for emulators)
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
"@

# Replace the ndkVersion line with itself + the ndk block
$newContent = $content -replace 'ndkVersion = "27.3.13750724"', ('ndkVersion = "27.3.13750724"' + $patch)

# Write back to file
Set-Content $file $newContent -NoNewline

Write-Host "[SUCCESS] MobileGlues patched for ARM-only build."
