package net.kdt.pojavlaunch.knight;

import android.util.Log;

import net.kdt.pojavlaunch.Tools;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Utility class for applying mods to the game.
 * Replicates the desktop KnightLauncher's mod mounting logic:
 * 1. Rebuild resources (unpack jar bundles to rsrc/)
 * 2. Mount mods (extract mod zips to rsrc/)
 */
public class ModsApplier {
    private static final String TAG = "ModsApplier";

    /**
     * Callback interface for reporting apply progress.
     */
    public interface ProgressCallback {
        void onStatusUpdate(String status);
        void onProgress(int current, int total, String currentItem);
        void onComplete(ApplyStats stats);
        void onError(String error, Throwable throwable);
    }

    /**
     * Stats for the apply operation.
     */
    public static class ApplyStats {
        public int jarsUnpacked = 0;
        public int modsApplied = 0;

        @Override
        public String toString() {
            return "Jars unpacked: " + jarsUnpacked + ", Mods applied: " + modsApplied;
        }
    }

    /**
     * Gets the mods directory path.
     */
    public static File getModsDirectory() {
        return new File(Tools.DIR_GAME_HOME, "mods");
    }

    /**
     * Gets the rsrc directory path where mods are applied.
     */
    public static File getRsrcDirectory() {
        return new File(Tools.DIR_GAME_HOME, "spiral/rsrc");
    }

    /**
     * Gets the spiral directory path.
     */
    public static File getSpiralDirectory() {
        return new File(Tools.DIR_GAME_HOME, "spiral");
    }

    /**
     * Gets the list of mod zip files in the mods directory.
     */
    public static List<File> getModFiles() {
        List<File> mods = new ArrayList<>();
        File modsDir = getModsDirectory();
        
        if (modsDir.exists() && modsDir.isDirectory()) {
            File[] files = modsDir.listFiles((dir, name) -> name.endsWith(".zip"));
            if (files != null) {
                for (File file : files) {
                    mods.add(file);
                }
            }
        }
        
        return mods;
    }

    /**
     * Applies mods to the game.
     * This follows the desktop KnightLauncher logic:
     * 1. Rebuild resources by unpacking jar bundles
     * 2. Extract mod zip files to rsrc directory
     */
    public static void applyMods(ProgressCallback callback) {
        ApplyStats stats = new ApplyStats();

        try {
            File rsrcDir = getRsrcDirectory();
            File spiralDir = getSpiralDirectory();

            // Check if spiral directory exists
            if (!spiralDir.exists()) {
                callback.onError("Game not installed. Please install the game first.", null);
                return;
            }

            // Get mod files
            List<File> modFiles = getModFiles();
            if (modFiles.isEmpty()) {
                callback.onError("No mods found in mods folder. Download mods first.", null);
                return;
            }

            // Step 1: Rebuild resources (unpack jar bundles)
            callback.onStatusUpdate("Rebuilding game resources...");
            stats.jarsUnpacked = rebuildResources(rsrcDir, callback);

            // Step 2: Mount mods (extract zip files to rsrc)
            callback.onStatusUpdate("Applying mods...");
            int total = modFiles.size();
            int current = 0;

            for (File modFile : modFiles) {
                current++;
                callback.onProgress(current, total, modFile.getName());
                Log.i(TAG, "Mounting mod: " + modFile.getName());

                try {
                    extractZipToDirectory(modFile, rsrcDir);
                    stats.modsApplied++;
                } catch (IOException e) {
                    Log.e(TAG, "Failed to extract mod: " + modFile.getName(), e);
                    // Continue with other mods
                }
            }

            callback.onComplete(stats);

        } catch (Exception e) {
            Log.e(TAG, "Failed to apply mods", e);
            callback.onError("Failed to apply mods: " + e.getMessage(), e);
        }
    }

    /**
     * Rebuilds resources by unpacking jar bundles to rsrc directory.
     * This is equivalent to the desktop's rebuildFiles() method.
     */
    private static int rebuildResources(File rsrcDir, ProgressCallback callback) throws IOException {
        int jarsUnpacked = 0;
        
        if (!rsrcDir.exists() || !rsrcDir.isDirectory()) {
            Log.w(TAG, "rsrc directory does not exist");
            return 0;
        }

        // Find all .jar files in rsrc directory (the bundle jars)
        File[] jarFiles = rsrcDir.listFiles((dir, name) -> name.endsWith(".jar"));
        if (jarFiles == null || jarFiles.length == 0) {
            Log.i(TAG, "No jar bundles found to unpack");
            return 0;
        }

        for (File jarFile : jarFiles) {
            callback.onStatusUpdate("Unpacking: " + jarFile.getName());
            Log.i(TAG, "Unpacking jar bundle: " + jarFile.getName());
            
            try {
                extractZipToDirectory(jarFile, rsrcDir);
                jarsUnpacked++;
            } catch (IOException e) {
                Log.e(TAG, "Failed to unpack jar: " + jarFile.getName(), e);
                // Continue with other jars
            }
        }

        return jarsUnpacked;
    }

    /**
     * Extracts a zip/jar file to the target directory.
     */
    private static void extractZipToDirectory(File zipFile, File targetDir) throws IOException {
        byte[] buffer = new byte[8192];

        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                // Skip directories and metadata files
                if (entry.isDirectory()) {
                    zis.closeEntry();
                    continue;
                }

                String entryName = entry.getName();

                // Skip mod.json and mod.png metadata
                if (entryName.equals("mod.json") || entryName.endsWith("/mod.json") ||
                    entryName.equals("mod.png") || entryName.endsWith("/mod.png")) {
                    zis.closeEntry();
                    continue;
                }

                File destFile = new File(targetDir, entryName);

                // Ensure parent directories exist
                File parent = destFile.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }

                // Extract the file
                try (FileOutputStream fos = new FileOutputStream(destFile)) {
                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        fos.write(buffer, 0, len);
                    }
                }

                zis.closeEntry();
            }
        }
    }
}
