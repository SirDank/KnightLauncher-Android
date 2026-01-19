package net.kdt.pojavlaunch.knight;

import android.util.Log;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.utils.DownloadUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;

/**
 * Utility class for checking app updates from GitHub releases.
 * Fetches the latest release and compares versions to determine if an update is available.
 */
public class UpdateChecker {
    private static final String TAG = "UpdateChecker";
    private static final String GITHUB_RELEASES_URL = 
        "https://api.github.com/repos/SirDank/KnightLauncher-Android/releases";
    
    /**
     * Data class containing information about a GitHub release.
     */
    public static class ReleaseInfo {
        public final String tagName;       // e.g., "v0.3.4"
        public final String versionName;   // e.g., "0.3.4" (without 'v' prefix)
        public final String apkUrl;        // Direct download URL for the APK
        public final String releaseName;   // e.g., "KnightLauncher Android v0.3.4"
        public final String releaseNotes;  // Release body text
        public final long apkSize;         // APK size in bytes
        
        public ReleaseInfo(String tagName, String versionName, String apkUrl, 
                          String releaseName, String releaseNotes, long apkSize) {
            this.tagName = tagName;
            this.versionName = versionName;
            this.apkUrl = apkUrl;
            this.releaseName = releaseName;
            this.releaseNotes = releaseNotes;
            this.apkSize = apkSize;
        }
    }
    
    /**
     * Callback interface for async update checks.
     */
    public interface UpdateCheckCallback {
        void onUpdateAvailable(ReleaseInfo release);
        void onNoUpdate();
        void onError(String error);
    }
    
