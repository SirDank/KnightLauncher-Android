package net.kdt.pojavlaunch.value.launcherprofiles;

import net.kdt.pojavlaunch.Tools;
import java.io.File;
import java.util.HashMap;
import java.io.IOException;
import android.util.Log;

public class LauncherProfiles {
    public static final File launcherProfilesFile = new File(Tools.DIR_GAME_HOME, "launcher_profiles.json");
    public static MinecraftLauncherProfiles mainProfileJson;

    public static void load() {
        // Hardcoded profile for Spiral Knights
        mainProfileJson = new MinecraftLauncherProfiles();
        mainProfileJson.profiles = new HashMap<>();

        MinecraftProfile skProfile = new MinecraftProfile();
        skProfile.name = "Spiral Knights";
        skProfile.lastVersionId = "SpiralKnights";
        skProfile.controlFile = "default.json";

        mainProfileJson.profiles.put("SpiralKnights", skProfile);

    }

    public static void write() {
        try {
            Tools.write(launcherProfilesFile.getAbsolutePath(), mainProfileJson.toJson());
        } catch (IOException e) {
            Log.e(LauncherProfiles.class.toString(), "Failed to write profile file", e);
            throw new RuntimeException(e);
        }
    }

    public static MinecraftProfile getCurrentProfile() {
        if (mainProfileJson == null)
            load();
        return mainProfileJson.profiles.get("SpiralKnights");
    }

    public static void updateProfile(String name, MinecraftProfile profile) {
        if (mainProfileJson == null)
            load();
        mainProfileJson.profiles.put(name, profile);
        write();
    }

    public static void deleteProfile(String name) {
        if (mainProfileJson == null)
            load();
        mainProfileJson.profiles.remove(name);
        write();
    }
}