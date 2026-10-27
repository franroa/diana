package com.limelight.binding.input.virtual_controller;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import com.limelight.Game;
import com.limelight.nvstream.input.KeyboardPacket;
import com.limelight.preferences.PreferenceConfiguration;

/**
 * Turns the position of a stick (-1 ... 1 on both axes, up is positive) into mouse input while it is held:
 * either wheel scrolling (vertical only, faster the further the stick is pushed), or scrolling a game's map
 * in the direction of the stick, which is how strategy games scroll.
 *
 * Map scrolling picks the direction from the angle of the stick: around each diagonal there is a wide corner
 * zone (lines drawn on the stick) that always means that corner, and the rest of the circle is up, down, left
 * or right. Games scroll at their own fixed speed, so the speed buttons (slow, medium, fast) set how much of
 * the time the scrolling is on, in percent per button (settings). It is done by pressing the arrow keys, so
 * the pointer stays where it is, or, as the other way, by moving the pointer to the border of the screen and
 * along it, stepping a little inside the border when the scrolling is off. Letting go stops the scrolling and
 * puts the pointer back in the middle.
 */
class ScrollStickDriver {
    private static final float DEADZONE = 0.12f;
    // High resolution wheel units per tick (120 is one notch) at full deflection
    private static final float MAX_WHEEL_UNITS_PER_TICK = 40f;
    // Half the width of each corner zone in degrees, around 45, 135, 225 and 315 (so a corner zone is 60 wide)
    static final float CORNER_ZONE_HALF_ANGLE = 30f;
    // Pointer speed from the middle to the border, in screen widths per second
    private static final float FLY_SCREENS_PER_SECOND = 5f;
    // Pointer speed along the border while the scrolling is on, in screen widths per second
    // (the same speed in pixels vertically)
    private static final float BORDER_SCREENS_PER_SECOND = 1.5f;
    static final int SPEED_SLOW = 0, SPEED_MEDIUM = 1, SPEED_FAST = 2;
    // How far inside the border (fraction of the screen) the pointer steps while the scrolling is off
    private static final float EDGE_INSET = 0.006f;
    // Windows virtual key codes of the arrow keys
    private static final int VK_LEFT = 0x25, VK_UP = 0x26, VK_RIGHT = 0x27, VK_DOWN = 0x28;
    // Closer than this to an edge (fraction of the screen) counts as on the border
    private static final float ON_BORDER = 0.002f;
    // Reference frame for the absolute positions the pointer is sent to
    private static final short FRAME = 10000;
    private static final long TICK_MS = 16;

