package net.kdt.pojavlaunch.customcontrols.buttons;

import static net.kdt.pojavlaunch.customcontrols.gamepad.GamepadJoystick.DIRECTION_EAST;
import static net.kdt.pojavlaunch.customcontrols.gamepad.GamepadJoystick.DIRECTION_NONE;
import static net.kdt.pojavlaunch.customcontrols.gamepad.GamepadJoystick.DIRECTION_NORTH;
import static net.kdt.pojavlaunch.customcontrols.gamepad.GamepadJoystick.DIRECTION_NORTH_EAST;
import static net.kdt.pojavlaunch.customcontrols.gamepad.GamepadJoystick.DIRECTION_NORTH_WEST;
import static net.kdt.pojavlaunch.customcontrols.gamepad.GamepadJoystick.DIRECTION_SOUTH;
import static net.kdt.pojavlaunch.customcontrols.gamepad.GamepadJoystick.DIRECTION_SOUTH_EAST;
import static net.kdt.pojavlaunch.customcontrols.gamepad.GamepadJoystick.DIRECTION_SOUTH_WEST;
import static net.kdt.pojavlaunch.customcontrols.gamepad.GamepadJoystick.DIRECTION_WEST;
import static net.kdt.pojavlaunch.Tools.currentDisplayMetrics;

import android.annotation.SuppressLint;
import android.view.View;

import net.kdt.pojavlaunch.LwjglGlfwKeycode;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.customcontrols.ControlData;
import net.kdt.pojavlaunch.customcontrols.ControlJoystickData;
import net.kdt.pojavlaunch.customcontrols.ControlLayout;
import net.kdt.pojavlaunch.customcontrols.gamepad.GamepadJoystick;
import net.kdt.pojavlaunch.customcontrols.handleview.EditControlSideDialog;

import org.lwjgl.glfw.CallbackBridge;

import io.github.controlwear.virtual.joystick.android.JoystickView;

@SuppressLint("ViewConstructor")
public class ControlJoystick extends JoystickView implements ControlInterface {
    public final static int DIRECTION_FORWARD_LOCK = 8;
    // Fixed radius in pixels for mouse movement from screen center
    private static final float MOUSE_RADIUS = 100f;
    // Directions keycode
    private final int[] mDirectionForwardLock = new int[] { LwjglGlfwKeycode.GLFW_KEY_LEFT_CONTROL };
    private final int[] mDirectionForward = new int[] { LwjglGlfwKeycode.GLFW_KEY_W };
    private final int[] mDirectionRight = new int[] { LwjglGlfwKeycode.GLFW_KEY_D };
    private final int[] mDirectionBackward = new int[] { LwjglGlfwKeycode.GLFW_KEY_S };
    private final int[] mDirectionLeft = new int[] { LwjglGlfwKeycode.GLFW_KEY_A };
    private final int[] mDirectionUpArrow = new int[] { LwjglGlfwKeycode.GLFW_KEY_UP };
    private final int[] mDirectionRightArrow = new int[] { LwjglGlfwKeycode.GLFW_KEY_RIGHT };
    private final int[] mDirectionDownArrow = new int[] { LwjglGlfwKeycode.GLFW_KEY_DOWN };
    private final int[] mDirectionLeftArrow = new int[] { LwjglGlfwKeycode.GLFW_KEY_LEFT };

    private ControlJoystickData mControlData;
    private int mLastDirectionInt = GamepadJoystick.DIRECTION_NONE;
    private int mCurrentDirectionInt = GamepadJoystick.DIRECTION_NONE;

    public ControlJoystick(ControlLayout parent, ControlJoystickData data) {
        super(parent.getContext());
        init(data, parent);
    }

    private static void sendInput(int[] keys, boolean isDown) {
        for (int key : keys) {
            CallbackBridge.sendKeyPress(key, CallbackBridge.getCurrentMods(), isDown);
        }
    }

    private void init(ControlJoystickData data, ControlLayout layout) {
        mControlData = data;
        setProperties(preProcessProperties(data, layout));
        setDeadzone(35);
        setFixedCenter(data.absolute);
        setAutoReCenterButton(true);

        injectBehaviors();

        setOnMoveListener(new OnMoveListener() {
            @Override
            public void onMove(int angle, int strength) {
                mLastDirectionInt = mCurrentDirectionInt;
                mCurrentDirectionInt = getDirectionInt(angle, strength);

                if (mLastDirectionInt != mCurrentDirectionInt) {
                    sendDirectionalKeycode(mLastDirectionInt, false);
                    sendDirectionalKeycode(mCurrentDirectionInt, true);
                }

                // Handle mouse movement for sendMouse mode
                if (mControlData.sendMouse) {
                    sendMousePosition(angle, strength);
                }
            }

            @Override
            public void onForwardLock(boolean isLocked) {
                sendInput(mDirectionForwardLock, isLocked);
            }
        });
    }

    @Override
    public View getControlView() {
        return this;
    }

    @Override
    public ControlData getProperties() {
        return mControlData;
    }

