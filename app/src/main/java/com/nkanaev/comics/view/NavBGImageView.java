package com.nkanaev.comics.view;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.nkanaev.comics.R;
import com.nkanaev.comics.managers.LocalCoverHandler;
import com.nkanaev.comics.managers.Utils;
import com.nkanaev.comics.model.Comic;
import com.nkanaev.comics.model.Storage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;

public class NavBGImageView extends androidx.appcompat.widget.AppCompatImageView {
    private static Bitmap mLastBitmap = null;

    public NavBGImageView(Context context) {
        super(context);
        init();
    }

    public NavBGImageView(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        init();
    }

    public NavBGImageView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init(){
        if (mLastBitmap!=null)
            setImageBitmap(mLastBitmap);

        final GestureDetector gDetector = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return false;
            }
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                reset();
                return true;
            }
            @Override
            public void onLongPress(@NonNull MotionEvent e) {
                reset();
            }
        });
        setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                gDetector.onTouchEvent(event);
                return true;
            }
        });
    }

    @Override
    protected boolean setFrame(int l, int t, int r, int b) {
        boolean changed = super.setFrame(l, t, r, b);
        if (changed)
            reset();
        return changed;
    }

    @Override
    public void setImageBitmap(Bitmap bm) {
        mLastBitmap = bm;
        super.setImageBitmap(bm);
    }

    private long lastRun = 0;

    private synchronized void createBitmap() {
        if ( !Utils.isJellyBeanMR1orLater() || getWidth() < 1 || getHeight() < 1)
            return;

        // skip if last run was under 3s (3000ms) ago, give Animation and HalftonerTask time to finish
        long now = System.currentTimeMillis();
        if (lastRun + 3000 >= now)
            return;

        lastRun = now;

        ArrayList<Comic> comics = Storage.getStorage(getActivity()).listComics();
        if (comics.size() > 0) {
            Comic c = comics.get(new Random().nextInt(comics.size()));
            //Picasso mPicasso = ((MainActivity) getActivity()).getPicasso();
            Uri uri = LocalCoverHandler.getComicCoverUri(c);
            try {
                Bitmap bitmap = LocalCoverHandler.getCover(uri);
                if (bitmap==null)
                    return;
                HalftonerTask task = new HalftonerTask(bitmap);
                task.execute();
                //mDrawable = new BitmapDrawable(getActivity().getResources(), bitmap);

            } catch (IOException e) {
                Log.e("bubble2","error",e);
            }

        }
    }

    private Activity getActivity() {
        Context context = getContext();
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) {
                return (Activity)context;
            }
            context = ((ContextWrapper)context).getBaseContext();
        }
        return null;
    }

    public void reset (){
        createBitmap();
    }

    private class HalftonerTask extends AsyncTask<Void, Void, Bitmap> {
        private Bitmap mBitmap;

        public HalftonerTask(Bitmap bitmap) {
            mBitmap = bitmap;
        }

        @Override
        protected Bitmap doInBackground(Void... params) {
            double bw = mBitmap.getWidth();
            double bh = mBitmap.getHeight();
            double vw = getWidth();
            double vh = getHeight();

            int nbw, nbh, bx, by;
            if (bh/bw > vh/vw) {
                nbw = (int)vw;
                nbh = (int)(bh * (vw / bw));
                bx = 0;
                by = (int)((double)nbh / 2 - vh / 2);
            }
            else {
                nbw = (int)(bw * (vh / bh));
                nbh = (int)vh;
                bx = (int)((double)nbw / 2 - vw / 2);
                by = 0;
            }

            Bitmap scaled = Bitmap.createScaledBitmap(mBitmap, nbw, nbh, false);
            Bitmap mutable = scaled.copy(Bitmap.Config.ARGB_8888, true);
            Bitmap bitmap = Bitmap.createBitmap(mutable, bx, by, (int)vw, (int)vh);

            double s = Math.PI/6;
            int a, r, g, b, l, t, f, p;
            int primary = getResources().getColor(R.color.primary);
            for (int y = 0; y < bitmap.getHeight(); y++) {
                for (int x = 0; x < bitmap.getWidth(); x++) {
                    p = bitmap.getPixel(x, y);
                    a = Color.alpha(p);
                    r = Color.red(p);
                    g = Color.green(p);
                    b = Color.blue(p);
                    l = (int)(0.299 * r + 0.587 * g + 0.114 * b);
                    t = (int)((Math.cos(s*(x+0.5))*Math.cos(s*(y+0.5))+1)*127);
                    f = (l > t) ? primary : Color.argb(a, 0, 0, 0);
                    bitmap.setPixel(x, y, f);
                }
            }

            RenderScript rs = RenderScript.create(getActivity());
            Allocation input = Allocation.createFromBitmap(rs, bitmap);
            Allocation output = Allocation.createTyped(rs, input.getType());
            ScriptIntrinsicBlur script = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs));
            script.setRadius(1);
            script.setInput(input);
            script.forEach(output);
            output.copyTo(bitmap);

            return bitmap;
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            try {
                //mDrawable = new BitmapDrawable(getActivity().getResources(), bitmap);
                //invalidate();
                Drawable old = getDrawable();
                if (old instanceof BitmapDrawable){
                    ImageView v = getActivity().findViewById(R.id.drawer_bg_image2);
                    v.setImageDrawable(old);
                }
                setAlpha(0f);
                setImageBitmap(bitmap);
                animate().alpha(1).setDuration(2000).withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        // reset multiple run protection
                        lastRun = 0;
                    }
                }).setListener(null);
            }
            catch (Exception e) {
                Log.e("bubble2","error",e);
            }
            finally {
                Utils.close(mBitmap);
            }
        }
    }
}