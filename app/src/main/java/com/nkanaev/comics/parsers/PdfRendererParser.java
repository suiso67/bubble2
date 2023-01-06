package com.nkanaev.comics.parsers;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;
import com.nkanaev.comics.MainApplication;
import com.nkanaev.comics.managers.Utils;

import java.io.*;

public class PdfRendererParser extends AbstractParser{
    PdfRenderer renderer = null;

    public PdfRendererParser() {
        super(new Class[]{File.class});
    }

    @Override
    public void parse() throws IOException {
        File file = (File) getSource();
        ParcelFileDescriptor pd = ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY);
        renderer = new PdfRenderer(pd);
    }

    @Override
    public synchronized InputStream getPage(int num) throws IOException {
        if (renderer==null)
            parse();

        // read image
        PdfRenderer.Page page = null;
        ByteArrayOutputStream bos = null;
        Bitmap bitmap = null;
        try {
            page = renderer.openPage(num);
            int ddpi = MainApplication.getAppContext().getResources().getDisplayMetrics().densityDpi;
            // pdf sizes are relative to the document
            // let's calculate a pixel size that fits on our device keeping the aspect ratio
            float aspect = (float)page.getWidth()/page.getHeight();
            int maxSize = Utils.getMaxPageSize();
            int w = aspect>1?maxSize:Math.round(aspect*maxSize);
            int h = aspect<1?maxSize:Math.round(maxSize/aspect);
            bitmap = Bitmap.createBitmap(
                    w,
                    h,
                    Bitmap.Config.ARGB_8888
            );
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);

            // write to in-memory stream
            bos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100 /*jpg100 is way faster than png, dunnowhy*/, bos);
            byte[] byteArray = bos.toByteArray();
            return new ByteArrayInputStream(byteArray);
        } finally {
            // make sure pages are closed always
            Utils.close(page);
            Utils.close(bos);
            if (bitmap!=null) bitmap.recycle();
        }
    }

    @Override
    public int numPages() throws IOException {
        if (renderer==null)
            parse();
        return renderer.getPageCount();
    }

    @Override
    public String getType() {
        return "pdf";
    }

    @Override
    public void destroy() {
        Utils.close(renderer);
        renderer = null;
        super.destroy();
    }
}
