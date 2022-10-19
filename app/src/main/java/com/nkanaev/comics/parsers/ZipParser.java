package com.nkanaev.comics.parsers;

import com.nkanaev.comics.managers.NaturalOrderComparator;
import com.nkanaev.comics.managers.Utils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;


public class ZipParser extends AbstractParser {
    private ZipFile mZipFile = null;
    private ArrayList<ZipEntry> mEntries = null;

    public ZipParser() {
        super(new Class[]{File.class});
    }

    @Override
    public void parse() throws IOException {
        if (mZipFile != null && mEntries != null)
            return;

        File file = (File) getSource();

        mZipFile = new ZipFile(file.getAbsolutePath());
        mEntries = new ArrayList<ZipEntry>();

        Enumeration<? extends ZipEntry> e = mZipFile.entries();
        while (e.hasMoreElements()) {
            ZipEntry ze = e.nextElement();
            if (!ze.isDirectory() && Utils.isImage(ze.getName())) {
                mEntries.add(ze);
            }
        }

        Collections.sort(mEntries, new NaturalOrderComparator() {
            @Override
            public String stringValue(Object o) {
                return ((ZipEntry) o).getName();
            }
        });
    }

    @Override
    public int numPages() throws IOException {
        // lazy parse
        parse();
        return mEntries.size();
    }

    @Override
    public InputStream getPage(int num) throws IOException {
        parse();
        return mZipFile.getInputStream(mEntries.get(num));
    }

    @Override
    public String getType() {
        return "zip";
    }

    @Override
    public void destroy() {
        Utils.close(mZipFile);
        mZipFile = null;
        mEntries = null;
    }

}
