package net.kdt.pojavlaunch.prefs;

import static net.kdt.pojavlaunch.prefs.LauncherPreferences.PREF_MOUSESPEED;
import static net.kdt.pojavlaunch.prefs.LauncherPreferences.PREF_MOUSE_GRAB_FORCE;
import static net.kdt.pojavlaunch.prefs.LauncherPreferences.PREF_SCALE_FACTOR;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Switch;
import android.widget.TextView;

import com.kdt.CustomSeekbar;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.utils.interfaces.SimpleSeekBarListener;

/**
 * Side dialog for quick settings that you can change in game
 * The implementation has to take action on some preference changes
 */
public abstract class QuickSettingSideDialog extends com.kdt.SideDialogView {

    private SharedPreferences.Editor mEditor;
    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private Switch mMouseGrabSwitch;
    private CustomSeekbar mMouseSpeedBar, mResolutionBar;
    private TextView mMouseSpeedText, mResolutionText;

    private boolean mOriginalMouseGrab;
    private float mOriginalMouseSpeed, mOriginalResolution;

    public QuickSettingSideDialog(Context context, ViewGroup parent) {
        super(context, parent, R.layout.dialog_quick_setting);
        setTitle(R.string.quick_setting_title);
        setupCancelButton();
    }

    @Override
    protected void onInflate() {
        bindLayout();
        Tools.runOnUiThread(() -> {
            this.setupListeners();
        });
    }

    @Override
    protected void onDestroy() {
        removeListeners();
    }

    private void bindLayout() {
        // Bind layout elements
        mMouseGrabSwitch = mDialogContent.findViewById(R.id.always_grab_mouse_side_dialog);

        mMouseSpeedBar = mDialogContent.findViewById(R.id.editMouseSpeed_seekbar);
        mResolutionBar = mDialogContent.findViewById(R.id.editResolution_seekbar);

        mMouseSpeedText = mDialogContent.findViewById(R.id.editMouseSpeed_textView_percent);
        mResolutionText = mDialogContent.findViewById(R.id.editResolution_textView_percent);
    }

    private void setupListeners() {
        mEditor = LauncherPreferences.DEFAULT_PREF.edit();

        mOriginalMouseGrab = PREF_MOUSE_GRAB_FORCE;

        mOriginalMouseSpeed = PREF_MOUSESPEED;
        mOriginalResolution = PREF_SCALE_FACTOR;

        mMouseGrabSwitch.setChecked(mOriginalMouseGrab);

        mMouseGrabSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            PREF_MOUSE_GRAB_FORCE = isChecked;
            mEditor.putBoolean("always_grab_mouse", isChecked);
        });

        mMouseSpeedBar.setOnSeekBarChangeListener((SimpleSeekBarListener) (seekBar, progress, fromUser) -> {
            PREF_MOUSESPEED = progress / 100f;
            mEditor.putInt("mousespeed", progress);
            setSeekTextPercent(mMouseSpeedText, progress);
        });
        mMouseSpeedBar.setProgress((int) (mOriginalMouseSpeed * 100f));
        setSeekTextPercent(mMouseSpeedText, mMouseSpeedBar.getProgress());

        mResolutionBar.setOnSeekBarChangeListener((SimpleSeekBarListener) (seekBar, progress, fromUser) -> {
            PREF_SCALE_FACTOR = progress / 100f;
            mEditor.putInt("resolutionRatio", progress);
            setSeekTextPercent(mResolutionText, progress);
            onResolutionChanged();
        });
        mResolutionBar.setProgress((int) (mOriginalResolution * 100));
        setSeekTextPercent(mResolutionText, mResolutionBar.getProgress());

        updateMouseGrabVisibility();
    }

    private static void setSeekTextPercent(TextView target, int value) {
        setSeekText(target, R.string.percent_format, value);
    }

    private static void setSeekText(TextView target, int format, int value) {
        target.setText(target.getContext().getString(format, value));
    }

    private void updateMouseGrabVisibility() {
        mMouseGrabSwitch.setVisibility(View.VISIBLE);
    }

    private void removeListeners() {
        mMouseGrabSwitch.setOnCheckedChangeListener(null);

        mMouseSpeedBar.setOnSeekBarChangeListener(null);
        mResolutionBar.setOnSeekBarChangeListener(null);
    }

    private void setupCancelButton() {
        setStartButtonListener(android.R.string.cancel, v -> cancel());
        setEndButtonListener(android.R.string.ok, v -> {
            mEditor.apply();
            disappear(true);
        });
    }

    /** Resets all settings to their original values */
    public void cancel() {
        // Reset all settings if we were editing
        if (isDisplaying()) {
            PREF_MOUSE_GRAB_FORCE = mOriginalMouseGrab;

            PREF_MOUSESPEED = mOriginalMouseSpeed;
            PREF_SCALE_FACTOR = mOriginalResolution;

            onResolutionChanged();
        }

        disappear(true);
    }

    /**
     * Called when the resolution is changed. Use
     * {@link LauncherPreferences#PREF_SCALE_FACTOR}
     */
    public abstract void onResolutionChanged();

    /**
     * Called when the gyro state is changed.
     * Use {@link LauncherPreferences#PREF_ENABLE_GYRO}
     * Use {@link LauncherPreferences#PREF_GYRO_INVERT_X}
     * Use {@link LauncherPreferences#PREF_GYRO_INVERT_Y}
     */
    public abstract void onGyroStateChanged();

}