package com.limelight.binding.input.virtual_controller;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;

/**
 * A button that picks how fast the scroll stick moves the pointer along the screen border. The picked one
 * keeps a ring around it.
 */
public class SpeedButton extends DigitalButton {
    private final ScrollStickDriver driver;
    private final int speed;
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public SpeedButton(final VirtualController controller, final Context context, int elementId,
                       final ScrollStickDriver driver, final int speed) {
        super(controller, elementId, 1, context);
        this.driver = driver;
        this.speed = speed;

        addDigitalButtonListener(new DigitalButtonListener() {
            @Override
            public void onClick() {
                driver.setSpeed(speed);
                // Move the ring from the button picked before
                for (VirtualControllerElement element : controller.getElements()) {
                    if (element instanceof SpeedButton) {
                        element.invalidate();
                    }
                }
            }

            @Override
            public void onLongClick() {
            }

            @Override
            public void onRelease() {
            }
        });
    }

    @Override
    protected void onElementDraw(Canvas canvas) {
        super.onElementDraw(canvas);
        if (driver.getSpeed() == speed) {
            ringPaint.setStyle(Paint.Style.STROKE);
            ringPaint.setStrokeWidth(getDefaultStrokeWidth() * 2);
            ringPaint.setColor(pressedColor);
            float inset = ringPaint.getStrokeWidth();
            canvas.drawOval(inset, inset, getWidth() - inset, getHeight() - inset, ringPaint);
        }
    }
}
