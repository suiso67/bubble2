package com.nkanaev.comics.parsers;

import android.graphics.Bitmap;
import com.nkanaev.comics.MainApplication;
import com.nkanaev.comics.managers.Utils;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.rendering.ImageType;
import com.tom_roush.pdfbox.rendering.PDFRenderer;

import java.io.*;

public class PdfParser extends AbstractParser{
    PDDocument document = null;

    public PdfParser() {
        super(new Class[]{File.class});
    }

    @Override
    public void parse() throws IOException {
        PDFBoxResourceLoader.init(MainApplication.getAppContext());
        File file = (File) getSource();
        document = PDDocument.load(file);
    }

    @Override
    public InputStream getPage(int num) throws IOException {
        if (document==null)
            parse();

        // read image
        PDFRenderer renderer = new PDFRenderer(document);
        Bitmap bitmap = renderer.renderImage(num, 1, ImageType.RGB);
        // write to in-memory stream
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 0 /*png is lossless*/, bos);
        byte[] byteArray = bos.toByteArray();
        Utils.close(bos);
        bitmap.recycle();

        return new ByteArrayInputStream(byteArray);
    }

    @Override
    public int numPages() throws IOException {
        if (document==null)
            parse();
        return document.getNumberOfPages();
    }

    @Override
    public String getType() {
        return "pdf";
    }

    @Override
    public void destroy() {
        Utils.close(document);
        document = null;
        super.destroy();
    }
}
