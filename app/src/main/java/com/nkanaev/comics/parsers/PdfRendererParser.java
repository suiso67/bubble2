package com.nkanaev.comics.parsers;

import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import com.nkanaev.comics.BuildConfig;
import com.nkanaev.comics.MainApplication;
import com.nkanaev.comics.managers.Utils;

import java.io.*;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class PdfRendererParser extends AbstractParser {
    int mPageCount = 0;
    private HashMap<Integer, Map> mPagesMetaData = new HashMap<>();

    public PdfRendererParser() {
        super(new Class[]{File.class});
    }

    // creating pdf parser is cheap, no use to reuse it
    private PdfRenderer createPdfRenderer() throws IOException {
        File file = (File) getSource();
        ParcelFileDescriptor pd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
        return new PdfRenderer(pd);
    }

    @Override
    public synchronized void parse() throws IOException {
        if (mPageCount>0) return;

        PdfRenderer pr = createPdfRenderer();
        mPageCount = pr.getPageCount();
        Utils.close(pr);
    }

    @Override
    public InputStream getPage(int num) throws IOException {
        long start = Utils.now();
        // read image
        ByteArrayOutputStream bos = null;
        Bitmap bitmap = null;
        PdfRenderer pr = null;
        PdfRenderer.Page page = null;
        try {
            pr = createPdfRenderer();
            page = pr.openPage(num);
            // pdf sizes are relative to the document
            // let's calculate a pixel size that fits on our device keeping the aspect ratio
            float aspect = (float) page.getWidth() / page.getHeight();
            int maxSize = Utils.getMaxPageSize();
            int w = aspect <= 1 ? maxSize : Math.round(aspect * maxSize);
            int h = aspect >= 1 ? maxSize : Math.round(maxSize / aspect);
            bitmap = Bitmap.createBitmap(
                    w,
                    h,
                    Bitmap.Config.ARGB_8888
            );
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);

            long start2 = Utils.now();
            // write to in-memory stream
            bos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100 /*jpg100 is way faster than png or webp, dunnowhy*/, bos);
            byte[] byteArray = bos.toByteArray();
            if (BuildConfig.DEBUG) {
                String text = "getPage(" + num + ") " + Utils.milliSecondsSince(start) + ", recode " + Utils.milliSecondsSince(start2);
                mPagesMetaData.put(Integer.valueOf(num), Collections.singletonMap("debug-pdf",text));
                Log.d("bubble2 pdf", text);
            }
            return new ByteArrayInputStream(byteArray);
        } finally {
            // cleanup, close everything
            Utils.close(page);
            Utils.close(bos);
            Utils.close(bitmap);
            // in case we were closed during rendering
            Utils.close(pr);
        }
    }

    @Override
    public Map getPageMetaData(int num) throws IOException {
        Integer key = Integer.valueOf(num);
        if (mPagesMetaData.containsKey(key))
            return new HashMap<>(mPagesMetaData.get(key));

        return super.getPageMetaData(num);
    }

    @Override
    public int numPages() throws IOException {
        parse();
        return mPageCount;
    }

    @Override
    public String getType() {
        return "pdf";
    }

    @Override
    public void destroy() {
        super.destroy();
    }
}
