package com.limelight.binding.input.virtual_controller;

import android.content.Context;
import android.view.MotionEvent;

/**
 * A AnalogStickFree that scrolls the mouse wheel instead of moving a gamepad stick: pushing it up scrolls up,
 * down scrolls down.
 */
public class ScrollAnalogStickFree extends AnalogStickFree implements ScrollStick {
    private final ScrollStickDriver driver;

    public ScrollAnalogStickFree(final VirtualController controller, final Context context) {
        super(controller, context, EID_SCROLL);

        driver = new ScrollStickDriver(context, this);

        addAnalogStickListener(new AnalogStickListener() {
            @Override
            public void onMovement(float x, float y) {
                driver.setStick(x, y);
            }

            @Override
            public void onClick() {
            }

            @Override
            public void onDoubleClick() {
            }

            @Override
            public void onRevoke() {
                driver.release();
            }
        });
    }

    @Override
    public ScrollStickDriver getDriver() {
        return driver;
    }

    @Override
    public boolean onElementTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_POINTER_UP && event.getActionIndex() == 0) {
            // The finger on the stick lifted while another one stays down: that is still a release
            MotionEvent up = MotionEvent.obtain(event);
            up.setAction(MotionEvent.ACTION_UP);
            boolean handled = super.onElementTouchEvent(up);
            up.recycle();
            return handled;
        }
        return super.onElementTouchEvent(event);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        setPressed(false);
        driver.release();
    }
}
