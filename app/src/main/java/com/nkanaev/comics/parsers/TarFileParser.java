package com.nkanaev.comics.parsers;


import com.nkanaev.comics.managers.IgnoreCaseComparator;
import com.nkanaev.comics.managers.Utils;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarFile;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorStreamFactory;

import java.io.*;
import java.util.*;

public class TarFileParser extends AbstractParser {
    private File mUncompressedFile = null;
    private TarFile mTarFile = null;
    private List<TarArchiveEntry> mEntries = new ArrayList();

    private boolean mParsedAlready = false;

    public TarFileParser() {
        super(new Class[]{File.class});
    }

    public synchronized void parse() throws IOException {
        if (mParsedAlready)
            return;

        File file = (File) getSource();
        if (Utils.isCompressedTarball(file.getName())) {
            InputStream is = toUncompressedStream(file);
            File folder = Utils.initCacheDirectory("tar");
            File tarFile = new File(folder, "plain.tar");
            Utils.copyToFile(is, tarFile);
            mUncompressedFile = tarFile;
            file = tarFile;
        }

        mTarFile = new TarFile(file);
        ArrayList entries = new ArrayList<>();
        for (TarArchiveEntry entry : mTarFile.getEntries()) {
            if (entry.isDirectory()) {
                continue;
            }
            // TODO: cache to file system instead
            if (Utils.isImage(entry.getName())) {
                entries.add(entry);
            }
        }

        Collections.sort(entries, new IgnoreCaseComparator() {
            @Override
            public String stringValue(Object o) {
                return ((TarArchiveEntry) o).getName();
            }
        });

        mEntries = entries;
        mParsedAlready = true;
    }

    @Override
    public int numPages() throws IOException {
        parse();
        return mEntries != null ? mEntries.size() : 0;
    }

    @Override
    public InputStream getPage(int num) throws IOException {
        parse();
        TarArchiveEntry needle = mEntries.get(num);
        return mTarFile.getInputStream(needle);
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
        return "tar";
    }

    @Override
    public void destroy() {
        Utils.close(mTarFile);
        mTarFile = null;
        mEntries.clear();
        mEntries = null;
        if (mUncompressedFile != null) {
            Utils.rmDir(mUncompressedFile.getParentFile());
            mUncompressedFile = null;
        }
        mParsedAlready = false;
    }

    static private InputStream toUncompressedStream(File file) throws IOException {
        String fileName = file.getName();
        InputStream is = null;

        is = new BufferedInputStream(new FileInputStream(file));
        try {
            if (Utils.isTGZ(fileName))
                is = new CompressorStreamFactory().createCompressorInputStream(CompressorStreamFactory.GZIP, is);
            else if (Utils.isTBZ(fileName))
                is = new CompressorStreamFactory().createCompressorInputStream(CompressorStreamFactory.BZIP2, is);
            else if (Utils.isTXZ(fileName))
                is = new CompressorStreamFactory().createCompressorInputStream(CompressorStreamFactory.LZMA, is);
            else if (Utils.isTZST(fileName))
                is = new CompressorStreamFactory().createCompressorInputStream(CompressorStreamFactory.ZSTANDARD, is);
            else if (Utils.isTBR(fileName))
                is = new CompressorStreamFactory().createCompressorInputStream(CompressorStreamFactory.BROTLI, is);
            // no name, let's try to detect
            else
                is = new CompressorStreamFactory().createCompressorInputStream(is);
        } catch (CompressorException e) {
            throw new IOException(e);
        }

        return is;
    }

}
