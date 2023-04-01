package com.nkanaev.comics.view;

import android.view.animation.Animation;
import android.view.animation.Transformation;

public class CircularPathAnimation extends Animation {
    private float cx, cy;
    private float prevX, prevY;
    private final float radius;
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
        cx = width / 2f;
        cy = height / 2f;

        // match below anim start position of 90deg up
        // so image does not jump on anim start
        prevX = cx;
        prevY = cy-radius;
    }

    @Override
    protected void applyTransformation(float interpolatedTime, Transformation t) {
        if (interpolatedTime == 0) {
            t.getMatrix().setTranslate(prevDx, prevDy);
            return;
        }

        // calculate new angle
        float angleDeg = (interpolatedTime * 360f - 90) % 360;
        float angleRad = (float) Math.toRadians(angleDeg);

        // calculate new position
        float x = (float) (cx + radius * Math.cos(angleRad));
        float y = (float) (cy + radius * Math.sin(angleRad));

        // calculate difference for translation
        float dx = x -prevX;
        float dy = y -prevY;

        prevX = x;
        prevY = y;

        prevDx = dx;
        prevDy = dy;

        t.getMatrix().setTranslate(dx, dy);
    }
}