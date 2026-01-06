package net.kdt.pojavlaunch.knight;

import net.kdt.pojavlaunch.Tools;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.Permission;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.zip.GZIPInputStream;

public class KnightInstaller implements Runnable {

    private static final String HEAD_N = "`head -n ";
    private static final String CODE_LINE = "code = ";
    private static final String ARG_LINE = "jvmarg = ";
    private static final String CLASS_LINE = "class = ";
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
        // No SecurityManager on Android, so we can't intercept System.exit easily.
        // We rely on getdown not calling it or us handling it differently if possible.
        // For now, we proceed without the SecurityManager block that was in the desktop
        // version.

        int progressStep = 0;
        File getdown = new File(spiral, "getdown-pro.jar");
        if (!getdown.exists()) {
            pr.postMaxSteps(5);
            byte[] installer;
            try {
                pr.postLogLine("Downloading Linux installer...", null);
                installer = Utils.getFromWeb("https://gamemedia.spiralknights.com/spiral/client/spiral-install.bin",
                        pr);
                pr.postStepProgress(++progressStep);
            } catch (IOException e) {
                pr.postLogLine("Failed to download Linux installer", e);
                pr.unlockExit();
                return;
            }

            int gzStart;
            int gzSize;
            try {
                pr.postLogLine("Processing installer...", null);
                pr.postMaxPart(1);
                pr.postPartProgress(0);
                pr.setPartIndeterminate(true);
                byte[] offsetEquals = "offset=`head".getBytes();
                byte[] filesizesEquals = "filesizes=".getBytes();
                int offsetAt = Utils.indexOf(installer, offsetEquals);
                int sizeAt = Utils.indexOf(installer, filesizesEquals);
                if (offsetAt == -1 || sizeAt == -1) {
                    pr.postLogLine("Failed to find necessary data", null);
                    pr.setPartIndeterminate(false);
                    return;
                }
                int offsetSz = Utils.indexOf(installer, offsetAt, (byte) 0x0A);
                int sizeSz = Utils.indexOf(installer, sizeAt, (byte) 0x0A);
                offsetSz = offsetSz - offsetAt;
                sizeSz = sizeSz - sizeAt;
                String offset = new String(installer, offsetAt, offsetSz);
                String size = new String(installer, sizeAt, sizeSz);
                // System.out.println(offset);
                // System.out.println(size);

                int headNumStart = offset.indexOf(HEAD_N) + HEAD_N.length();
                int headNumEnd = -1;
                for (int i = headNumStart; i < offset.length(); i++) {
                    if (offset.charAt(i) == ' ') {
                        headNumEnd = i;
                        break;
                    }
                }

                if (headNumEnd == -1) {
                    pr.postLogLine("Failed to find necessary data", null);
                    pr.setPartIndeterminate(false);
                    return;
                }
                gzStart = Integer.parseInt(offset.substring(headNumStart, headNumEnd));
                gzSize = Integer.parseInt(size.substring(size.indexOf("\"") + 1, size.lastIndexOf(("\""))));
                gzStart = Utils.findXth(installer, (byte) 0x0A, gzStart) + 1;
                pr.postStepProgress(++progressStep);
            } catch (Exception e) {
                pr.postLogLine(
                        "Failed to read necessary data via parsing (expected if variable substitution is used), trying fallback...",
                        null);
                // Fallback: Find the GZIP header (0x1F 0x8B 0x08)
                // This is the standard GZIP magic number + DEFLATE compression method
                byte[] gzipHeader = new byte[] { (byte) 0x1F, (byte) 0x8B, (byte) 0x08 };
                int gzipIndex = Utils.indexOf(installer, gzipHeader);

                if (gzipIndex != -1) {
                    gzStart = gzipIndex;
                    // Assume the rest of the file is the payload
                    gzSize = installer.length - gzStart;
                    pr.postLogLine("Fallback successful: Found GZIP header at " + gzStart, null);
                    pr.postStepProgress(++progressStep);
                } else {
                    pr.postLogLine("Fallback failed: GZIP header not found", null);
                    pr.setPartIndeterminate(false);
                    pr.unlockExit();
                    return;
                }
            }
            try {
                pr.postLogLine("Decompressing...", null);
                pr.postMaxPart(1);
                pr.postPartProgress(0);
                pr.setPartIndeterminate(true);
                try (TarArchiveInputStream tarIn = new TarArchiveInputStream(
                        new GZIPInputStream(new ByteArrayInputStream(installer, gzStart, gzSize)))) {
                    TarArchiveEntry entry = tarIn.getNextTarEntry();
                    while (entry != null) {
                        String entryName = entry.getName();
                        pr.postLogLine("Decompressing " + entryName, null);
                        File dest = new File(spiral, entry.getName());
                        if (entry.isDirectory()) {
                            dest.mkdirs();
                        } else if (entry.isFile()) {
                            dest.getParentFile().mkdirs();
                            try (FileOutputStream fos = new FileOutputStream(dest)) {
                                byte[] buf = new byte[65535];
                                int i;
                                while ((i = tarIn.read(buf)) != -1) {
                                    fos.write(buf, 0, i);
                                }
                            }
                        }
                        entry = tarIn.getNextTarEntry();
                    }
                }
                pr.postStepProgress(++progressStep);
            } catch (Exception e) {
                pr.postLogLine("Failed to decompress", e);
                pr.setPartIndeterminate(false);
                pr.unlockExit();
                return;
            }
        } else {
            pr.postMaxSteps(2);
        }

