package com.nkanaev.comics.view;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import androidx.appcompat.widget.AppCompatImageView;

public class GroupImageView extends AppCompatImageView {

    public GroupImageView(Context context) {
        this(context,null);
    }

    public GroupImageView(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        setScaleType(ScaleType.MATRIX);
    }

    @Override
    public void setImageDrawable(Drawable drawable) {
        super.setImageDrawable(drawable);
        scaleV2();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int width = getMeasuredWidth();
        // tiles are quadratic 1:1
        setMeasuredDimension(width, width);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        scaleV2();
    }

    private void scale() {
        if (getDrawable() != null) {
            Matrix matrix = getImageMatrix();
            float scaleFactor = getWidth() / (float) getDrawable().getIntrinsicWidth();
            matrix.setScale(scaleFactor, scaleFactor, 0, 0);
            setImageMatrix(matrix);
        }
    }

    @Override
    protected boolean setFrame(int l, int t, int r, int b) {
        if (true)
            return super.setFrame(l,t,r,b);

        final boolean changed = super.setFrame(l, t, r, b);
        Drawable drawable = getDrawable();
        if (drawable == null)
            return changed;

        Matrix matrix = getImageMatrix();
        matrix.reset();

        int vwidth = getMeasuredWidth() - getPaddingLeft() - getPaddingRight();
        int vheight = getMeasuredHeight() - getPaddingTop() - getPaddingBottom();
        int dwidth = drawable.getIntrinsicWidth();
        int dheight = drawable.getIntrinsicHeight();

        float scale;
        float dx = 0, dy = 0;

        // landscape format
        if (dwidth > dheight) {
            // fit to height, crop left side overflow assuming
            // it is a wraparound with actual cover on the right side
            scale = (float) vheight / (float) dheight;
            dx = (vwidth - dwidth * scale) * 1f;
        }
        // portrait, just fit to width
        else {
            scale = (float) vwidth / (float) dwidth;
            //dy = (vheight - dheight * scale) * 0.5f;
        }

        matrix.setScale(scale, scale);
        matrix.postTranslate(Math.round(dx), Math.round(dy));

        setImageMatrix(matrix);

        return changed;
    }

    private void scaleV2() {

        Drawable drawable = getDrawable();
        if (drawable == null)
            return;

        Matrix matrix = getImageMatrix();
        matrix.reset();

        int vwidth = getMeasuredWidth() - getPaddingLeft() - getPaddingRight();
        int vheight = getMeasuredHeight() - getPaddingTop() - getPaddingBottom();
        int dwidth = drawable.getIntrinsicWidth();
        int dheight = drawable.getIntrinsicHeight();

        float scale;
        float dx = 0, dy = 0;

        // landscape format
        if (dwidth > dheight) {
            // fit to height, crop left side overflow assuming
            // it is a wraparound with actual cover on the right side
            scale = (float) vheight / (float) dheight;
            dx = (vwidth - dwidth * scale) * 1f;
        }
        // portrait, just fit to width
        else {
            scale = (float) vwidth / (float) dwidth;
            //dy = (vheight - dheight * scale) * 0.5f;
        }

        matrix.setScale(scale, scale);
        matrix.postTranslate(Math.round(dx), Math.round(dy));

        setImageMatrix(matrix);
    }
}
