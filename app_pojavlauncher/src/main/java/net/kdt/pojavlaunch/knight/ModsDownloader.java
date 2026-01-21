package net.kdt.pojavlaunch.knight;

import android.util.Log;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.utils.DownloadUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Utility class for downloading mods from the Spiral Knights Modpack GitHub repository.
 * Uses incremental sync with SHA hash comparison to minimize downloads.
 */
public class ModsDownloader {
    private static final String TAG = "ModsDownloader";
    private static final String GITHUB_API_URL = "https://api.github.com/repos/SirDank/Spiral-Knights-Modpack/contents/mods";
    
    /**
     * Callback interface for reporting download progress.
     */
    public interface ProgressCallback {
        void onStatusUpdate(String status);
        void onProgress(int current, int total, String currentFileName);
        void onComplete(SyncStats stats);
        void onError(String error, Throwable throwable);
    }
    
    /**
     * Stats for the sync operation.
     */
    public static class SyncStats {
        public int downloaded = 0;
        public int skipped = 0;
        public int deleted = 0;
        
        @Override
        public String toString() {
            return "Downloaded: " + downloaded + ", Skipped: " + skipped + ", Deleted: " + deleted;
        }
    }
    
    /**
     * Gets the mods directory path.
     */
    public static File getModsDirectory() {
        return new File(Tools.DIR_GAME_HOME, "mods");
    }
    
    /**
     * Fetches the list of mod files from the GitHub repository with SHA hashes.
     * @return List of ModFile objects containing name, download URL, and SHA hash
     */
    public static List<ModFile> getModsList() throws IOException {
        List<ModFile> mods = new ArrayList<>();
        
        try {
            String response = DownloadUtils.downloadString(GITHUB_API_URL);
            JSONArray files = new JSONArray(response);
            
            for (int i = 0; i < files.length(); i++) {
                JSONObject file = files.getJSONObject(i);
                String type = file.getString("type");
                
                // Only include files (not directories)
                if ("file".equals(type)) {
                    String name = file.getString("name");
                    String downloadUrl = file.getString("download_url");
                    String sha = file.optString("sha", "");
                    mods.add(new ModFile(name, downloadUrl, sha));
                }
            }
        } catch (Exception e) {
            throw new IOException("Failed to fetch mods list from GitHub", e);
        }
        
        return mods;
    }
    
    /**
     * Calculates the Git blob SHA hash for a file.
     * Git uses: SHA1("blob " + filesize + "\0" + content)
     * @param file The file to hash
     * @return The Git blob SHA hash as a hex string
     */
    private static String calculateGitBlobSha(File file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            long fileSize = file.length();
            
            // Git blob header: "blob <size>\0"
            String header = "blob " + fileSize + "\0";
            digest.update(header.getBytes("UTF-8"));
            
            // Read file content
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                }
            }
            
            // Convert to hex string
            byte[] hashBytes = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IOException("Failed to calculate SHA hash", e);
        }
    }
    
    /**
     * Syncs mods from the repository using incremental updates.
     * - Deletes local files not present in remote
     * - Skips files with matching SHA hashes
     * - Downloads new or modified files
     * @param callback Progress callback for UI updates
     */
    public static void downloadMods(ProgressCallback callback) {
        SyncStats stats = new SyncStats();
        
        try {
            File modsDir = getModsDirectory();
            
            // Create mods folder if it doesn't exist
            if (!modsDir.exists()) {
                callback.onStatusUpdate("Creating mods folder...");
                if (!modsDir.mkdirs()) {
                    throw new IOException("Failed to create mods directory");
                }
            }

            // Fetch mods list from GitHub
            callback.onStatusUpdate("Fetching mods list from GitHub...");
            List<ModFile> remoteMods = getModsList();
            
            if (remoteMods.isEmpty()) {
                callback.onError("No mods found in repository", null);
                return;
            }
            
            // Build a map of remote files for quick lookup
            Map<String, ModFile> remoteModsMap = new HashMap<>();
            for (ModFile mod : remoteMods) {
                remoteModsMap.put(mod.name, mod);
            }
            
            // Delete local files not in remote
            callback.onStatusUpdate("Checking for files to remove...");
            File[] localFiles = modsDir.listFiles();
            if (localFiles != null) {
                for (File localFile : localFiles) {
                    if (localFile.isFile() && !remoteModsMap.containsKey(localFile.getName())) {
                        Log.i(TAG, "Deleting removed mod: " + localFile.getName());
                        if (localFile.delete()) {
                            stats.deleted++;
                        }
                    }
                }
            }
            
            // Process each remote mod
            int total = remoteMods.size();
            int current = 0;
            
            for (ModFile mod : remoteMods) {
                current++;
                File destFile = new File(modsDir, mod.name);
                
                // Check if file exists and has matching SHA
                if (destFile.exists() && !mod.sha.isEmpty()) {
                    callback.onStatusUpdate("Checking: " + mod.name);
                    try {
                        String localSha = calculateGitBlobSha(destFile);
                        if (localSha.equals(mod.sha)) {
                            Log.i(TAG, "Skipping unchanged: " + mod.name);
                            stats.skipped++;
                            callback.onProgress(current, total, mod.name + " (unchanged)");
                            continue;
                        }
                    } catch (IOException e) {
                        Log.w(TAG, "Failed to calculate SHA for " + mod.name + ", will re-download", e);
                    }
                }
                
                // Download the file
                callback.onProgress(current, total, mod.name);
                Log.i(TAG, "Downloading: " + mod.name + " from " + mod.downloadUrl);
                DownloadUtils.downloadFile(mod.downloadUrl, destFile);
                stats.downloaded++;
            }
            
            callback.onComplete(stats);
            
        } catch (IOException e) {
            Log.e(TAG, "Failed to sync mods", e);
            callback.onError("Failed to sync mods: " + e.getMessage(), e);
        }
    }
    
    /**
     * Simple data class to hold mod file information.
     */
    public static class ModFile {
        public final String name;
        public final String downloadUrl;
        public final String sha;
        
        public ModFile(String name, String downloadUrl, String sha) {
            this.name = name;
            this.downloadUrl = downloadUrl;
            this.sha = sha;
        }
    }
}