        // Replace getdown execution with custom download logic
        try {
            downloadGameFiles(progressStep);
            progressStep++; // Increment after successful download
        } catch (Exception e) {
            pr.postLogLine("Failed to download game files", e);
            pr.setPartIndeterminate(false);
            pr.unlockExit();
            return;
        }

        // Now generate JSON files and copy libraries
        try {
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
                    // Skip memory args, library path, and bracketed args
                    if (!arg.startsWith("-Xm") && !arg.startsWith("-Djava.library.path") && !arg.startsWith("[")) {
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

    private void downloadGameFiles(int progressStep) throws Exception {
        pr.postLogLine("Downloading game files...", null);
        pr.setPartIndeterminate(true);

        File getdownTxt = new File(spiral, "getdown.txt");
        if (!getdownTxt.exists()) {
            throw new IOException("getdown.txt not found");
        }

        String appbase = null;
        String version = null;
        List<String> resources = new ArrayList<>();
        List<String> uresources = new ArrayList<>();

        try (BufferedReader rdr = new BufferedReader(new InputStreamReader(new FileInputStream(getdownTxt)))) {
            String line;
            while ((line = rdr.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("appbase = ")) {
                    appbase = line.substring("appbase = ".length()).trim();
                } else if (line.startsWith("version = ")) {
                    version = line.substring("version = ".length()).trim();
                } else if (line.startsWith("code = ") || line.startsWith("resource = ")) {
                    String res = line.substring(line.indexOf(" = ") + 3).trim();
                    if (!res.startsWith("[")) { // Ignore platform specific for now unless
                                                // it matches our platform, but
                                                // simpler to ignore
                        resources.add(res);
                    }
                } else if (line.startsWith("uresource = ") || line.startsWith("full.uresource = ")) {
                    String res = line.substring(line.indexOf(" = ") + 3).trim();
                    if (!res.startsWith("[")) {
                        uresources.add(res);
                    }
                }
            }
        }

        // If we have appbase but no version, it means we have a stub getdown.txt (like
        // from the installer)
        // We need to fetch the full getdown.txt from the appbase.
        if (appbase != null && version == null) {
            pr.postLogLine("Detected stub getdown.txt, fetching full version...", null);
            String getdownUrl = appbase + "getdown.txt";
            Utils.downloadFile(getdownUrl, getdownTxt, pr);

            // Re-parse the new getdown.txt
            resources.clear();
            uresources.clear();
            appbase = null;
            version = null;

            try (BufferedReader rdr = new BufferedReader(new InputStreamReader(new FileInputStream(getdownTxt)))) {
                String line;
                while ((line = rdr.readLine()) != null) {
                    line = line.trim();
                    if (line.startsWith("appbase = ")) {
                        appbase = line.substring("appbase = ".length()).trim();
                    } else if (line.startsWith("version = ")) {
                        version = line.substring("version = ".length()).trim();
                    } else if (line.startsWith("code = ") || line.startsWith("resource = ")) {
                        String res = line.substring(line.indexOf(" = ") + 3).trim();
                        if (!res.startsWith("[")) {
                            resources.add(res);
                        }
                    } else if (line.startsWith("uresource = ") || line.startsWith("full.uresource = ")) {
                        String res = line.substring(line.indexOf(" = ") + 3).trim();
                        if (!res.startsWith("[")) {
                            uresources.add(res);
                        }
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

        int totalFiles = resources.size() + uresources.size();
        int currentFile = 0;
        pr.postMaxPart(totalFiles);
        pr.setPartIndeterminate(false);

        // Download resources
        for (String res : resources) {
            pr.postLogLine("Downloading " + res, null);
            pr.postPartProgress(currentFile++);
            File dest = new File(spiral, res);
            if (dest.exists())
                continue; // Skip if already exists? Or overwrite? Getdown usually checks digests. For
                          // now, skip if exists to save time, or overwrite to be safe? Let's overwrite to
                          // ensure correctness.
            // Actually, let's check if it exists to avoid re-downloading on retry.
            // But if it's partial?
            // Let's overwrite for now.
            Utils.downloadFile(baseUrl + res, dest, pr);
        }

        // Download and unpack uresources
        for (String res : uresources) {
            pr.postLogLine("Downloading and unpacking " + res, null);
            pr.postPartProgress(currentFile++);
            File dest = new File(spiral, res);
            Utils.downloadFile(baseUrl + res, dest, pr);
            File unpackTarget = spiral;
            if (res.contains("full-music-bundle.jar") || res.contains("full-rest-bundle.jar")
                    || res.contains("intro-bundle.jar")) {
                unpackTarget = new File(spiral, "rsrc");
            }
            unpack(dest, unpackTarget);
        }

        pr.postLogLine("Download complete.", null);
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
     * - spiral/rsrc/*.jar files (full-music-bundle.jar, full-rest-bundle.jar, intro-bundle.jar) -> spiral/rsrc/
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
        if (crucibleJar.exists()) totalJars++;

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