package com.nkanaev.comics.view;

import android.content.Context;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class PreCachingGridLayoutManager extends GridLayoutManager {
    private int extraLayoutSpace = -1;

    public PreCachingGridLayoutManager(Context context, int spanCount) {
        super(context, spanCount);
    }

    public void setExtraLayoutSpace(int extraLayoutSpace) {
        this.extraLayoutSpace = extraLayoutSpace;
    }

    @Override
    protected int getExtraLayoutSpace(RecyclerView.State state) {
        if (extraLayoutSpace > 0) {
            return extraLayoutSpace;
        }
        return super.getExtraLayoutSpace(state);
    }
}