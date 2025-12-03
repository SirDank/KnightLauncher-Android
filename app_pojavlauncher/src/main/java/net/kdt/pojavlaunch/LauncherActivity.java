package net.kdt.pojavlaunch;

import static android.content.res.Configuration.ORIENTATION_PORTRAIT;

import android.Manifest;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentContainerView;
import androidx.fragment.app.FragmentManager;

import com.kdt.mcgui.ProgressLayout;

import net.kdt.pojavlaunch.contracts.OpenDocumentWithExtension;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.extra.ExtraListener;
import net.kdt.pojavlaunch.fragments.MainMenuFragment;
import net.kdt.pojavlaunch.lifecycle.ContextExecutor;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceFragment;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.progresskeeper.TaskCountListener;
import net.kdt.pojavlaunch.services.ProgressServiceKeeper;
import net.kdt.pojavlaunch.utils.NotificationUtils;

import java.lang.ref.WeakReference;
import java.io.File;
import android.app.ProgressDialog;
import net.kdt.pojavlaunch.knight.KnightInstaller;
import net.kdt.pojavlaunch.knight.Progress;

public class LauncherActivity extends BaseActivity {
    public static final String SETTING_FRAGMENT_TAG = "SETTINGS_FRAGMENT";

    private FragmentContainerView mFragmentView;
    private ImageButton mSettingsButton;
    private ProgressLayout mProgressLayout;
    private ProgressServiceKeeper mProgressServiceKeeper;
    private NotificationManager mNotificationManager;
    private PowerManager.WakeLock mWakeLock;

    /* Allows to switch from one button "type" to another */
    private final FragmentManager.FragmentLifecycleCallbacks mFragmentCallbackListener = new FragmentManager.FragmentLifecycleCallbacks() {
        @Override
        public void onFragmentResumed(@NonNull FragmentManager fm, @NonNull Fragment f) {
            mSettingsButton.setImageDrawable(ContextCompat.getDrawable(getBaseContext(),
                    f instanceof MainMenuFragment ? R.drawable.ic_menu_settings : R.drawable.ic_menu_home));
        }
    };

    /* Listener for the back button in settings */
    private final ExtraListener<String> mBackPreferenceListener = (key, value) -> {
        if (value.equals("true"))
            onBackPressed();
        return false;
    };

    /* Listener for the settings fragment */
    private final View.OnClickListener mSettingButtonListener = v -> {
        Fragment fragment = getSupportFragmentManager().findFragmentById(mFragmentView.getId());
        if (fragment instanceof MainMenuFragment) {
            Tools.swapFragment(this, LauncherPreferenceFragment.class, SETTING_FRAGMENT_TAG, null);
        } else {
            // The setting button doubles as a home button now
            Tools.backToMainMenu(this);
        }
    };

    private final ExtraListener<Boolean> mLaunchGameListener = (key, value) -> {
        if (mProgressLayout.hasProcesses()) {
            Toast.makeText(this, R.string.tasks_ongoing, Toast.LENGTH_LONG).show();
            return false;
        }

        // Launch Spiral Knights
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra(MainActivity.INTENT_MINECRAFT_VERSION, "SpiralKnights");
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        // finish(); // Keep launcher open or close it? Usually close it to save
        // resources, but maybe keep for now.
        return false;
    };

    private final TaskCountListener mDoubleLaunchPreventionListener = taskCount -> {
        // Hide the notification that starts the game if there are tasks executing.
        // Prevents the user from trying to launch the game with tasks ongoing.
        if (taskCount > 0) {
            Tools.runOnUiThread(() -> mNotificationManager.cancel(NotificationUtils.NOTIFICATION_ID_GAME_START));
        }
    };

    private ActivityResultLauncher<String> mRequestNotificationPermissionLauncher;
    private ActivityResultLauncher<String> mRequestMicrophonePermissionLauncher;
    private WeakReference<Runnable> mRequestNotificationPermissionRunnable;
    private WeakReference<Runnable> mRequestMicrophonePermissionRunnable;

    @Override
    protected boolean shouldIgnoreNotch() {
        return getResources().getConfiguration().orientation == ORIENTATION_PORTRAIT;
    }

    @Override
    public boolean setFullscreen() {
        return false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pojav_launcher);
        FragmentManager fragmentManager = getSupportFragmentManager();
        // If we don't have a back stack root yet...
        if (fragmentManager.getBackStackEntryCount() < 1) {
            // Manually add the first fragment to the backstack to get easily back to it
            // There must be a better way to handle the root though...
            // (artDev: No, there is not. I've spent days researching this for another
            // unrelated project.)
            fragmentManager.beginTransaction()
                    .setReorderingAllowed(true)
                    .addToBackStack("ROOT")
                    .add(R.id.container_fragment, MainMenuFragment.class, null, "ROOT").commit();
        }

        mRequestNotificationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isAllowed -> {
                    if (!isAllowed)
                        handleNoNotificationPermission();
                    else {
                        Runnable runnable = Tools.getWeakReference(mRequestNotificationPermissionRunnable);
                        if (runnable != null)
                            runnable.run();
                    }
                });
        mRequestMicrophonePermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isAllowed -> {
                    if (!isAllowed)
                        handleNoNotificationPermission();
                    else {
                        Runnable runnable = Tools.getWeakReference(mRequestMicrophonePermissionRunnable);
                        if (runnable != null)
                            runnable.run();
                    }
                });
        getWindow().setBackgroundDrawable(null);
        bindViews();
        checkNotificationPermission();
        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        ProgressKeeper.addTaskCountListener(mDoubleLaunchPreventionListener);
        ProgressKeeper.addTaskCountListener((mProgressServiceKeeper = new ProgressServiceKeeper(this)));

        mSettingsButton.setOnClickListener(mSettingButtonListener);
        ProgressKeeper.addTaskCountListener(mProgressLayout);
        ExtraCore.addExtraListener(ExtraConstants.BACK_PREFERENCE, mBackPreferenceListener);
        ExtraCore.addExtraListener(ExtraConstants.LAUNCH_GAME, mLaunchGameListener);

        if (savedInstanceState == null && !new File(Tools.DIR_GAME_HOME, "spiral/getdown-pro.jar").exists()) {
            installSpiralKnights();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        ContextExecutor.setActivity(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        ContextExecutor.clearActivity();
    }

    @Override
    protected void onStart() {
        super.onStart();
        getSupportFragmentManager().registerFragmentLifecycleCallbacks(mFragmentCallbackListener, true);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mProgressLayout.cleanUpObservers();
        ProgressKeeper.removeTaskCountListener(mProgressLayout);
        ProgressKeeper.removeTaskCountListener(mProgressServiceKeeper);
        ExtraCore.removeExtraListenerFromValue(ExtraConstants.BACK_PREFERENCE, mBackPreferenceListener);
        ExtraCore.removeExtraListenerFromValue(ExtraConstants.LAUNCH_GAME, mLaunchGameListener);

        getSupportFragmentManager().unregisterFragmentLifecycleCallbacks(mFragmentCallbackListener);
    }

    /** Custom implementation to feel more natural when a backstack isn't present */
    @Override
    public void onBackPressed() {
        // Check if we are at the root then
        if (getVisibleFragment("ROOT") != null) {
            finish();
        }

        super.onBackPressed();
    }

    @Override
    public void onAttachedToWindow() {
        LauncherPreferences.computeNotchSize(this);
    }

    @SuppressWarnings("SameParameterValue")
    private Fragment getVisibleFragment(String tag) {
        Fragment fragment = getSupportFragmentManager().findFragmentByTag(tag);
        if (fragment != null && fragment.isVisible()) {
            return fragment;
        }
        return null;
    }

    @SuppressWarnings("unused")
    private Fragment getVisibleFragment(int id) {
        Fragment fragment = getSupportFragmentManager().findFragmentById(id);
        if (fragment != null && fragment.isVisible()) {
            return fragment;
        }
        return null;
    }

    private void checkNotificationPermission() {
        if (LauncherPreferences.PREF_SKIP_NOTIFICATION_PERMISSION_CHECK ||
                checkForNotificationPermission()) {
            return;
        }

        if (ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.POST_NOTIFICATIONS)) {
            showNotificationPermissionReasoning();
            return;
        }
        askForNotificationPermission(null);
    }

    private void showNotificationPermissionReasoning() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.notification_permission_dialog_title)
                .setMessage(R.string.notification_permission_dialog_text)
                .setPositiveButton(android.R.string.ok, (d, w) -> askForNotificationPermission(null))
                .setNegativeButton(android.R.string.cancel, (d, w) -> handleNoNotificationPermission())
                .show();
    }

    private void handleNoNotificationPermission() {
        LauncherPreferences.PREF_SKIP_NOTIFICATION_PERMISSION_CHECK = true;
        LauncherPreferences.DEFAULT_PREF.edit()
                .putBoolean(LauncherPreferences.PREF_KEY_SKIP_NOTIFICATION_CHECK, true)
                .apply();
        Toast.makeText(this, R.string.notification_permission_toast, Toast.LENGTH_LONG).show();
    }

    public boolean checkForNotificationPermission() {
        return Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_DENIED;
    }

    public boolean checkForMicrophonePermission() {
        return ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_DENIED;
    }

    public void askForNotificationPermission(Runnable onSuccessRunnable) {
        if (Build.VERSION.SDK_INT < 33)
            return;
        if (onSuccessRunnable != null) {
            mRequestNotificationPermissionRunnable = new WeakReference<>(onSuccessRunnable);
        }
        mRequestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
    }

    public void askForMicrophonePermission(Runnable onSuccessRunnable) {
        if (onSuccessRunnable != null) {
            mRequestMicrophonePermissionRunnable = new WeakReference<>(onSuccessRunnable);
        }
        mRequestMicrophonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
    }

    public void reinstallGame() {
        File spiralDir = new File(Tools.DIR_GAME_HOME, "spiral");
        if (spiralDir.exists()) {
            try {
                org.apache.commons.io.FileUtils.deleteDirectory(spiralDir);
            } catch (java.io.IOException e) {
                e.printStackTrace();
                Toast.makeText(this, "Failed to delete game files", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        installSpiralKnights();
    }

    private void releaseWakeLock() {
        if (mWakeLock != null && mWakeLock.isHeld()) {
            mWakeLock.release();
        }
        getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    public void installSpiralKnights() {
        if (new File(Tools.DIR_GAME_HOME, "spiral/getdown-pro.jar").exists()) {
            return;
        }

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);

        // Acquire wake lock to prevent the device from sleeping during installation
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "KnightLauncher:InstallWakeLock");
        mWakeLock.acquire(60 * 60 * 1000L); // 1 hour max timeout
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        final ProgressDialog pd = new ProgressDialog(this);
        pd.setTitle("Installing Spiral Knights");
        pd.setMessage("Please wait...");
        pd.setIndeterminate(false);
        pd.setCancelable(false);
        pd.show();

        new Thread(new KnightInstaller(new Progress() {
            @Override
            public void postStepProgress(int prg) {
                // pd.setProgress(prg); // ProgressDialog max is 100 by default, steps are small
                // integers
            }

            @Override
            public void postPartProgress(int prg) {
                // pd.setSecondaryProgress(prg);
            }

            @Override
            public void postMaxSteps(int max) {
                // pd.setMax(max);
            }

            @Override
            public void postMaxPart(int max) {
                //
            }

            @Override
            public void setPartIndeterminate(boolean indeterminate) {
                pd.setIndeterminate(indeterminate);
            }

            @Override
            public void postLogLine(String line, Throwable th) {
                Tools.runOnUiThread(() -> {
                    pd.setMessage(line);
                    if (th != null) {
                        // Release wake lock on error
                        releaseWakeLock();
                        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
                        String errorMessage = "Error: " + th.getMessage() + "\n" + Tools.printToString(th);
                        new AlertDialog.Builder(LauncherActivity.this)
                                .setTitle("Installation Error")
                                .setMessage(errorMessage)
                                .setPositiveButton("Retry", (d, w) -> installSpiralKnights())
                                .setNeutralButton("Copy Error", (d, w) -> {
                                    android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(
                                            CLIPBOARD_SERVICE);
                                    android.content.ClipData clip = android.content.ClipData
                                            .newPlainText("Installation Error", errorMessage);
                                    clipboard.setPrimaryClip(clip);
                                    Toast.makeText(LauncherActivity.this, "Error copied to clipboard",
                                            Toast.LENGTH_SHORT).show();
                                })
                                .setCancelable(true)
                                .show();
                        pd.dismiss();
                    }
                });
            }

            @Override
            public void unlockExit() {
                Tools.runOnUiThread(() -> {
                    // Release wake lock when installation is complete
                    releaseWakeLock();
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
                    pd.dismiss();
                    boolean getdownExists = new File(Tools.DIR_GAME_HOME, "spiral/getdown-pro.jar").exists();
                    boolean jsonExists = new File(Tools.DIR_GAME_HOME, "versions/SpiralKnights/SpiralKnights.json")
                            .exists();

                    if (getdownExists && jsonExists) {
                        LauncherPreferences.DEFAULT_PREF.edit()
                                .putString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, "SpiralKnights").apply();
                        Toast.makeText(LauncherActivity.this, "Installation Complete", Toast.LENGTH_SHORT).show();
                    } else {
                        // Error dialog is handled in postLogLine if exception occurred,
                        // but if it just failed silently or finished without exception but files are
                        // missing:
                        String missingFiles = "";
                        if (!getdownExists)
                            missingFiles += "getdown-pro.jar ";
                        if (!jsonExists)
                            missingFiles += "SpiralKnights.json";

                        String failureMessage = "Installation finished but required files were not found: "
                                + missingFiles;
                        new AlertDialog.Builder(LauncherActivity.this)
                                .setTitle("Installation Failed")
                                .setMessage(failureMessage)
                                .setPositiveButton("Retry", (d, w) -> installSpiralKnights())
                                .setNeutralButton("Copy Error", (d, w) -> {
                                    android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(
                                            CLIPBOARD_SERVICE);
                                    android.content.ClipData clip = android.content.ClipData
                                            .newPlainText("Installation Error", failureMessage);
                                    clipboard.setPrimaryClip(clip);
                                    Toast.makeText(LauncherActivity.this, "Error copied to clipboard",
                                            Toast.LENGTH_SHORT).show();
                                })
                                .setCancelable(true)
                                .show();
                    }
                });
            }
        })).start();
    }

    /** Stuff all the view boilerplate here */
    private void bindViews() {
        mFragmentView = findViewById(R.id.container_fragment);
        mSettingsButton = findViewById(R.id.setting_button);
        mProgressLayout = findViewById(R.id.progress_layout);
    }
}