    /**
     * Check for updates asynchronously.
     * Runs the network request on a background thread and calls the callback on the UI thread.
     * 
     * @param currentVersion The current app version (from BuildConfig.VERSION_NAME)
     * @param callback The callback to receive the result
     */
    public static void checkForUpdates(String currentVersion, UpdateCheckCallback callback) {
        new Thread(() -> {
            try {
                ReleaseInfo latestRelease = fetchLatestRelease();
                
                if (latestRelease == null) {
                    Tools.runOnUiThread(() -> callback.onNoUpdate());
                    return;
                }
                
                // Compare versions
                String currentClean = cleanVersion(currentVersion);
                String latestClean = latestRelease.versionName;
                
                Log.i(TAG, "Current version: " + currentClean + ", Latest: " + latestClean);
                
                if (isNewerVersion(latestClean, currentClean)) {
                    Tools.runOnUiThread(() -> callback.onUpdateAvailable(latestRelease));
                } else {
                    Tools.runOnUiThread(() -> callback.onNoUpdate());
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Update check failed", e);
                Tools.runOnUiThread(() -> callback.onError(e.getMessage()));
            }
        }).start();
    }
    
    /**
     * Fetches the latest release from GitHub API.
     * @return ReleaseInfo for the latest release, or null if no valid release found
     */
    private static ReleaseInfo fetchLatestRelease() throws IOException {
        try {
            String response = DownloadUtils.downloadString(GITHUB_RELEASES_URL);
            JSONArray releases = new JSONArray(response);
            
            if (releases.length() == 0) {
                return null;
            }
            
            // Get the first (latest) release
            JSONObject release = releases.getJSONObject(0);
            
            String tagName = release.getString("tag_name");
            String releaseName = release.optString("name", tagName);
            String releaseNotes = release.optString("body", "");
            
            // Find the APK asset
            JSONArray assets = release.getJSONArray("assets");
            String apkUrl = null;
            long apkSize = 0;
            
            for (int i = 0; i < assets.length(); i++) {
                JSONObject asset = assets.getJSONObject(i);
                String name = asset.getString("name");
                
                // Look for .apk file
                if (name.endsWith(".apk")) {
                    apkUrl = asset.getString("browser_download_url");
                    apkSize = asset.optLong("size", 0);
                    break;
                }
            }
            
            if (apkUrl == null) {
                Log.w(TAG, "No APK found in release " + tagName);
                return null;
            }
            
            String versionName = cleanVersion(tagName);
            
            return new ReleaseInfo(tagName, versionName, apkUrl, releaseName, releaseNotes, apkSize);
            
        } catch (Exception e) {
            throw new IOException("Failed to fetch releases from GitHub", e);
        }
    }
    
    /**
     * Clean a version string by removing common prefixes and suffixes.
     * 
     * Version formats from build.gradle:
     * 1. Tagged release: "v0.3.4-0-g1234abc-main" or "v0.3.4-main"
     * 2. Untagged build: "knightlauncher-v0.3.4-20260119-commit-branch" (version tag included)
     * 3. Local build: "LOCAL-20260119"
     * 
     * Examples:
     *   "v0.3.4" -> "0.3.4"
     *   "v0.3.4-0-g1234abc-main" -> "0.3.4"
     *   "knightlauncher-v0.3.4-20260119-abc1234-main" -> "0.3.4"
     *   "LOCAL-20260119" -> "" (local build)
     * 
     * @param version The raw version string
     * @return Cleaned version string (just numbers and dots)
     */
    private static String cleanVersion(String version) {
        if (version == null || version.isEmpty()) {
            return "";
        }
        
        // Handle LOCAL builds - no version available
        if (version.toUpperCase().startsWith("LOCAL-")) {
            return "";
        }
        
        // Handle knightlauncher-vX.X.X-date-commit-branch format
        // Extract the version tag after "knightlauncher-"
        if (version.toLowerCase().startsWith("knightlauncher-")) {
            // Format: knightlauncher-v0.3.4-20260119-commit-branch
            String afterPrefix = version.substring("knightlauncher-".length());
            // afterPrefix is now: v0.3.4-20260119-commit-branch
            // Extract the version part (up to the next dash after the version numbers)
            if (afterPrefix.toLowerCase().startsWith("v")) {
                afterPrefix = afterPrefix.substring(1);
                // afterPrefix is now: 0.3.4-20260119-commit-branch
                int dashIndex = afterPrefix.indexOf('-');
                if (dashIndex > 0) {
                    return afterPrefix.substring(0, dashIndex);
                }
                return afterPrefix;
            }
            return "";
        }
        
        // Standard version format: v0.3.4 or v0.3.4-suffix
        String cleaned = version;
        
        // Remove 'v' or 'V' prefix
        if (cleaned.toLowerCase().startsWith("v")) {
            cleaned = cleaned.substring(1);
        }
        
        // Extract just the version part (before any dash)
        // e.g., "0.3.4-0-g1234-main" -> "0.3.4"
        int dashIndex = cleaned.indexOf('-');
        if (dashIndex > 0) {
            cleaned = cleaned.substring(0, dashIndex);
        }
        
        return cleaned;
    }
    
    /**
     * Compare two version strings to determine if newVersion is newer than oldVersion.
     * 
     * @param newVersion The potential new version
     * @param oldVersion The current version
     * @return true if newVersion is strictly greater than oldVersion
     */
    private static boolean isNewerVersion(String newVersion, String oldVersion) {
        if (newVersion == null || newVersion.isEmpty()) {
            return false;
        }
        if (oldVersion == null || oldVersion.isEmpty()) {
            // If we can't determine current version, assume update is available
            return true;
        }
        
        String[] newParts = newVersion.split("\\.");
        String[] oldParts = oldVersion.split("\\.");
        
        int maxLength = Math.max(newParts.length, oldParts.length);
        
        for (int i = 0; i < maxLength; i++) {
            int newPart = 0;
            int oldPart = 0;
            
            if (i < newParts.length) {
                try {
                    newPart = Integer.parseInt(newParts[i]);
                } catch (NumberFormatException e) {
                    // Non-numeric part, skip
                }
            }
            
            if (i < oldParts.length) {
                try {
                    oldPart = Integer.parseInt(oldParts[i]);
                } catch (NumberFormatException e) {
                    // Non-numeric part, skip
                }
            }
            
            if (newPart > oldPart) {
                return true;
            } else if (newPart < oldPart) {
                return false;
            }
        }
        
        // Versions are equal
        return false;
    }
}