    @Override
    public void setProperties(ControlData properties, boolean changePos) {
        mControlData = (ControlJoystickData) properties;
        mControlData.isHideable = true;
        ControlInterface.super.setProperties(properties, changePos);
        postDelayed(() -> {
            setForwardLockDistance(mControlData.forwardLock ? (int) Tools.dpToPx(60) : 0);
            setFixedCenter(mControlData.absolute);
        }, 10);
    }

    @Override
    public void removeButton() {
        getControlLayoutParent().getLayout().mJoystickDataList.remove(getProperties());
        getControlLayoutParent().removeView(this);
    }

    @Override
    public void cloneButton() {
        ControlJoystickData data = new ControlJoystickData(mControlData);
        getControlLayoutParent().addJoystickButton(data);
    }

    @Override
    public void setBackground() {
        setBorderWidth(
                (int) Tools.dpToPx(getProperties().strokeWidth * (getControlLayoutParent().getLayoutScale() / 100f)));
        setBorderColor(getProperties().strokeColor);
        setBackgroundColor(getProperties().bgColor);
    }

    @Override
    public void sendKeyPresses(boolean isDown) {
        /* STUB since non swipeable */ }

    @Override
    public void loadEditValues(EditControlSideDialog editControlPopup) {
        editControlPopup.loadJoystickValues(mControlData);
    }

    private int getDirectionInt(int angle, int intensity) {
        if (intensity == 0)
            return DIRECTION_NONE;
        return (int) (((angle + 22.5) / 45) % 8);
    }

    /**
     * Send mouse position based on joystick angle and strength.
     * The mouse moves within a fixed radius from the screen center.
     * 
     * @param angle    The joystick angle in degrees (0-360, 0 = right, 90 = up)
     * @param strength The joystick strength (0-100)
     */
    private void sendMousePosition(int angle, int strength) {

        // Don't send mouse position if joystick is released (strength == 0)
        if (strength == 0) {
            return;
        }
        // Get screen center
        float centerX = currentDisplayMetrics.widthPixels / 2f;
        float centerY = currentDisplayMetrics.heightPixels / 2f;

        // Convert angle to radians (joystick uses 0=right, 90=up, counter-clockwise)
        // Note: Screen Y is inverted (positive is down)
        double radians = Math.toRadians(angle);

        // Calculate offset based on strength (0-100) and fixed radius
        float distance = (strength / 100f) * MOUSE_RADIUS;
        float offsetX = (float) (Math.cos(radians) * distance);
        float offsetY = (float) (-Math.sin(radians) * distance); // Negative because screen Y is inverted

        // Calculate final position
        float mouseX = centerX + offsetX;
        float mouseY = centerY + offsetY;

        CallbackBridge.sendCursorPos(mouseX, mouseY);
    }

    private void sendDirectionalKeycode(int direction, boolean isDown) {
        switch (direction) {
            case DIRECTION_NORTH:
                if (mControlData.sendWasd)
                    sendInput(mDirectionForward, isDown);
                if (mControlData.sendArrows)
                    sendInput(mDirectionUpArrow, isDown);
                break;
            case DIRECTION_NORTH_EAST:
                if (mControlData.sendWasd) {
                    sendInput(mDirectionForward, isDown);
                    sendInput(mDirectionRight, isDown);
                }
                if (mControlData.sendArrows) {
                    sendInput(mDirectionUpArrow, isDown);
                    sendInput(mDirectionRightArrow, isDown);
                }
                break;
            case DIRECTION_EAST:
                if (mControlData.sendWasd)
                    sendInput(mDirectionRight, isDown);
                if (mControlData.sendArrows)
                    sendInput(mDirectionRightArrow, isDown);
                break;
            case DIRECTION_SOUTH_EAST:
                if (mControlData.sendWasd) {
                    sendInput(mDirectionRight, isDown);
                    sendInput(mDirectionBackward, isDown);
                }
                if (mControlData.sendArrows) {
                    sendInput(mDirectionRightArrow, isDown);
                    sendInput(mDirectionDownArrow, isDown);
                }
                break;
            case DIRECTION_SOUTH:
                if (mControlData.sendWasd)
                    sendInput(mDirectionBackward, isDown);
                if (mControlData.sendArrows)
                    sendInput(mDirectionDownArrow, isDown);
                break;
            case DIRECTION_SOUTH_WEST:
                if (mControlData.sendWasd) {
                    sendInput(mDirectionBackward, isDown);
                    sendInput(mDirectionLeft, isDown);
                }
                if (mControlData.sendArrows) {
                    sendInput(mDirectionDownArrow, isDown);
                    sendInput(mDirectionLeftArrow, isDown);
                }
                break;
            case DIRECTION_WEST:
                if (mControlData.sendWasd)
                    sendInput(mDirectionLeft, isDown);
                if (mControlData.sendArrows)
                    sendInput(mDirectionLeftArrow, isDown);
                break;
            case DIRECTION_NORTH_WEST:
                if (mControlData.sendWasd) {
                    sendInput(mDirectionForward, isDown);
                    sendInput(mDirectionLeft, isDown);
                }
                if (mControlData.sendArrows) {
                    sendInput(mDirectionUpArrow, isDown);
                    sendInput(mDirectionLeftArrow, isDown);
                }
                break;
            case DIRECTION_FORWARD_LOCK:
                sendInput(mDirectionForwardLock, isDown);
                break;
        }
    }

}