    private final Context context;
    // The stick being held: nothing moves unless it is still pressed
    private final View stick;
    private final boolean movePointer;
    private final boolean useKeys;
    // Percent of the time the scrolling is on for each speed button
    private final int[] speedPercent;
    // Arrow keys currently held, indexed by direction: 0 right, 1 up, 2 left, 3 down
    private final boolean[] keyDown = new boolean[4];
    private final Handler handler = new Handler(Looper.getMainLooper());
    private float stickX = 0f;
    private float stickY = 0f;
    private float remainder = 0f;
    // Pointer position as a fraction of the screen (0 ... 1)
    private float pointerX = 0.5f;
    private float pointerY = 0.5f;
    // Whether the stick moved the pointer away from the middle since it was last put back
    private boolean moved = false;
    private boolean ticking = false;
    private int speed = SPEED_MEDIUM;
    // Share of a tick of scrolling owed so far: the on ticks are spread out evenly, not grouped
    private float owed = 0f;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!stick.isPressed()) {
                // The finger is gone but the release never came (touch taken over, view hidden): stop here
                ticking = false;
                release();
                return;
            }
            if (movePointer) {
                if (Math.hypot(stickX, stickY) < DEADZONE) {
                    ticking = false;
                    releaseKeys();
                    return;
                }
                if (useKeys) {
                    pressKeys();
                } else {
                    movePointer();
                }
            } else {
                float magnitude = Math.abs(stickY);
                if (magnitude < DEADZONE) {
                    ticking = false;
                    remainder = 0f;
                    return;
                }
                float units = ease(stickY) * MAX_WHEEL_UNITS_PER_TICK + remainder;
                short send = (short) units;
                remainder = units - send;
                if (send != 0 && context instanceof Game && ((Game) context).conn != null) {
                    ((Game) context).conn.sendMouseHighResScroll(send);
                }
            }
            handler.postDelayed(this, TICK_MS);
        }
    };

    ScrollStickDriver(Context context, View stick) {
        this.context = context;
        this.stick = stick;
        PreferenceConfiguration config = PreferenceConfiguration.readPreferences(context);
        this.movePointer = config.scrollStickMovesPointer;
        this.useKeys = config.scrollStickKeys;
        this.speedPercent = new int[] {
                config.scrollStickSpeedSlow, config.scrollStickSpeedMedium, config.scrollStickSpeedFast};
    }

    /** Ease in so small pushes are slow and precise; keeps the sign and drops the deadzone. */
    private static float ease(float v) {
        float magnitude = Math.abs(v);
        if (magnitude < DEADZONE) {
            return 0f;
        }
        float e = (magnitude - DEADZONE) / (1f - DEADZONE);
        return Math.signum(v) * e * e;
    }

    /**
     * Where on the border the stick points, as a perimeter position 0 ... 4: counterclockwise from the bottom
     * right corner, 0 ... 1 up the right edge, 1 ... 2 along the top, 2 ... 3 down the left, 3 ... 4 the bottom.
     */
    static float borderSpotOf(float angle) {
        // Measured from the bottom right diagonal (-45 degrees), so each quarter of the stick is one edge
        float a = (((angle + 45f) % 360f) + 360f) % 360f;
        int edge = (int) (a / 90f) % 4;
        float within = a - edge * 90f;
        // The corner zones are flat: the whole zone is the corner; the rest of the quarter spans the edge
        float span = 90f - 2 * CORNER_ZONE_HALF_ANGLE;
        float t = Math.max(0f, Math.min(1f, (within - CORNER_ZONE_HALF_ANGLE) / span));
        return edge + t;
    }

    private static float[] pointOf(float p) {
        p = ((p % 4f) + 4f) % 4f;
        int edge = (int) p % 4;
        float t = p - (int) p;
        switch (edge) {
            case 0: return new float[] {1f, 1f - t};
            case 1: return new float[] {1f - t, 0f};
            case 2: return new float[] {0f, t};
            default: return new float[] {t, 1f};
        }
    }

    private float perimeterOf(float x, float y) {
        // Nearest edge decides; only called while the pointer is on the border
        float right = 1f - x, top = y, left = x, bottom = 1f - y;
        float min = Math.min(Math.min(right, top), Math.min(left, bottom));
        if (min == right) return 1f - y;
        if (min == top) return 1f + (1f - x);
        if (min == left) return 2f + y;
        return 3f + x;
    }

    /**
     * Whether the scrolling is on for this tick at the picked speed. The on ticks are spread as evenly as
     * possible (50% is on, off, on, off), so the game sees a steady slower scroll rather than bursts.
     */
    private boolean scrollingOn() {
        owed += speedPercent[speed] / 100f;
        if (owed >= 1f) {
            owed -= 1f;
            return true;
        }
        return false;
    }

    /** Directions the stick points into: bit 0 right, 1 up, 2 left, 3 down; two bits for a corner zone. */
    static int directionsOf(float angle) {
        // Measured from the bottom right diagonal, so each quarter of the circle is one edge
        float a = (((angle + 45f) % 360f) + 360f) % 360f;
        int edge = (int) (a / 90f) % 4;
        float within = a - (int) (a / 90f) * 90f;
        int mask = 1 << edge;
        if (within <= CORNER_ZONE_HALF_ANGLE) {
            mask |= 1 << ((edge + 3) % 4);
        } else if (within >= 90f - CORNER_ZONE_HALF_ANGLE) {
            mask |= 1 << ((edge + 1) % 4);
        }
        return mask;
    }

    private void pressKeys() {
        int mask = scrollingOn() ? directionsOf((float) Math.toDegrees(Math.atan2(stickY, stickX))) : 0;
        int[] codes = {VK_RIGHT, VK_UP, VK_LEFT, VK_DOWN};
        for (int d = 0; d < 4; d++) {
            boolean want = (mask & (1 << d)) != 0;
            if (want != keyDown[d]) {
                keyDown[d] = want;
                if (context instanceof Game && ((Game) context).conn != null) {
                    ((Game) context).conn.sendKeyboardInput((short) (0x8000 | codes[d]),
                            want ? KeyboardPacket.KEY_DOWN : KeyboardPacket.KEY_UP, (byte) 0, (byte) 0);
                }
            }
        }
    }

    private void releaseKeys() {
        owed = 0f;
        for (int d = 0; d < 4; d++) {
            if (keyDown[d]) {
                keyDown[d] = false;
                int[] codes = {VK_RIGHT, VK_UP, VK_LEFT, VK_DOWN};
                if (context instanceof Game && ((Game) context).conn != null) {
                    ((Game) context).conn.sendKeyboardInput((short) (0x8000 | codes[d]),
                            KeyboardPacket.KEY_UP, (byte) 0, (byte) 0);
                }
            }
        }
    }

    private void movePointer() {
        float angle = (float) Math.toDegrees(Math.atan2(stickY, stickX));
        float spot = borderSpotOf(angle);
        View screen = stick.getRootView();
        float aspect = screen.getHeight() > 0 ? (float) screen.getWidth() / screen.getHeight() : 16f / 9f;
        float seconds = TICK_MS / 1000f;
        boolean onBorder = pointerX <= ON_BORDER || pointerX >= 1f - ON_BORDER
                || pointerY <= ON_BORDER || pointerY >= 1f - ON_BORDER;
        float[] target = pointOf(spot);
        if (!onBorder) {
            // In the middle: straight to the spot, fast (same speed in pixels both ways)
            float dx = target[0] - pointerX;
            float dy = (target[1] - pointerY) / aspect;
            float distance = (float) Math.hypot(dx, dy);
            float step = FLY_SCREENS_PER_SECOND * seconds;
            if (distance <= step) {
                pointerX = target[0];
                pointerY = target[1];
            } else {
                pointerX += dx / distance * step;
                pointerY += dy / distance * step * aspect;
            }
        } else {
            // On the border: along it, the short way round
            float here = perimeterOf(pointerX, pointerY);
            float delta = ((spot - here) % 4f + 6f) % 4f - 2f;
            boolean vertical = (here % 2f) < 1f;   // right and left edges
            float step = BORDER_SCREENS_PER_SECOND * seconds * (vertical ? aspect : 1f);
            float[] next = pointOf(Math.abs(delta) <= step ? spot : here + Math.signum(delta) * step);
            pointerX = next[0];
            pointerY = next[1];
            moved = true;
            if (!scrollingOn()) {
                // Off the edge for this tick: a little inside on the axes where it is on the border
                float x = pointerX <= ON_BORDER ? EDGE_INSET : pointerX >= 1f - ON_BORDER ? 1f - EDGE_INSET : pointerX;
                float y = pointerY <= ON_BORDER ? EDGE_INSET * aspect
                        : pointerY >= 1f - ON_BORDER ? 1f - EDGE_INSET * aspect : pointerY;
                sendPosition(x, y);
                return;
            }
        }
        moved = true;
        sendPointer();
    }

    private void sendPointer() {
        sendPosition(pointerX, pointerY);
    }

    private void sendPosition(float x, float y) {
        if (context instanceof Game && ((Game) context).conn != null) {
            // Keep a pixel inside: a position on the last pixel row or column is outside the screen
            x = Math.max(0.001f, Math.min(0.999f, x));
            y = Math.max(0.001f, Math.min(0.999f, y));
            ((Game) context).conn.sendMousePosition((short) (x * FRAME), (short) (y * FRAME), FRAME, FRAME);
        }
    }

    /** Speed along the border, one of SPEED_SLOW, SPEED_MEDIUM and SPEED_FAST. */
    void setSpeed(int speed) {
        this.speed = speed;
    }

    int getSpeed() {
        return speed;
    }

    void setStick(float x, float y) {
        stickX = x;
        stickY = y;
        double magnitude = movePointer ? Math.hypot(x, y) : Math.abs(y);
        if (!ticking && magnitude >= DEADZONE) {
            ticking = true;
            handler.post(tick);
        }
    }

    void release() {
        stickX = 0f;
        stickY = 0f;
        releaseKeys();
        if (movePointer && moved) {
            // Let go: the pointer goes straight back to the middle, so a game that scrolls at the screen
            // edge stops. Nothing is sent if the pointer was never moved by the stick.
            moved = false;
            pointerX = 0.5f;
            pointerY = 0.5f;
            sendPointer();
        }
    }
}
