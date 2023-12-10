package com.nkanaev.comics.parsers;

import java.io.*;
import java.util.*;

import android.util.Log;
import com.nkanaev.comics.managers.IgnoreCaseComparator;
import com.nkanaev.comics.managers.Utils;
import net.sf.sevenzipjbinding.*;
import net.sf.sevenzipjbinding.impl.*;

public class LibSevenZParser extends AbstractParser {
    private static String TAG = "LibSevenZParser";
    private List<ArchiveEntry> mEntries = null;
    private String mArchiveFormat = null;

    public LibSevenZParser() {
        super(new Class[]{File.class});
    }

    @Override
    public void parse() throws IOException {
        if (mEntries != null)
            return;

        File file = (File) getSource();
        RandomAccessFile randomAccessFile = new RandomAccessFile(file, "r");
        RandomAccessFileInStream stream = new RandomAccessFileInStream(randomAccessFile);
        IInArchive archive = SevenZip.openInArchive(null, stream);

        ArchiveFormat format = archive.getArchiveFormat();
        mArchiveFormat = format.getMethodName();

        int itemCount = archive.getNumberOfItems();
        Log.d(TAG, "Items in archive: " + itemCount);
        List entries = new ArrayList();
        for (int i = 0; i < itemCount; i++) {
            String path = archive.getStringProperty(i, PropID.PATH);
            boolean isFolder = (boolean) archive.getProperty(i, PropID.IS_FOLDER);
            Log.d(TAG, "File " + i + ": " + path + " : " + isFolder);
            if (isFolder || !Utils.isImage(path))
                continue;

            // populate comic page entry
            ArchiveEntry entry = new ArchiveEntry();
            entry.path = path;
            entry.index = i;
            entries.add(entry);
        }

        Collections.sort(entries, new IgnoreCaseComparator() {
            @Override
            public String stringValue(Object o) {
                return ((ArchiveEntry) o).path;
            }
        });

        mEntries = entries;

        // cleanup
        Utils.close(archive);
        Utils.close(stream);
    }

    @Override
    public InputStream getPage(int num) throws IOException {
        parse();
        int i = mEntries.get(num).index;

        ExtractOperationResult result = null;
        ByteArrayOutputToInputStream bos = new ByteArrayOutputToInputStream();
        ISequentialOutStream sos = new ISequentialOutStream(){
            @Override
            public int write(byte[] data) throws SevenZipException {
                try {
                    bos.write(data);
                } catch (IOException e) {
                    throw new SevenZipException(e);
                }
                return data.length;
            }
        };
        RandomAccessFileInStream stream = null;
        IInArchive archive = null;
        try {
            File file = (File) getSource();
            RandomAccessFile randomAccessFile = new RandomAccessFile(file, "r");
            stream = new RandomAccessFileInStream(randomAccessFile);
            archive = SevenZip.openInArchive(null, stream);
            result = archive.extractSlow(i, sos);
        } catch (SevenZipException e) {
            Log.e(TAG, "extraction error", e);
        } finally {
            Utils.close(stream);
            Utils.close(archive);
        }
        if (result != ExtractOperationResult.OK) {
            Log.e(TAG, result.toString());
        }

        return bos.getInputStream();
    }

    @Override
    public int numPages() throws IOException {
        parse();
        return mEntries != null ? mEntries.size() : 0;
    }

    @Override
    public String getType() {
        return "lib7z"+(mArchiveFormat!=null?"+"+mArchiveFormat:"");
    }

    @Override
    public Map getPageMetaData(int num) throws IOException {
        parse();
        Map m = new HashMap();
        m.put(Parser.PAGEMETADATA_KEY_NAME,mEntries.get(num).path);
        return m;
    }

    @Override
    public void destroy() {
        super.destroy();
        mEntries = null;
    }

    public static boolean isAvailable() {
        try {
            SevenZip.initSevenZipFromPlatformJAR();
        } catch (SevenZipNativeInitializationException e) {
            Log.e(TAG,"cannot init lib7z", e);
        }
        return SevenZip.isInitializedSuccessfully();
    }

    private class ArchiveEntry {
        public int index;
        public String path;
    }
/*
    private class SequentialOutStream extends ByteArrayOutputStream implements ISequentialOutStream {
        OutputStream outputStream;
        private SequentialOutStream(){}

        public SequentialOutStream(OutputStream os) {
            super();
            outputStream = os;
        }

        @Override
        public synchronized int write(byte[] data) throws SevenZipException {
            if (data == null || data.length == 0) {
                throw new SevenZipException("null data");
            }
            Log.i(TAG, "Data to write: " + data.length);
            try {
                outputStream.write(data);
            } catch (IOException e) {
                throw new SevenZipException(e);
            }
            return data.length;
        }
    }
    */

    private class ByteArrayOutputToInputStream extends ByteArrayOutputStream {
        public ByteArrayInputStream getInputStream() {
            // reuse protected byte buffer, save memory
            ByteArrayInputStream in = new ByteArrayInputStream(this.buf, 0, this.count);
            // free reference, prevent further modification
            this.buf = null;

            return in;
        }
    }
}
