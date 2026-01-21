package net.kdt.pojavlaunch.utils;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.os.PowerManager;
import android.view.WindowManager;

/**
 * Utility class for managing wake locks and screen orientation during long-running operations.
 * Prevents the device from sleeping and keeps the screen on during downloads/installations.
 */
public class WakeLockUtils {
    
    private PowerManager.WakeLock mWakeLock;
    private Activity mActivity;
    
    /**
     * Acquires a wake lock and locks the screen orientation.
     * Should be called at the start of a long-running operation.
     * 
     * @param activity The activity context
     * @param wakeLockTag A unique tag for the wake lock (e.g., "KnightLauncher:ModsDownload")
     */
    public void acquire(Activity activity, String wakeLockTag) {
        mActivity = activity;
        
        // Lock screen orientation during operation
        activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
        
        // Acquire wake lock to prevent the device from sleeping
        PowerManager powerManager = (PowerManager) activity.getSystemService(Context.POWER_SERVICE);
        mWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, wakeLockTag);
        mWakeLock.acquire(60 * 60 * 1000L); // 1 hour max timeout
        
        // Keep screen on
        activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    
    /**
     * Releases the wake lock and unlocks the screen orientation.
     * Should be called when the long-running operation completes.
     */
    public void release() {
        if (mWakeLock != null && mWakeLock.isHeld()) {
            mWakeLock.release();
            mWakeLock = null;
        }
        if (mActivity != null) {
            mActivity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
            mActivity = null;
        }
    }
    
    /**
     * Checks if the wake lock is currently held.
     */
    public boolean isHeld() {
        return mWakeLock != null && mWakeLock.isHeld();
    }
}
