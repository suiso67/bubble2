package com.nkanaev.comics.view;

import android.view.View;
import android.view.animation.Animation;
import android.view.animation.Transformation;

public class CircularPathAnimation extends Animation {

    private View view;
    // center x,y position of circular path
    private float cx, cy;
    private float prevX, prevY;
    private float radius;
    private float prevDx, prevDy;

    /**
     * @param radius - radius of circular path
     */
    public CircularPathAnimation(float radius) {
        this.radius = radius;
    }

    @Override
    public boolean willChangeBounds() {
        return true;
    }

    @Override
    public void initialize(int width, int height, int parentWidth, int parentHeight) {
        // memorize original position of image center
        cx = width / 2;
        cy = height / 2;

        // set previous position to center
        prevX = cx;
        prevY = cy;
    }

    @Override
    protected void applyTransformation(float interpolatedTime, Transformation t) {
        if (interpolatedTime == 0) {
            t.getMatrix().setTranslate(prevDx, prevDy);
            return;
        }

        // calculate new angle
        float angleDeg = (interpolatedTime * 360f + 90) % 360;
        float angleRad = (float) Math.toRadians(angleDeg);

        // calculate new position
        float x = (float) (cx + radius * Math.cos(angleRad));
        float y = (float) (cy + radius * Math.sin(angleRad));

        // calculate difference for translation
        float dx = prevX - x;
        float dy = prevY - y;

        prevX = x;
        prevY = y;

        prevDx = dx;
        prevDy = dy;

        t.getMatrix().setTranslate(dx, dy);
    }
}