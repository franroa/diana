package com.limelight.binding.input.virtual_controller;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;

/**
 * A AnalogStick that scrolls the mouse wheel instead of moving a gamepad stick: pushing it up scrolls up,
 * down scrolls down.
 */
public class ScrollAnalogStick extends AnalogStick implements ScrollStick {
    private final ScrollStickDriver driver;
    private final Paint zonePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public ScrollAnalogStick(final VirtualController controller, final Context context) {
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

    @Override
    protected void onElementDraw(Canvas canvas) {
        super.onElementDraw(canvas);

        // The borders of the four corner zones (see ScrollStickDriver): short lines between the dead zone and the rim
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float outer = Math.min(getWidth(), getHeight()) / 2f - 2 * getDefaultStrokeWidth();
        float inner = outer * 0.3f;
        zonePaint.setStyle(Paint.Style.STROKE);
        zonePaint.setStrokeWidth(getDefaultStrokeWidth());
        zonePaint.setColor(getDefaultColor());
        for (int quarter = 0; quarter < 4; quarter++) {
            for (float side : new float[] {-1f, 1f}) {
                double a = Math.toRadians(quarter * 90 + 45 + side * ScrollStickDriver.CORNER_ZONE_HALF_ANGLE);
                float dx = (float) Math.cos(a);
                float dy = (float) -Math.sin(a);   // screen y grows downwards
                canvas.drawLine(cx + dx * inner, cy + dy * inner, cx + dx * outer, cy + dy * outer, zonePaint);
            }
        }
    }
}
