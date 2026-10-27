package com.limelight.binding.input.virtual_controller;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;

/**
 * A button that picks how fast the scroll stick scrolls: L(ow), M(edium) or H(igh), the letter drawn on a
 * face button. The picked one keeps a ring around it.
 */
public class SpeedButton extends DigitalButton {
    private final ScrollStickDriver driver;
    private final int speed;
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint letterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final String letter;

    public SpeedButton(final VirtualController controller, final Context context, int elementId,
                       final ScrollStickDriver driver, final int speed) {
        super(controller, elementId, 1, context);
        this.letter = speed == ScrollStickDriver.SPEED_SLOW ? "L" : speed == ScrollStickDriver.SPEED_MEDIUM ? "M" : "H";
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
        // Like the letters of the face buttons: dark on the button, light while it is pressed
        letterPaint.setTextAlign(Paint.Align.CENTER);
        letterPaint.setTextSize(getHeight() * 0.5f);
        letterPaint.setFakeBoldText(true);
        letterPaint.setColor(isPressed() ? 0xBFFFFFFF : 0x99000000);
        float baseline = getHeight() / 2f - (letterPaint.descent() + letterPaint.ascent()) / 2f;
        canvas.drawText(letter, getWidth() / 2f, baseline, letterPaint);
        if (driver.getSpeed() == speed) {
            ringPaint.setStyle(Paint.Style.STROKE);
            ringPaint.setStrokeWidth(getDefaultStrokeWidth() * 2);
            ringPaint.setColor(pressedColor);
            float inset = ringPaint.getStrokeWidth();
            canvas.drawOval(inset, inset, getWidth() - inset, getHeight() - inset, ringPaint);
        }
    }
}
