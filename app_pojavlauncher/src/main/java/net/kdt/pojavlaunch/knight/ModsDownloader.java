package net.kdt.pojavlaunch.knight;

import android.util.Log;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.utils.DownloadUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for downloading mods from the Spiral Knights Modpack GitHub repository.
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
        void onComplete();
        void onError(String error, Throwable throwable);
    }
    
    /**
     * Gets the mods directory path.
     */
    public static File getModsDirectory() {
        return new File(Tools.DIR_GAME_HOME, "mods");
    }
    
    /**
     * Fetches the list of mod files from the GitHub repository.
     * @return List of ModFile objects containing name and download URL
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
                    mods.add(new ModFile(name, downloadUrl));
                }
            }
        } catch (Exception e) {
            throw new IOException("Failed to fetch mods list from GitHub", e);
        }
        
        return mods;
    }
    
    /**
     * Downloads all mods from the repository to the mods folder.
     * Handles folder deletion/creation and progress reporting.
     * @param callback Progress callback for UI updates
     */
    public static void downloadMods(ProgressCallback callback) {
        try {
            // Delete mods folder if it exists
            File modsDir = getModsDirectory();
            if (modsDir.exists()) {
                callback.onStatusUpdate("Deleting existing mods...");
                org.apache.commons.io.FileUtils.deleteDirectory(modsDir);
            }

            // Create fresh mods folder
            callback.onStatusUpdate("Creating mods folder...");
            if (!modsDir.mkdirs() && !modsDir.exists()) {
                throw new IOException("Failed to create mods directory");
            }

            // Fetch mods list from GitHub
            callback.onStatusUpdate("Fetching mods list from GitHub...");
            List<ModFile> mods = getModsList();
            
            if (mods.isEmpty()) {
                callback.onError("No mods found in repository", null);
                return;
            }
            
            int total = mods.size();
            
            for (int i = 0; i < mods.size(); i++) {
                ModFile mod = mods.get(i);
                callback.onProgress(i + 1, total, mod.name);
                
                File destFile = new File(modsDir, mod.name);
                Log.i(TAG, "Downloading: " + mod.name + " from " + mod.downloadUrl);
                
                DownloadUtils.downloadFile(mod.downloadUrl, destFile);
            }
            
            callback.onComplete();
            
        } catch (IOException e) {
            Log.e(TAG, "Failed to download mods", e);
            callback.onError("Failed to download mods: " + e.getMessage(), e);
        }
    }
    
    /**
     * Simple data class to hold mod file information.
     */
    public static class ModFile {
        public final String name;
        public final String downloadUrl;
        
        public ModFile(String name, String downloadUrl) {
            this.name = name;
            this.downloadUrl = downloadUrl;
        }
    }
}
