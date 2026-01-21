package net.kdt.pojavlaunch.knight;

import android.util.Log;

import net.kdt.pojavlaunch.Tools;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Utility class for applying mods to the game.
 * Replicates the desktop KnightLauncher's mod mounting logic:
 * 1. Rebuild resources (unpack jar bundles to rsrc/)
 * 2. Mount mods:
 *    - Resource mods (type=null) -> extracted to rsrc/
 *    - Class mods (type="class") -> extracted to code/class-changes/, then merged into config.jar
 * 3. Apply locale changes to projectx-config.jar
 * 
 * Includes compatibility features:
 * - pxVersion validation for class mods
 * - File header protection (protected game files)
 * - Locale changes support
 * - Modpack (.modpack) support
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
        void onWarning(String modName, String warning);
    }

    /**
     * Stats for the apply operation.
     */
    public static class ApplyStats {
        public int jarsUnpacked = 0;
        public int resourceModsApplied = 0;
        public int classModsApplied = 0;
        public int modpacksApplied = 0;
        public int modsSkipped = 0;
        public int localeChangesApplied = 0;
        public List<String> warnings = new ArrayList<>();

        public int getTotalModsApplied() {
            return resourceModsApplied + classModsApplied + modpacksApplied;
        }

        @Override
        public String toString() {
            return "Jars unpacked: " + jarsUnpacked + 
                   ", Resource mods: " + resourceModsApplied + 
                   ", Class mods: " + classModsApplied +
                   ", Modpacks: " + modpacksApplied +
                   ", Skipped: " + modsSkipped +
                   ", Locale changes: " + localeChangesApplied;
        }
    }

    /**
     * Represents mod metadata parsed from mod.json.
     */
    public static class ModMetadata {
        public String name;
        public String type;
        public String pxVersion;
        public Map<String, Map<String, String>> locale; // bundle -> (key -> value)
        
        public boolean isClassMod() {
            return "class".equalsIgnoreCase(type);
        }
        
        public boolean hasLocaleChanges() {
            return locale != null && !locale.isEmpty();
        }
    }

    /**
     * Protected file paths that should not be overwritten by mods.
     * Based on desktop KnightLauncher's FILTER_LIST.
     */
    private static final Set<String> PROTECTED_FILES = new HashSet<>();
    static {
        // Config files that affect game behavior
        String[] protectedPaths = {
            "config/accessory.dat", "config/accessory.xml",
            "config/actor.dat", "config/actor.xml",
            "config/area.dat", "config/area.xml",
            "config/attack.dat", "config/attack.xml",
            "config/battle_sprite.dat", "config/battle_sprite.xml",
            "config/catalog.dat", "config/catalog.xml",
            "config/conversation.dat", "config/conversation.xml",
            "config/cursor.dat", "config/cursor.xml",
            "config/depot_catalog.dat", "config/depot_catalog.xml",
            "config/depth_scale.dat", "config/depth_scale.xml",
            "config/description.dat", "config/description.xml",
            "config/effect.dat", "config/effect.xml",
            "config/emote.dat", "config/emote.xml",
            "config/event.dat", "config/event.xml",
            "config/fire_action.dat", "config/fire_action.xml",
            "config/font.dat", "config/font.xml",
            "config/forge_property.dat", "config/forge_property.xml",
            "config/gift.dat", "config/gift.xml",
            "config/ground.dat", "config/ground.xml",
            "config/harness.dat", "config/harness.xml",
            "config/interact.dat", "config/interact.xml",
            "config/interface_script.dat", "config/interface_script.xml",
            "config/item.dat", "config/item.xml",
            "config/item_depth_weight.dat", "config/item_depth_weight.xml",
            "config/item_property.dat", "config/item_property.xml",
            "config/level_table.dat", "config/level_table.xml",
            "config/material.dat", "config/material.xml",
            "config/mission.dat", "config/mission.xml",
            "config/mission_group.dat", "config/mission_group.xml",
            "config/mission_property.dat", "config/mission_property.xml",
            "config/parameterized_handler.dat", "config/parameterized_handler.xml",
            "config/path.dat", "config/path.xml",
            "config/placeable.dat", "config/placeable.xml",
            "config/recipe.dat", "config/recipe.xml",
            "config/recipe_property.dat", "config/recipe_property.xml",
            "config/render_effect.dat", "config/render_effect.xml",
            "config/render_queue.dat", "config/render_queue.xml",
            "config/render_scheme.dat", "config/render_scheme.xml",
            "config/scene_global.dat", "config/scene_global.xml",
            "config/shader.dat", "config/shader.xml",
            "config/sounder.dat", "config/sounder.xml",
            "config/status_condition.dat", "config/status_condition.xml",
            "config/status_effect.dat", "config/status_effect.xml",
            "config/tileset.dat", "config/tileset.xml",
            "config/toy.dat", "config/toy.xml",
            "config/vfx.dat", "config/vfx.xml",
            "config/view.dat", "config/view.xml",
            "config/weapon.dat", "config/weapon.xml"
        };
        for (String path : protectedPaths) {
            PROTECTED_FILES.add(path);
        }
    }

    // Global locale changes collected from all mods
    private static final Map<String, Map<String, String>> globalLocaleChanges = new HashMap<>();

    /**
     * Gets the mods directory path.
     */
    public static File getModsDirectory() {
        return new File(Tools.DIR_GAME_HOME, "mods");
    }

    /**
     * Gets the rsrc directory path where resource mods are applied.
     */
    public static File getRsrcDirectory() {
        return new File(Tools.DIR_GAME_HOME, "spiral/rsrc");
    }

    /**
     * Gets the code/class-changes directory path where class mods are applied.
     */
    public static File getClassChangesDirectory() {
        return new File(Tools.DIR_GAME_HOME, "spiral/code/class-changes");
    }

    /**
     * Gets the code/locale-changes directory path for locale patching.
     */
    public static File getLocaleChangesDirectory() {
        return new File(Tools.DIR_GAME_HOME, "spiral/code/locale-changes");
    }

    /**
     * Gets the code directory path.
     */
    public static File getCodeDirectory() {
        return new File(Tools.DIR_GAME_HOME, "spiral/code");
    }

    /**
     * Gets the spiral directory path.
     */
    public static File getSpiralDirectory() {
        return new File(Tools.DIR_GAME_HOME, "spiral");
    }

    /**
     * Gets the current game version from getdown.txt.
     */
    public static String getGameVersion() {
        File getdownFile = new File(Tools.DIR_GAME_HOME, "spiral/getdown.txt");
        if (!getdownFile.exists()) {
            return null;
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(getdownFile)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("version = ")) {
                    return line.substring("version = ".length()).trim();
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to read game version", e);
        }
        return null;
    }

    /**
     * Gets the list of mod files in the mods directory (.zip and .modpack).
     */
    public static List<File> getModFiles() {
        List<File> mods = new ArrayList<>();
        File modsDir = getModsDirectory();
        
        if (modsDir.exists() && modsDir.isDirectory()) {
            File[] files = modsDir.listFiles((dir, name) -> 
                name.endsWith(".zip") || name.endsWith(".modpack"));
            if (files != null) {
                for (File file : files) {
                    mods.add(file);
                }
            }
        }
        
        return mods;
    }

    /**
     * Parses mod metadata from mod.json inside the zip file.
     */
    private static ModMetadata parseModMetadata(File modFile) {
        ModMetadata metadata = new ModMetadata();
        metadata.name = modFile.getName();

        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(modFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String entryName = entry.getName();
                if (entryName.equals("mod.json") || entryName.endsWith("/mod.json")) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(zis));
                    StringBuilder content = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        content.append(line);
                    }
                    
                    JSONObject json = new JSONObject(content.toString());
                    
                    // Handle both { "mod": {...} } and direct format
                    JSONObject modObj = json.has("mod") ? json.getJSONObject("mod") : json;
                    
                    if (modObj.has("name")) {
                        metadata.name = modObj.getString("name");
                    }
                    if (modObj.has("type")) {
                        metadata.type = modObj.getString("type");
                    }
                    if (modObj.has("pxVersion")) {
                        metadata.pxVersion = modObj.getString("pxVersion");
                    }
                    
                    // Parse locale changes
                    if (modObj.has("locale")) {
                        metadata.locale = new HashMap<>();
                        JSONObject localeObj = modObj.getJSONObject("locale");
                        for (String bundle : localeKeys(localeObj)) {
                            JSONObject bundleObj = localeObj.getJSONObject(bundle);
                            Map<String, String> changes = new HashMap<>();
                            for (String key : localeKeys(bundleObj)) {
                                changes.put(key, bundleObj.getString(key));
                            }
                            metadata.locale.put(bundle, changes);
                        }
                    }
                    break;
                }
                zis.closeEntry();
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to parse mod metadata from " + modFile.getName(), e);
        }
        
        return metadata;
    }

    /**
     * Helper to get keys from JSONObject.
     */
    private static Iterable<String> localeKeys(JSONObject obj) {
        List<String> keys = new ArrayList<>();
        java.util.Iterator<String> it = obj.keys();
        while (it.hasNext()) {
            keys.add(it.next());
        }
        return keys;
    }

    /**
     * Checks if a class mod is compatible with the current game version.
     */
    private static boolean isClassModCompatible(ModMetadata metadata, String gameVersion) {
        if (metadata.pxVersion == null || metadata.pxVersion.isEmpty()) {
            // No version specified, allow but warn
            return true;
        }
        if (gameVersion == null) {
            // Can't determine game version, allow
            return true;
        }
        return metadata.pxVersion.equalsIgnoreCase(gameVersion);
    }

    /**
     * Checks if a file path is protected.
     */
    private static boolean isProtectedFile(String path) {
        return PROTECTED_FILES.contains(path);
    }

    /**
     * Applies mods to the game with full compatibility checking.
     */
    public static void applyMods(ProgressCallback callback) {
        ApplyStats stats = new ApplyStats();
        globalLocaleChanges.clear();

        try {
            File rsrcDir = getRsrcDirectory();
            File classChangesDir = getClassChangesDirectory();
            File codeDir = getCodeDirectory();
            File spiralDir = getSpiralDirectory();

            // Check if spiral directory exists
            if (!spiralDir.exists()) {
                callback.onError("Game not installed. Please install the game first.", null);
                return;
            }

            // Get game version for compatibility check
            String gameVersion = getGameVersion();
            Log.i(TAG, "Game version: " + gameVersion);

            // Get mod files
            List<File> modFiles = getModFiles();
            if (modFiles.isEmpty()) {
                callback.onError("No mods found in mods folder. Download mods first.", null);
                return;
            }

            // Create class-changes directory if it doesn't exist
            if (!classChangesDir.exists()) {
                classChangesDir.mkdirs();
            }

            // Step 1: Rebuild resources (unpack jar bundles)
            callback.onStatusUpdate("Rebuilding game resources...");
            stats.jarsUnpacked = rebuildResources(rsrcDir, callback);

            // Step 2: Mount mods based on their type
            callback.onStatusUpdate("Applying mods...");
            int total = modFiles.size();
            int current = 0;
            boolean hasClassMods = false;

            for (File modFile : modFiles) {
                current++;
                ModMetadata metadata = parseModMetadata(modFile);
                boolean isModpack = modFile.getName().endsWith(".modpack");
                
                String modTypeLabel;
                if (isModpack) {
                    modTypeLabel = " [modpack]";
                } else if (metadata.isClassMod()) {
                    modTypeLabel = " [class]";
                } else {
                    modTypeLabel = " [resource]";
                }
                
                callback.onProgress(current, total, metadata.name + modTypeLabel);
                Log.i(TAG, "Processing mod: " + metadata.name + " (type: " + metadata.type + 
                          ", pxVersion: " + metadata.pxVersion + ")");

                try {
                    if (isModpack) {
                        // Modpack: extract and process inner mods
                        int applied = applyModpack(modFile, rsrcDir, classChangesDir, gameVersion, 
                                                   stats, callback);
                        if (applied > 0) {
                            stats.modpacksApplied++;
                        }
                    } else if (metadata.isClassMod()) {
                        // Class mod: check pxVersion compatibility
                        if (!isClassModCompatible(metadata, gameVersion)) {
                            String warning = "Class mod '" + metadata.name + 
                                "' requires game version " + metadata.pxVersion + 
                                " but current is " + gameVersion + " - SKIPPED";
                            Log.w(TAG, warning);
                            stats.warnings.add(warning);
                            stats.modsSkipped++;
                            callback.onWarning(metadata.name, "Incompatible game version");
                            continue;
                        }
                        
                        extractZipToDirectory(modFile, classChangesDir, false);
                        stats.classModsApplied++;
                        hasClassMods = true;
                        Log.i(TAG, "Class mod mounted to code/class-changes/");
                    } else {
                        // Resource mod: extract to rsrc/ (no file protection by default)
                        extractZipToDirectory(modFile, rsrcDir, false);
                        stats.resourceModsApplied++;
                        Log.i(TAG, "Resource mod mounted to rsrc/");
                    }
                    
                    // Collect locale changes
                    if (metadata.hasLocaleChanges()) {
                        for (Map.Entry<String, Map<String, String>> entry : metadata.locale.entrySet()) {
                            String bundle = entry.getKey();
                            if (!globalLocaleChanges.containsKey(bundle)) {
                                globalLocaleChanges.put(bundle, new HashMap<>());
                            }
                            globalLocaleChanges.get(bundle).putAll(entry.getValue());
                            stats.localeChangesApplied += entry.getValue().size();
                        }
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Failed to extract mod: " + modFile.getName(), e);
                    stats.warnings.add("Failed to apply " + metadata.name + ": " + e.getMessage());
                }
            }

            // Step 3: If class mods were applied, merge them into config.jar
            if (hasClassMods) {
                callback.onStatusUpdate("Merging class changes into config.jar...");
                mergeClassChangesIntoConfigJar(codeDir, classChangesDir, callback);
            }

            // Step 4: Apply locale changes to projectx-config.jar
            if (!globalLocaleChanges.isEmpty()) {
                callback.onStatusUpdate("Applying locale changes...");
                applyLocaleChanges(codeDir, callback);
            }

            // Clean up class-changes directory
            if (classChangesDir.exists()) {
                deleteDirectory(classChangesDir);
            }

            // Clean up mod metadata files that may have been extracted
            cleanModMetadata(rsrcDir);

            callback.onComplete(stats);

        } catch (Exception e) {
            Log.e(TAG, "Failed to apply mods", e);
            callback.onError("Failed to apply mods: " + e.getMessage(), e);
        }
    }

    /**
     * Applies a modpack by extracting and processing its inner mods.
     */
    private static int applyModpack(File modpackFile, File rsrcDir, File classChangesDir,
            String gameVersion, ApplyStats stats, ProgressCallback callback) throws IOException {
        int applied = 0;
        File tempDir = new File(getModsDirectory(), ".modpack_temp");
        
        try {
            // Extract modpack to temp directory
            if (!tempDir.exists()) {
                tempDir.mkdirs();
            }
            extractZipToDirectory(modpackFile, tempDir, false);
            
            // Process each mod inside the modpack
            File[] innerMods = tempDir.listFiles((dir, name) -> name.endsWith(".zip"));
            if (innerMods != null) {
                for (File innerMod : innerMods) {
                    ModMetadata metadata = parseModMetadata(innerMod);
                    
                    if (metadata.isClassMod()) {
                        if (!isClassModCompatible(metadata, gameVersion)) {
                            stats.modsSkipped++;
                            continue;
                        }
                        extractZipToDirectory(innerMod, classChangesDir, true);
                        stats.classModsApplied++;
                    } else {
                        extractZipToDirectory(innerMod, rsrcDir, true);
                        stats.resourceModsApplied++;
                    }
                    applied++;
                }
            }
        } finally {
            // Clean up temp directory
            if (tempDir.exists()) {
                deleteDirectory(tempDir);
            }
        }
        
        return applied;
    }

    /**
     * Applies locale changes to projectx-config.jar.
     * This replicates desktop KnightLauncher's locale change mounting.
     */
    private static void applyLocaleChanges(File codeDir, ProgressCallback callback) throws IOException {
        File projectxConfig = new File(codeDir, "projectx-config.jar");
        File localeChangesDir = getLocaleChangesDirectory();
        
        if (!projectxConfig.exists()) {
            Log.w(TAG, "projectx-config.jar not found, skipping locale changes");
            return;
        }

        Log.i(TAG, "Applying " + globalLocaleChanges.size() + " locale bundles...");
        
        try {
            // Create locale changes directory
            if (!localeChangesDir.exists()) {
                localeChangesDir.mkdirs();
            }

            // Extract projectx-config.jar
            callback.onStatusUpdate("Extracting projectx-config.jar...");
            extractJarToDirectoryFull(projectxConfig, localeChangesDir);

            // Apply locale changes to properties files
            for (Map.Entry<String, Map<String, String>> bundleEntry : globalLocaleChanges.entrySet()) {
                String bundlePath = bundleEntry.getKey();
                Map<String, String> changes = bundleEntry.getValue();
                
                File propsFile = new File(localeChangesDir, "rsrc/i18n/" + bundlePath);
                if (!propsFile.exists()) {
                    Log.w(TAG, "Locale bundle not found: " + bundlePath);
                    continue;
                }
                
                // Load existing properties
                Properties props = new Properties();
                try (FileInputStream fis = new FileInputStream(propsFile)) {
                    props.load(fis);
                }
                
                // Apply changes
                for (Map.Entry<String, String> change : changes.entrySet()) {
                    props.setProperty(change.getKey(), change.getValue());
                }
                
                // Save modified properties
                try (FileOutputStream fos = new FileOutputStream(propsFile)) {
                    props.store(fos, null);
                }
                
                Log.i(TAG, "Applied " + changes.size() + " changes to " + bundlePath);
            }

            // Repack projectx-config.jar
            callback.onStatusUpdate("Repacking projectx-config.jar...");
            File configNewJar = new File(codeDir, "projectx-config-new.jar");
            File configOldJar = new File(codeDir, "projectx-config-old.jar");
            
            createJarFromDirectory(localeChangesDir, configNewJar);
            
            // Replace original
            if (configOldJar.exists()) configOldJar.delete();
            if (projectxConfig.renameTo(configOldJar)) {
                if (configNewJar.renameTo(projectxConfig)) {
                    configOldJar.delete();
                    Log.i(TAG, "Locale changes applied to projectx-config.jar");
                } else {
                    configOldJar.renameTo(projectxConfig);
                    throw new IOException("Failed to rename projectx-config-new.jar");
                }
            }
        } finally {
            // Clean up locale changes directory
            if (localeChangesDir.exists()) {
                deleteDirectory(localeChangesDir);
            }
        }
    }

    /**
     * Merges class changes into config.jar following desktop KnightLauncher logic.
     */
    private static void mergeClassChangesIntoConfigJar(File codeDir, File classChangesDir, 
            ProgressCallback callback) throws IOException {
        File configJar = new File(codeDir, "config.jar");
        File configNewJar = new File(codeDir, "config-new.jar");
        File configOldJar = new File(codeDir, "config-old.jar");

        if (!configJar.exists()) {
            Log.w(TAG, "config.jar not found, skipping class mod merge");
            return;
        }

        Log.i(TAG, "Mounting class changes into config.jar...");

        // Step 1: Unpack current config.jar into class-changes/ (mod files will override)
        callback.onStatusUpdate("Unpacking config.jar...");
        extractJarToDirectory(configJar, classChangesDir);

        // Step 2: Create new config.jar from merged contents
        callback.onStatusUpdate("Creating new config.jar...");
        createJarFromDirectory(classChangesDir, configNewJar);

        // Step 3: Replace original config.jar
        if (configOldJar.exists()) {
            configOldJar.delete();
        }

        if (configJar.renameTo(configOldJar)) {
            if (configNewJar.renameTo(configJar)) {
                configOldJar.delete();
                Log.i(TAG, "Class changes merged into config.jar successfully");
            } else {
                configOldJar.renameTo(configJar);
                throw new IOException("Failed to rename config-new.jar to config.jar");
            }
        } else {
            throw new IOException("Failed to rename config.jar to config-old.jar");
        }
    }

    /**
     * Extracts a jar file to the target directory.
     * Mod files already present take precedence.
     */
    private static void extractJarToDirectory(File jarFile, File targetDir) throws IOException {
        byte[] buffer = new byte[8192];

        try (JarInputStream jis = new JarInputStream(new FileInputStream(jarFile))) {
            JarEntry entry;
            while ((entry = jis.getNextJarEntry()) != null) {
                if (entry.isDirectory()) {
                    new File(targetDir, entry.getName()).mkdirs();
                    continue;
                }

                File destFile = new File(targetDir, entry.getName());

                File parent = destFile.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }

                // Only extract if file doesn't exist (mod files take precedence)
                if (!destFile.exists()) {
                    try (FileOutputStream fos = new FileOutputStream(destFile)) {
                        int len;
                        while ((len = jis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }

                jis.closeEntry();
            }
        }
    }

    /**
     * Extracts a jar file to the target directory, overwriting existing files.
     */
    private static void extractJarToDirectoryFull(File jarFile, File targetDir) throws IOException {
        byte[] buffer = new byte[8192];

        try (JarInputStream jis = new JarInputStream(new FileInputStream(jarFile))) {
            JarEntry entry;
            while ((entry = jis.getNextJarEntry()) != null) {
                if (entry.isDirectory()) {
                    new File(targetDir, entry.getName()).mkdirs();
                    continue;
                }

                File destFile = new File(targetDir, entry.getName());

                File parent = destFile.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }

                try (FileOutputStream fos = new FileOutputStream(destFile)) {
                    int len;
                    while ((len = jis.read(buffer)) > 0) {
                        fos.write(buffer, 0, len);
                    }
                }

                jis.closeEntry();
            }
        }
    }

    /**
     * Creates a jar file from a directory's contents.
     */
    private static void createJarFromDirectory(File sourceDir, File jarFile) throws IOException {
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jarFile))) {
            addDirectoryToJar(sourceDir, sourceDir, jos);
        }
    }

    /**
     * Recursively adds directory contents to a jar output stream.
     */
    private static void addDirectoryToJar(File baseDir, File currentDir, JarOutputStream jos) 
            throws IOException {
        File[] files = currentDir.listFiles();
        if (files == null) return;

        byte[] buffer = new byte[8192];

        for (File file : files) {
            String entryName = baseDir.toURI().relativize(file.toURI()).getPath();
            
            if (file.isDirectory()) {
                if (!entryName.endsWith("/")) {
                    entryName += "/";
                }
                JarEntry entry = new JarEntry(entryName);
                jos.putNextEntry(entry);
                jos.closeEntry();
                
                addDirectoryToJar(baseDir, file, jos);
            } else {
                // Skip mod metadata files
                if (entryName.equals("mod.json") || entryName.equals("mod.png")) {
                    continue;
                }

                JarEntry entry = new JarEntry(entryName);
                jos.putNextEntry(entry);

                try (FileInputStream fis = new FileInputStream(file)) {
                    int len;
                    while ((len = fis.read(buffer)) > 0) {
                        jos.write(buffer, 0, len);
                    }
                }

                jos.closeEntry();
            }
        }
    }

    /**
     * Recursively deletes a directory and its contents.
     */
    private static void deleteDirectory(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    file.delete();
                }
            }
        }
        dir.delete();
    }

    /**
     * Cleans mod metadata files that may have been extracted.
     */
    private static void cleanModMetadata(File rsrcDir) {
        new File(rsrcDir, "mod.json").delete();
        new File(rsrcDir, "mod.png").delete();
    }

    /**
     * Rebuilds resources by unpacking jar bundles to rsrc directory.
     */
    private static int rebuildResources(File rsrcDir, ProgressCallback callback) throws IOException {
        int jarsUnpacked = 0;
        
        if (!rsrcDir.exists() || !rsrcDir.isDirectory()) {
            Log.w(TAG, "rsrc directory does not exist");
            return 0;
        }

        File[] jarFiles = rsrcDir.listFiles((dir, name) -> name.endsWith(".jar"));
        if (jarFiles == null || jarFiles.length == 0) {
            Log.i(TAG, "No jar bundles found to unpack");
            return 0;
        }

        for (File jarFile : jarFiles) {
            callback.onStatusUpdate("Unpacking: " + jarFile.getName());
            Log.i(TAG, "Unpacking jar bundle: " + jarFile.getName());
            
            try {
                extractZipToDirectory(jarFile, rsrcDir, false);
                jarsUnpacked++;
            } catch (IOException e) {
                Log.e(TAG, "Failed to unpack jar: " + jarFile.getName(), e);
            }
        }

        return jarsUnpacked;
    }

    /**
     * Extracts a zip/jar file to the target directory.
     * @param checkProtected If true, skip protected files
     */
    private static void extractZipToDirectory(File zipFile, File targetDir, boolean checkProtected) 
            throws IOException {
        byte[] buffer = new byte[8192];

        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
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

                // Check protected files
                if (checkProtected && isProtectedFile(entryName)) {
                    Log.w(TAG, "Skipping protected file: " + entryName);
                    zis.closeEntry();
                    continue;
                }

                File destFile = new File(targetDir, entryName);

                File parent = destFile.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }

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
