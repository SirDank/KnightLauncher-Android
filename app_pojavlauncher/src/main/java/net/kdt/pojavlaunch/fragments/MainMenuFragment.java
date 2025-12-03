package net.kdt.pojavlaunch.fragments;

import static net.kdt.pojavlaunch.Tools.openPath;
import static net.kdt.pojavlaunch.Tools.shareLog;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.CustomControlsActivity;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.File;

public class MainMenuFragment extends Fragment {
    public static final String TAG = "MainMenuFragment";

    public MainMenuFragment() {
        super(R.layout.fragment_launcher);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Button mDiscordButton = view.findViewById(R.id.discord_button);
        Button mCustomControlButton = view.findViewById(R.id.custom_control_button);
        Button mOpenDirectoryButton = view.findViewById(R.id.open_files_button);
        Button mShareLogsButton = view.findViewById(R.id.share_logs_button);

        Button mPlayButton = view.findViewById(R.id.play_button);
        Button mPlayerCountButton = view.findViewById(R.id.player_count_button);

        mDiscordButton.setOnClickListener(v -> Tools.openURL(requireActivity(), getString(R.string.discord_invite)));

        if (mCustomControlButton != null) {
            mCustomControlButton
                    .setOnClickListener(v -> startActivity(new Intent(requireContext(), CustomControlsActivity.class)));
        }

        mPlayButton.setOnClickListener(v -> ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true));

        if (mOpenDirectoryButton != null) {
            mOpenDirectoryButton.setOnClickListener((v) -> {
                openPath(v.getContext(), getCurrentProfileDirectory(), false);
            });
        }

        if (mShareLogsButton != null) {
            mShareLogsButton.setOnClickListener(v -> shareLog(requireContext()));
        }

        // Player count button
        if (mPlayerCountButton != null) {
            mPlayerCountButton.setText("Loading...");
            mPlayerCountButton.setEnabled(false);
            // Initial load
            loadPlayerCount(mPlayerCountButton);
            
            // Reload on click
            mPlayerCountButton.setOnClickListener(v -> {
                mPlayerCountButton.setEnabled(false);
                mPlayerCountButton.setText("Loading...");
                loadPlayerCount(mPlayerCountButton);
            });
        }

        Button mResetGameFilesButton = view.findViewById(R.id.reset_game_files_button);
        if (mResetGameFilesButton != null) {
            mResetGameFilesButton.setOnClickListener(v -> {
                new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                        .setTitle(R.string.mcl_button_reset_game_files)
                        .setMessage(R.string.mcl_reset_game_files_confirmation)
                        .setPositiveButton(android.R.string.ok, (d, w) -> {
                            if (requireActivity() instanceof net.kdt.pojavlaunch.LauncherActivity) {
                                ((net.kdt.pojavlaunch.LauncherActivity) requireActivity()).reinstallGame();
                            }
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            });
        }
    }

    private void loadPlayerCount(Button button) {
        new Thread(() -> {
            int playerCount = net.kdt.pojavlaunch.SteamUtil.getOfficialApproxPlayerCount();
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    button.setText("Players Online: " + playerCount);
                    button.setEnabled(true);
                });
            }
        }).start();
    }

    private File getCurrentProfileDirectory() {
        // For Spiral Knights, we might just return the game dir
        return new File(Tools.DIR_GAME_NEW);
    }

    @Override
    public void onResume() {
        super.onResume();
    }
}