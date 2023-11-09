package com.nkanaev.comics.view;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import androidx.appcompat.widget.AppCompatImageView;

public class CoverImageView extends AppCompatImageView {
    // The standard American comic page size is 6.875 by 10.438 inches with bleed
    public static final double FACTOR = 6.875/10.438;

    public CoverImageView(Context context) {
        super(context);
    }

    public CoverImageView(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int width = getMeasuredWidth();
        setMeasuredDimension(width, (int)(width * 1/ FACTOR) );
    }

    @Override
    public void setImageDrawable(Drawable drawable) {
        super.setImageDrawable(drawable);
    }

    @Override
    protected boolean setFrame(int l, int t, int r, int b) {
        final boolean changed = super.setFrame(l, t, r, b);
        Drawable drawable = getDrawable();
        if (drawable==null)
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
}
