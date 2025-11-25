package net.kdt.pojavlaunch.value.launcherprofiles;

import net.kdt.pojavlaunch.Tools;
import java.io.File;
import java.util.HashMap;

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
        // No-op, we don't want to write to disk
    }

    public static MinecraftProfile getCurrentProfile() {
        if (mainProfileJson == null)
            load();
        return mainProfileJson.profiles.get("SpiralKnights");
    }

    // Keep other methods if they are used elsewhere but make them safe or no-ops
    public static void updateProfile(String name, MinecraftProfile profile) {
    }

    public static void deleteProfile(String name) {
    }
}