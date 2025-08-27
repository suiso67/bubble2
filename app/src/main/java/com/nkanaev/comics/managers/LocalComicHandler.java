package com.nkanaev.comics.managers;

import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Log;
import com.nkanaev.comics.parsers.*;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Request;
import com.squareup.picasso.RequestHandler;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.FileOutputStream;
import java.util.UUID;


public class LocalComicHandler extends RequestHandler {
    private final static String HANDLER_URI = "localcomic";
    private final Parser mParser;

    public LocalComicHandler(Parser parser) {
        mParser = parser;
    }

    @Override
    public boolean canHandleRequest(Request request) {
        return HANDLER_URI.equals(request.uri.getScheme());
    }

    @Override
    public Result load(Request request, int networkPolicy) throws IOException {
        int pageNum = Integer.parseInt(request.uri.getFragment());
        InputStream stream = mParser.getPage(pageNum);

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(stream, null, options);
        Utils.close(stream);
        int w = options.outWidth;
        int h = options.outHeight;
        String id = w+"x"+h;
        int maxMemorySize = Utils.bitmapMaxMemorySize();

        options = new BitmapFactory.Options();
        // dying on mangastrips with GL error, max texture size?
        if (false && Utils.isOreoOrLater() && Math.max(w,h) <= Utils.glMaxTextureSize())
            options.inPreferredConfig = Bitmap.Config.HARDWARE;
        // assuming default 4byte Bitmap.Config.ARGB_8888
        if (maxMemorySize > 0 && (long)w * h * 4 > maxMemorySize) {
            // check if resample is needed assuming 4byte Bitmap.Config.ARGB_4444
            int inSampleSize = 1;
            while ((long)w * h * 4 > maxMemorySize) {
                inSampleSize *= 2;
                h /= 2;
                w /= 2;
            }
            options.inSampleSize = inSampleSize;
            Log.d("inSampleSize "+id, String.valueOf(inSampleSize));
        }

        stream = mParser.getPage(pageNum);

        Bitmap result = null;
        if (Utils.isAvif(stream)) {
            result = Utils.decodeAvif(stream, w, h);
        } else {
            result = BitmapFactory.decodeStream(stream, null, options);
        }

        if (Utils.isKitKatOrLater()) {
            int m = result.getAllocationByteCount();
            Log.d("alloc " + id, String.valueOf(m));
        }
        Log.d("config "+id, result.getConfig().toString());
/*
        Bitmap oldResult = result;
        // make extra sure result ist 565
        if (false && result != null) {
            result = result.copy(Bitmap.Config.RGB_565, false);
            Utils.close(oldResult);
        }
*/
        Utils.close(stream);
        return new Result(result, Picasso.LoadedFrom.DISK);
        //return new Result(Okio.source(stream), Picasso.LoadedFrom.DISK);
    }

/*  // trial to adapt to picasso 3.0.0-alpha
    // keeping this for reference.
    @Override
    public void load(@NotNull Picasso picasso, @NotNull Request request, @NotNull Callback callback) throws IOException {
        InputStream stream = null;
        Bitmap bitmap = null;
        try {
            int pageNum = Integer.parseInt(request.uri.getFragment());
            stream = mParser.getPage(pageNum);
            //return new Result(stream, Picasso.LoadedFrom.DISK);
            bitmap = BitmapFactory.decodeStream(stream);
            Result result = new Result.Bitmap(bitmap, Picasso.LoadedFrom.DISK, 0);
            callback.onSuccess(result);
        } catch (Throwable e) {
            callback.onError(e);
        }
        finally {
            Utils.close(stream);
        }
    }
*/

    public Uri getPageUri(int pageNum) {
        return new Uri.Builder()
                .scheme(HANDLER_URI)
                .authority("")
                .fragment(Integer.toString(pageNum))
                .build();
    }
}
