package com.nkanaev.comics.parsers;


import com.nkanaev.comics.managers.NaturalOrderComparator;
import com.nkanaev.comics.managers.Utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;

public class DirectoryParser extends AbstractParser {
    private ArrayList<File> mFiles = null;
    public static final String TYPE = "folder";

    public DirectoryParser() {
        super(new Class[]{File.class});
    }

    @Override
    public void parse() throws IOException {
        if (mFiles != null) return;

        File dir = (File) getSource();

        if (!dir.isDirectory()) {
            throw new IOException("Not a directory: " + dir.getAbsolutePath());
        }

        File[] files = dir.listFiles();
        mFiles = new ArrayList<>();
        if (files != null) {
            for (File f : dir.listFiles()) {
                if (f.isFile() && Utils.isImage(f.getName())) {
                    mFiles.add(f);
                }
            }
        }

        Collections.sort(mFiles, new NaturalOrderComparator() {
            @Override
            public String stringValue(Object o) {
                return ((File) o).getName();
            }
        });
    }

    @Override
    public int numPages() throws IOException {
        parse();
        return mFiles.size();
    }

    @Override
    public InputStream getPage(int num) throws IOException {
        parse();
        return new FileInputStream(mFiles.get(num));
    }

    @Override
    public String getType() {
        return TYPE;
    }
}
