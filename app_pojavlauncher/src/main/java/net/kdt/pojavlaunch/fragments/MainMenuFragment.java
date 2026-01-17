package net.kdt.pojavlaunch.fragments;

import static net.kdt.pojavlaunch.Tools.openPath;
import static net.kdt.pojavlaunch.Tools.shareLog;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.CustomControlsActivity;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.knight.ModsDownloader;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.utils.WakeLockUtils;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.File;

public class MainMenuFragment extends Fragment {
    public static final String TAG = "MainMenuFragment";
    private final WakeLockUtils mWakeLockUtils = new WakeLockUtils();

    public MainMenuFragment() {
        super(R.layout.fragment_launcher);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Button mGithubButton = view.findViewById(R.id.github_button);
        Button mDiscordButton = view.findViewById(R.id.discord_button);
        Button mCustomControlButton = view.findViewById(R.id.custom_control_button);
        Button mOpenDirectoryButton = view.findViewById(R.id.open_files_button);
        Button mShareLogsButton = view.findViewById(R.id.share_logs_button);
        Button mDownloadModsButton = view.findViewById(R.id.download_mods_button);

        Button mPlayButton = view.findViewById(R.id.play_button);
        Button mPlayerCountButton = view.findViewById(R.id.player_count_button);

        mGithubButton.setOnClickListener(v -> Tools.openURL(requireActivity(), getString(R.string.github_url)));
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

        // Download Mods button
        if (mDownloadModsButton != null) {
            mDownloadModsButton.setOnClickListener(v -> showDownloadModsConfirmation());
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
                new AlertDialog.Builder(requireContext())
                        .setTitle(R.string.mcl_button_reset_game_files)
                        .setMessage(R.string.mcl_reset_game_files_confirmation)
                        .setPositiveButton(android.R.string.ok, (d, w) -> {
                            if (requireActivity() instanceof net.kdt.pojavlaunch.LauncherActivity) {
                                ((net.kdt.pojavlaunch.LauncherActivity) requireActivity()).updateGame();
                            }
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            });
        }
    }

    private void showDownloadModsConfirmation() {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.mcl_button_download_mods)
                .setMessage(R.string.mcl_download_mods_confirmation)
                .setPositiveButton(android.R.string.ok, (d, w) -> startModsDownload())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void startModsDownload() {
        // Acquire wake lock and lock orientation
        mWakeLockUtils.acquire(requireActivity(), "KnightLauncher:ModsDownloadWakeLock");

        // Create progress dialog
        final ProgressDialog pd = new ProgressDialog(requireContext());
        pd.setTitle(R.string.mcl_button_download_mods);
        pd.setMessage("Preparing...");
        pd.setIndeterminate(true);
        pd.setCancelable(false);
        pd.show();

        new Thread(() -> {
            // Download mods with progress callback
            ModsDownloader.downloadMods(new ModsDownloader.ProgressCallback() {
                @Override
                public void onStatusUpdate(String status) {
                    Tools.runOnUiThread(() -> pd.setMessage(status));
                }

                @Override
                public void onProgress(int current, int total, String currentFileName) {
                    Tools.runOnUiThread(() -> {
                        pd.setIndeterminate(false);
                        pd.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                        pd.setMax(100);
                        pd.setMessage(getString(R.string.mcl_download_mods_progress, current, total) + "\n" + currentFileName);
                        pd.setProgress((current * 100) / total);
                    });
                }

                @Override
                public void onComplete() {
                    Tools.runOnUiThread(() -> {
                        mWakeLockUtils.release();
                        pd.dismiss();
                        Toast.makeText(requireContext(), R.string.mcl_download_mods_complete, Toast.LENGTH_LONG).show();
                    });
                }

                @Override
                public void onError(String error, Throwable throwable) {
                    Tools.runOnUiThread(() -> {
                        mWakeLockUtils.release();
                        pd.dismiss();
                        new AlertDialog.Builder(requireContext())
                                .setTitle(R.string.mcl_download_mods_failed)
                                .setMessage(error)
                                .setPositiveButton(android.R.string.ok, null)
                                .show();
                    });
                }
            });
        }).start();
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

    @Override
    public void onDestroy() {
        super.onDestroy();
        mWakeLockUtils.release();
    }
}
