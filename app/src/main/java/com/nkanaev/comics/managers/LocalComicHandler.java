package com.nkanaev.comics.managers;

import android.net.Uri;
import com.nkanaev.comics.parsers.*;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Request;
import com.squareup.picasso.RequestHandler;
import okio.Okio;

import java.io.IOException;
import java.io.InputStream;


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
        return new Result(Okio.source(stream), Picasso.LoadedFrom.DISK);
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
