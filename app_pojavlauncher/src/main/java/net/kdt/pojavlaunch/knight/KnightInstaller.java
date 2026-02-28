package net.kdt.pojavlaunch.knight;

import net.kdt.pojavlaunch.Tools;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class KnightInstaller implements Runnable {

    private static final String CODE_LINE = "code = ";
    private static final String ARG_LINE = "jvmarg = ";
    private static final String CLASS_LINE = "class = ";
    private static final String GETDOWN_URL = "https://gamemedia2.spiralknights.com/spiral/client/getdown.txt";

    private final Progress pr;
    private final File destination;
    private final File spiral;

    public KnightInstaller(Progress pr) {
        this.pr = pr;
        this.destination = new File(Tools.DIR_GAME_HOME);
        this.spiral = new File(destination, "spiral");
    }

    @Override
    public void run() {
        int progressStep = 0;
        pr.postMaxSteps(3);

        // ── Step 1: Download game files via getdown.txt + digest.txt ──
        try {
            downloadGameFiles();
            pr.postStepProgress(++progressStep);
        } catch (Exception e) {
            pr.postLogLine("Failed to download game files", e);
            pr.setPartIndeterminate(false);
            pr.unlockExit();
            return;
        }

        // ── Step 2: Unpack uresources (handled inside downloadGameFiles now) ──
        // Unpacking is done within downloadGameFiles for resources that were
        // downloaded.
        pr.postStepProgress(++progressStep);

        // ── Step 3: Generate JSON configuration ──
        try {
            generateJsonConfig();
            pr.postStepProgress(++progressStep);
            pr.postLogLine("All done!", null);
            pr.setPartIndeterminate(false);
        } catch (Exception e) {
            pr.postLogLine("Failed to generate JSON", e);
            pr.setPartIndeterminate(false);
            pr.unlockExit();
            return;
        }
        pr.unlockExit();
    }

    /**
     * Downloads all game files using getdown.txt manifest and digest.txt for
     * smart re-download (only files with changed MD5 hashes are re-downloaded).
     */
    private void downloadGameFiles() throws Exception {
        pr.postLogLine("Downloading game files...", null);
        pr.setPartIndeterminate(true);

        spiral.mkdirs();

        // ── Fetch latest getdown.txt ──
        pr.postLogLine("Fetching latest getdown.txt from server...", null);
        File getdownTxt = new File(spiral, "getdown.txt");
        Utils.downloadFile(GETDOWN_URL, getdownTxt, pr);

        // ── Parse getdown.txt ──
        String appbase = null;
        String version = null;
        List<String> codeEntries = new ArrayList<>();
        List<String> resourceEntries = new ArrayList<>();
        List<String> uresourceEntries = new ArrayList<>();

        try (BufferedReader rdr = new BufferedReader(new InputStreamReader(new FileInputStream(getdownTxt)))) {
            String line;
            while ((line = rdr.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("appbase = ")) {
                    appbase = line.substring("appbase = ".length()).trim();
                } else if (line.startsWith("version = ")) {
                    version = line.substring("version = ".length()).trim();
                } else if (line.startsWith("code = ")) {
                    String res = line.substring("code = ".length()).trim();
                    if (!res.startsWith("[")) {
                        codeEntries.add(res);
                    }
                } else if (line.startsWith("resource = ")) {
                    String res = line.substring("resource = ".length()).trim();
                    if (!res.startsWith("[")) {
                        resourceEntries.add(res);
                    }
                } else if (line.startsWith("uresource = ") || line.startsWith("full.uresource = ")) {
                    String res = line.substring(line.indexOf(" = ") + 3).trim();
                    if (!res.startsWith("[")) {
                        uresourceEntries.add(res);
                    }
                }
            }
        }

        if (appbase == null || version == null) {
            throw new IOException("Invalid getdown.txt: missing appbase or version");
        }

        String baseUrl = appbase.replace("%VERSION%", version);
        if (!baseUrl.endsWith("/"))
            baseUrl += "/";

        // ── Fetch remote digest.txt ──
        pr.postLogLine("Fetching digest.txt from server...", null);
        String digestUrl = baseUrl + "digest.txt";
        String remoteDigestContent = Utils.downloadString(digestUrl);
        Map<String, String> remoteDigest = parseDigest(remoteDigestContent);

        // ── Load local digest.txt (if exists) ──
        File localDigestFile = new File(spiral, "digest.txt");
        Map<String, String> localDigest = new HashMap<>();
        if (localDigestFile.exists()) {
            try {
                String localContent = new String(Files.readAllBytes(localDigestFile.toPath()));
                localDigest = parseDigest(localContent);
            } catch (Exception e) {
                pr.postLogLine("Warning: Failed to read local digest, will re-download all", null);
            }
        }

        // ── Build complete file list and determine what needs downloading ──
        // Combine all file entries
        List<String> allFiles = new ArrayList<>();
        allFiles.addAll(codeEntries);
        allFiles.addAll(resourceEntries);
        allFiles.addAll(uresourceEntries);

        // Track which uresources were actually downloaded (for selective unpacking)
        Set<String> downloadedUresources = new HashSet<>();

        // Count files that need downloading
        int totalFiles = allFiles.size();
        int downloadedCount = 0;
        int skippedCount = 0;

        pr.postMaxPart(totalFiles);
        pr.setPartIndeterminate(false);
        int currentFile = 0;

        for (String filePath : allFiles) {
            pr.postPartProgress(currentFile++);

            File localFile = new File(spiral, filePath);
            String remoteHash = remoteDigest.get(filePath);
            String localHash = localDigest.get(filePath);

            // Smart re-download: skip if file exists AND hash matches remote digest
            if (localFile.exists() && remoteHash != null && remoteHash.equals(localHash)) {
                pr.postLogLine("Skipping (unchanged): " + filePath, null);
                skippedCount++;
                continue;
            }

            // Download the file
            pr.postLogLine("Downloading " + filePath, null);
            Utils.downloadFile(baseUrl + filePath, localFile, pr);
            downloadedCount++;

            // Track if this was a uresource that was downloaded
            if (uresourceEntries.contains(filePath)) {
                downloadedUresources.add(filePath);
            }
        }

        // ── Handle getdown-pro-new.jar → getdown-pro.jar rename ──
        File getdownNew = new File(spiral, "code/getdown-pro-new.jar");
        File getdownPro = new File(spiral, "getdown-pro.jar");
        if (getdownNew.exists()) {
            Files.copy(getdownNew.toPath(), getdownPro.toPath(), StandardCopyOption.REPLACE_EXISTING);
            pr.postLogLine("Copied getdown-pro-new.jar → getdown-pro.jar", null);
        } else if (!getdownPro.exists()) {
            // getdown-pro-new.jar is listed as a resource, download it directly
            String getdownNewUrl = baseUrl + "code/getdown-pro-new.jar";
            pr.postLogLine("Downloading getdown-pro-new.jar...", null);
            try {
                Utils.downloadFile(getdownNewUrl, getdownNew, pr);
                Files.copy(getdownNew.toPath(), getdownPro.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception e) {
                pr.postLogLine("Warning: Could not download getdown-pro-new.jar: " + e.getMessage(), null);
            }
        }

        // ── Unpack uresources that were downloaded (changed) ──
        if (!downloadedUresources.isEmpty()) {
            pr.postLogLine("Unpacking updated resources...", null);
            pr.postMaxPart(downloadedUresources.size());
            int unpackIdx = 0;
            for (String res : downloadedUresources) {
                File dest = new File(spiral, res);
                if (dest.exists()) {
                    pr.postLogLine("Unpacking " + res, null);
                    pr.postPartProgress(unpackIdx++);
                    File unpackTarget = spiral;
                    if (res.startsWith("rsrc/")) {
                        unpackTarget = new File(spiral, "rsrc");
                    }
                    unpack(dest, unpackTarget);
                }
            }
        } else {
            // Even if nothing was downloaded, ensure uresources are unpacked
            // (e.g., on first install after rsrc dirs were deleted during update)
            boolean needsUnpack = false;
            for (String res : uresourceEntries) {
                File dest = new File(spiral, res);
                if (dest.exists()) {
                    // Check if the unpack target has content
                    File checkDir = new File(spiral, "rsrc");
                    if (!checkDir.exists() || checkDir.list() == null ||
                            countNonJarFiles(checkDir) == 0) {
                        needsUnpack = true;
                        break;
                    }
                }
            }
            if (needsUnpack) {
                pr.postLogLine("Unpacking all resources...", null);
                pr.postMaxPart(uresourceEntries.size());
                int unpackIdx = 0;
                for (String res : uresourceEntries) {
                    File dest = new File(spiral, res);
                    if (dest.exists()) {
                        pr.postLogLine("Unpacking " + res, null);
                        pr.postPartProgress(unpackIdx++);
                        File unpackTarget = spiral;
                        if (res.startsWith("rsrc/")) {
                            unpackTarget = new File(spiral, "rsrc");
                        }
                        unpack(dest, unpackTarget);
                    }
                }
            }
        }

        // ── Save remote digest.txt locally for future comparison ──
        try (FileOutputStream fos = new FileOutputStream(localDigestFile)) {
            fos.write(remoteDigestContent.getBytes());
        }

        pr.postLogLine("Download complete. (" + downloadedCount + " downloaded, " + skippedCount + " skipped)", null);
    }

    /**
     * Count non-jar files in a directory (used to check if uresources need
     * unpacking).
     */
    private int countNonJarFiles(File dir) {
        int count = 0;
        File[] files = dir.listFiles();
        if (files == null)
            return 0;
        for (File f : files) {
            if (f.isFile() && !f.getName().endsWith(".jar")) {
                count++;
            } else if (f.isDirectory()) {
                count += countNonJarFiles(f);
            }
        }
        return count;
    }

    /**
     * Parse digest.txt content into a map of filename → md5hash.
     * Format: "filename = md5hash" per line.
     */
    private Map<String, String> parseDigest(String content) {
        Map<String, String> digest = new HashMap<>();
        if (content == null)
            return digest;
        String[] lines = content.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#"))
                continue;
            int eqIdx = line.indexOf(" = ");
            if (eqIdx > 0) {
                String fileName = line.substring(0, eqIdx).trim();
                String hash = line.substring(eqIdx + 3).trim();
                digest.put(fileName, hash);
            }
        }
        return digest;
    }

    /**
     * Generate JSON configuration files for the launcher.
     * Parses getdown.txt to build SpiralKnights.json and launcher_profiles.json.
     */
    private void generateJsonConfig() throws Exception {
        pr.postLogLine("Generating JSON...", null);
        pr.postMaxPart(1);
        pr.postPartProgress(0);
        pr.setPartIndeterminate(true);

        List<String> codeJars = new ArrayList<>();
        List<String> jvmArgs = new ArrayList<>();
        String mainClass = null;

        File getdownTxt = new File(spiral, "getdown.txt");
        if (!getdownTxt.exists()) {
            pr.postLogLine("Can't find getdown.txt", null);
            pr.setPartIndeterminate(false);
            pr.unlockExit();
            return;
        }

        // Parse getdown.txt
        BufferedReader rdr = new BufferedReader(new InputStreamReader(new FileInputStream(getdownTxt)));
        String line;
        while ((line = rdr.readLine()) != null) {
            if (line.startsWith(CODE_LINE)) {
                String codeJar = line.substring(CODE_LINE.length());
                // Skip jinput and lwjgl as they're provided by PojavLauncher
                if (!codeJar.contains("jinput") && !codeJar.contains("lwjgl")) {
                    codeJars.add(codeJar);
                }
            } else if (line.startsWith(ARG_LINE)) {
                String arg = line.substring(ARG_LINE.length());
                // Skip memory args, library path, bracketed args, module-system flags (we add
                // our own),
                // and deprecated JRE flags
                if (!arg.startsWith("-Xm") && !arg.startsWith("-Djava.library.path") && !arg.startsWith("[")
                        && !arg.startsWith("--add-opens") && !arg.startsWith("--enable-native-access")
                        && !arg.contains("AggressiveOpts") && !arg.contains("UseParallelOldGC")) {
                    jvmArgs.add(arg);
                }
            } else if (line.startsWith(CLASS_LINE)) {
                mainClass = line.substring(CLASS_LINE.length());
            }
        }
        rdr.close();

        // Add required LWJGL arg for Android
        jvmArgs.add("-Dorg.lwjgl.opengl.disableStaticInit=true");

        // Copy libraries and build JSON
        JSONObject outputJson = new JSONObject();
        outputJson.put("minecraftArguments", "");

        for (String jarPath : codeJars) {
            File source = new File(spiral, jarPath);
            if (!source.exists()) {
                pr.postLogLine("Warning: " + jarPath + " not found, skipping", null);
                continue;
            }

            String fileName = source.getName();
            String extension = fileName.substring(fileName.lastIndexOf("."));
            String libName = fileName.substring(0, fileName.lastIndexOf("."));
            String mavenName = "spiral:" + libName + ":0.0";

            // Copy to libraries folder
            File libDestination = new File(destination,
                    "libraries/spiral/" + libName + "/0.0/" + libName + "-0.0" + extension);
            libDestination.getParentFile().mkdirs();
            Files.copy(source.toPath(), libDestination.toPath(), StandardCopyOption.REPLACE_EXISTING);

            // Add to JSON
            JSONObject library = new JSONObject();
            library.put("name", mavenName);
            outputJson.append("libraries", library);
        }

        outputJson.put("id", "SpiralKnights");
        outputJson.put("releaseTime", "2009-05-13T20:11:00+00:00");
        outputJson.put("time", "2009-05-13T20:11:00+00:00");
        outputJson.put("type", "release");
        outputJson.put("mainClass", mainClass);

        // Write SpiralKnights.json
        File versionPath = new File(destination, "versions/SpiralKnights/SpiralKnights.json");
        versionPath.getParentFile().mkdirs();
        try (FileOutputStream fos = new FileOutputStream(versionPath)) {
            fos.write(outputJson.toString().getBytes());
        }

        // Update launcher_profiles.json
        String sprofiles = null;
        String b64Default = null;

        try {
            byte[] bprofiles = Files.readAllBytes(new File(destination, "launcher_profiles.json").toPath());
            sprofiles = new String(bprofiles, 0, bprofiles.length);
        } catch (Exception ignored) {
        }

        try {
            File iconFile = new File(spiral, "desktop.png");
            if (iconFile.exists()) {
                b64Default = Base64.getEncoder().encodeToString(Files.readAllBytes(iconFile.toPath()));
            }
        } catch (Exception ignored) {
        }

        JSONObject profiles = sprofiles == null ? new JSONObject() : new JSONObject(sprofiles);
        JSONObject spiralKnightsProfile = new JSONObject();

        // Build JVM args string
        StringBuilder sb = new StringBuilder();
        int sz = jvmArgs.size();
        for (int i = 0; i < sz; i++) {
            sb.append(jvmArgs.get(i).replace("%APPDIR%", "./spiral/"));
            if (i < sz - 1) {
                sb.append(" ");
            }
        }

        spiralKnightsProfile.put("javaArgs", sb.toString());
        spiralKnightsProfile.put("lastVersionId", "SpiralKnights");
        spiralKnightsProfile.put("name", "Spiral Knights");
        if (b64Default != null) {
            spiralKnightsProfile.put("icon", "data:image/png;base64," + b64Default);
        }

        if (profiles.has("profiles")) {
            profiles.getJSONObject("profiles").put("SpiralKnights", spiralKnightsProfile);
        } else {
            JSONObject newProfiles = new JSONObject();
            newProfiles.put("SpiralKnights", spiralKnightsProfile);
            profiles.put("profiles", newProfiles);
        }

        Files.write(new File(destination, "launcher_profiles.json").toPath(), profiles.toString().getBytes());
        pr.setPartIndeterminate(false);
    }

    private void unpack(File zipFile, File targetDir) throws IOException {
        unpackStatic(zipFile, targetDir);
    }

    private static void unpackStatic(File zipFile, File targetDir) throws IOException {
        try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(new FileInputStream(zipFile))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                File dest = new File(targetDir, entry.getName());
                if (entry.isDirectory()) {
                    dest.mkdirs();
                } else {
                    dest.getParentFile().mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(dest)) {
                        byte[] buf = new byte[4096];
                        int len;
                        while ((len = zis.read(buf)) > 0) {
                            fos.write(buf, 0, len);
                        }
                    }
                }
                zis.closeEntry();
            }
        }
    }

    /**
     * Unpacks the game resource jars without re-downloading.
     * This is used for the reinstall functionality.
     * 
     * Unpacks:
     * - spiral/rsrc/*.jar files (full-music-bundle.jar, full-rest-bundle.jar,
     * intro-bundle.jar) -> spiral/rsrc/
     * - spiral/crucible.jar -> spiral/
     */
    public static void unpackResources(Progress pr) throws IOException {
        File destination = new File(Tools.DIR_GAME_HOME);
        File spiral = new File(destination, "spiral");
        File rsrcDir = new File(spiral, "rsrc");

        pr.postLogLine("Unpacking game resources...", null);
        pr.setPartIndeterminate(true);

        int totalJars = 0;
        int currentJar = 0;

        // Count jars to unpack
        File crucibleJar = new File(spiral, "crucible.jar");
        if (crucibleJar.exists())
            totalJars++;

        if (rsrcDir.exists() && rsrcDir.isDirectory()) {
            File[] rsrcFiles = rsrcDir.listFiles((dir, name) -> name.endsWith(".jar"));
            if (rsrcFiles != null) {
                totalJars += rsrcFiles.length;
            }
        }

        if (totalJars == 0) {
            pr.postLogLine("No resource jars found to unpack", new IOException("No jars found"));
            return;
        }

        pr.postMaxPart(totalJars);
        pr.setPartIndeterminate(false);

        // Unpack rsrc jars
        if (rsrcDir.exists() && rsrcDir.isDirectory()) {
            File[] rsrcFiles = rsrcDir.listFiles((dir, name) -> name.endsWith(".jar"));
            if (rsrcFiles != null) {
                for (File jarFile : rsrcFiles) {
                    pr.postLogLine("Unpacking " + jarFile.getName(), null);
                    pr.postPartProgress(currentJar++);
                    try {
                        unpackStatic(jarFile, rsrcDir);
                    } catch (IOException e) {
                        pr.postLogLine("Failed to unpack " + jarFile.getName(), e);
                        throw e;
                    }
                }
            }
        }

        // Unpack crucible.jar
        if (crucibleJar.exists()) {
            pr.postLogLine("Unpacking crucible.jar", null);
            pr.postPartProgress(currentJar++);
            try {
                unpackStatic(crucibleJar, spiral);
            } catch (IOException e) {
                pr.postLogLine("Failed to unpack crucible.jar", e);
                throw e;
            }
        }

        pr.postLogLine("Resource repacking complete!", null);
        pr.unlockExit();
    }
}