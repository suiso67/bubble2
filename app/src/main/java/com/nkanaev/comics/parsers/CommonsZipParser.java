package com.nkanaev.comics.parsers;

import com.nkanaev.comics.managers.IgnoreCaseComparator;
import com.nkanaev.comics.managers.Utils;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;


public class CommonsZipParser extends AbstractParser {
    private ZipFile mZipFile = null;
    private ArrayList<ZipArchiveEntry> mEntries = null;

    public CommonsZipParser() {
        super(new Class[]{File.class});
    }

    @Override
    public void parse() throws IOException {
        if (mZipFile != null && mEntries != null)
            return;

        File file = (File) getSource();

        mZipFile = new ZipFile(file.getAbsolutePath());
        mEntries = new ArrayList<>();

        Enumeration<ZipArchiveEntry> e = mZipFile.getEntries();
        while (e.hasMoreElements()) {
            ZipArchiveEntry ze = e.nextElement();
            if (!ze.isDirectory() && Utils.isImage(ze.getName())) {
                mEntries.add(ze);
            }
        }

        //noinspection unchecked
        Collections.sort(mEntries,
                new IgnoreCaseComparator() {
                    @Override
                    public String stringValue(Object o) {
                        return ((ZipArchiveEntry) o).getName();
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
    public Map getPageMetaData(int num) throws IOException {
        parse();
        Map m = new HashMap();
        m.put(Parser.PAGEMETADATA_KEY_NAME,mEntries.get(num).getName());
        return m;
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
