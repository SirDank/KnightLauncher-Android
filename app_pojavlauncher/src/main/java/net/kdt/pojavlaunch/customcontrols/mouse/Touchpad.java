package net.kdt.pojavlaunch.customcontrols.mouse;

import static net.kdt.pojavlaunch.Tools.currentDisplayMetrics;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;

import net.kdt.pojavlaunch.GrabListener;
import net.kdt.pojavlaunch.MinecraftGLSurface;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;

import org.lwjgl.glfw.CallbackBridge;

/**
 * Class dealing with the virtual mouse
 */
public class Touchpad extends View implements GrabListener, AbstractTouchpad {
    /* Whether the Touchpad should be displayed */
    private boolean mDisplayState;
    /* Mouse pointer icon used by the touchpad */
    private Drawable mMousePointerDrawable;
    private float mMouseX, mMouseY;
    private MinecraftGLSurface mMinecraftGLSurface;
    public Touchpad(@NonNull Context context) {
        this(context, null);
    }

    public Touchpad(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    /** Enable the touchpad */
    private void _enable(){
        setVisibility(VISIBLE);
        placeMouseAt(currentDisplayMetrics.widthPixels / 2f, currentDisplayMetrics.heightPixels / 2f);
    }

    /** Disable the touchpad and hides the mouse */
    private void _disable(){
        setVisibility(GONE);
    }

    /** @return The new state, enabled or disabled */
    public boolean switchState(){
        mDisplayState = !mDisplayState;
        // Always apply state change - user controls visibility via touch UI button
        if(mDisplayState) _enable();
        else _disable();
        
        // Trigger re-evaluation of touch processor when state changes
        if(mMinecraftGLSurface != null) {
            mMinecraftGLSurface.refreshTouchProcessor();
        }
        
        return mDisplayState;
    }

    public void setMinecraftGLSurface(MinecraftGLSurface minecraftGLSurface) {
        mMinecraftGLSurface = minecraftGLSurface;
    }

    public void placeMouseAt(float x, float y) {
        mMouseX = x;
        mMouseY = y;
        updateMousePosition();
    }

    private void sendMousePosition() {
        CallbackBridge.sendCursorPos((mMouseX * LauncherPreferences.PREF_SCALE_FACTOR), (mMouseY * LauncherPreferences.PREF_SCALE_FACTOR));
    }

    private void updateMousePosition() {
        sendMousePosition();
        // I wanted to implement a dirty rect for this, but it is ignored since API level 21
        // (which is our min API)
        // Let's hope the "internally calculated area" is good enough.
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.translate(mMouseX, mMouseY);
        mMousePointerDrawable.draw(canvas);
    }

    private void init(){
        // Setup mouse pointer
        mMousePointerDrawable = ResourcesCompat.getDrawable(getResources(), R.drawable.ic_mouse_pointer, getContext().getTheme());
        // For some reason it's annotated as Nullable even though it doesn't seem to actually
        // ever return null
        assert mMousePointerDrawable != null;
        mMousePointerDrawable.setBounds(
                0, 0,
                (int) (36 * LauncherPreferences.PREF_MOUSESCALE),
                (int) (54 * LauncherPreferences.PREF_MOUSESCALE)
        );
        setFocusable(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            setDefaultFocusHighlightEnabled(false);
        }

        // When the game is grabbing, we should not display the mouse
        disable();
        mDisplayState = false;
    }

    @Override
    public void onGrabState(boolean isGrabbing) {
        post(()->updateGrabState(isGrabbing));
    }
    private void updateGrabState(boolean isGrabbing) {
        // Mouse visibility is now controlled by touch UI button (mDisplayState) only,
        // not by the game's grab state for camera control
        if(mDisplayState && getVisibility() != VISIBLE) _enable();
        if(!mDisplayState && getVisibility() == VISIBLE) _disable();
    }

    @Override
    public boolean getDisplayState() {
        return mDisplayState;
    }

    @Override
    public void applyMotionVector(float x, float y) {
        mMouseX = Math.max(0, Math.min(currentDisplayMetrics.widthPixels, mMouseX + x * LauncherPreferences.PREF_MOUSESPEED));
        mMouseY = Math.max(0, Math.min(currentDisplayMetrics.heightPixels, mMouseY + y * LauncherPreferences.PREF_MOUSESPEED));
        updateMousePosition();
    }

    @Override
    public void enable(boolean supposed) {
        if(mDisplayState) return;
        mDisplayState = true;
        if(supposed && CallbackBridge.isGrabbing() && LauncherPreferences.PREF_MOUSE_GRAB_FORCE) return;
        _enable();
    }

    @Override
    public void disable() {
        if(!mDisplayState) return;
        mDisplayState = false;
        _disable();
    }
}
