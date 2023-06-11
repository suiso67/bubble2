package com.nkanaev.comics.parsers;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.DisplayMetrics;
import android.util.Log;
import com.gemalto.jp2.JP2Decoder;
import com.github.junrar.exception.UnsupportedRarV5Exception;
import com.nkanaev.comics.MainApplication;
import com.nkanaev.comics.managers.Utils;

import java.io.*;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;

public class ParserFactory {

    public static Parser create(Object o) throws Exception {
        try {
            Parser p;
            if (o instanceof String)
                p = create(new File((String) o));
            else if (o instanceof File)
                p = create((File) o);
            else if (o instanceof Intent)
                p = create((Intent) o);
            else
                throw new IllegalArgumentException("Parser.create() call with unimplemented parameter");

            if (p != null) {
                // wrap 7z,rar,zip in retry parser, unless pre-Oreo
                if (p instanceof AbstractParser && Utils.isOreoOrLater() &&
                        Arrays.asList(new String[]{"7z", "rar", "zip"}).contains(p.getType()))
                    p = new LenientTryAnotherParserWrapper((AbstractParser) p);
                // wrap in MetaData wrapper
                p = new CachingPageMetaDataParserWrapper(p);
                // wrap in JP2 recoder
                p = new CachingDecodeJP2ParserWrapper(p);

            }
            return p;
        } catch (Exception e) {
            Log.e("ParserFactory", "create", e);
        }
        return null;
    }

    private static Parser create(File file) throws Exception {
        // let's skip undesireables, keep the noise down
        if (!file.isDirectory() && !(file.isFile() && Utils.isArchive(file.getName())))
            return null;

        Class parserClass = findParser(file);
        Method canParseMethod = parserClass.getMethod("canParse", Class.class);
        AbstractParser p = (AbstractParser) parserClass.getDeclaredConstructor().newInstance();
        if ((boolean) canParseMethod.invoke(p, File.class)) {
            p.setSource(file);
            return p;
        }
        throw new UnsupportedOperationException("Parser " + parserClass.getCanonicalName() + " does not support opening File!");
    }

    private static Parser create(Intent intent) throws Exception {
        Uri uri = AbstractParser.uriFromIntent(intent);
        if (uri == null)
            throw new IllegalArgumentException("Intent without url!");

        // handle file:// uris directly
        if ("file".equalsIgnoreCase(uri.getScheme())) {
            return create(new File(uri.getPath()));
        }

        Class parserClass = findParser(intent);

        AbstractParser p = (AbstractParser) parserClass.getDeclaredConstructor().newInstance();
        if (p.canParse(File.class)) {
            AbstractParser intentParser = new IntentWithTempFileParserWrapper(p);
            intentParser.setSource(intent);
            return intentParser;
        }

        throw new UnsupportedOperationException("Parser " + parserClass.getCanonicalName() + " does not support opening File!");
    }

    private static Class<? extends AbstractParser> findParser(Intent intent) {
        Uri uri = AbstractParser.uriFromIntent(intent);
        File file = new File(uri.getLastPathSegment());
        try {
            return findParser(file);
        } catch (UnsupportedOperationException e) {
            // lets retry below
        }
        // retry mimetype if needed
        String dummyName;
        if (intent.getType()!=null && (dummyName=Utils.dummyFileNameFromMimeType(intent.getType()))!=null)
            try {
                return findParser(new File(dummyName));
            } catch (UnsupportedOperationException e){
                // throw error below
            }

        throw new UnsupportedOperationException("No parser found for file " + file +" mimeType " + intent.getType());
    }

    private static Class<? extends AbstractParser> findParser(File file) {
        if (file.isDirectory()) {
            return DirectoryParser.class;
        } else if (Utils.isZip(file.getName())) {
            if (Utils.isOreoOrLater())
                return CommonsZipParser.class;
            // pretty much the only parser for pre-Oreo devices now
            return ZipParser.class;
        } else if (Utils.isRar(file.getName())) {
            if (!Utils.isOreoOrLater()) {
                throw new UnsupportedOperationException("Rar only available on Oreo (API26) or later");
            }
            return RarParser.class;
        } else if (Utils.isTarball(file.getName())) {
            if (!Utils.isOreoOrLater()) {
                throw new UnsupportedOperationException("Tar only available on Oreo (API26) or later");
            }
            return TarFileParser.class;
        } else if (Utils.isSevenZ(file.getName())) {
            if (!Utils.isOreoOrLater()) {
                throw new UnsupportedOperationException("7zip only available on Oreo (API26) or later");
            }
            // TODO: random access SevenZFileParser throws CRC errors
            return SevenZStreamParser.class;
        } else if (Utils.isPdf(file.getName())) {
            if (!Utils.isLollipopOrLater()) {
                throw new UnsupportedOperationException("Pdf only available on Lollipop (API21) or later");
            }
            return PdfRendererParser.class;
        }

        // no parser, no fun ;(
        throw new UnsupportedOperationException("No parser found for file " + file);
    }

    private static class IntentWithTempFileParserWrapper extends AbstractParser {
        private AbstractParser mParser;
        private File mTempDir = null;

        public IntentWithTempFileParserWrapper(AbstractParser parser) throws Exception {
            super(new Class[]{Intent.class});
            mParser = parser;
        }

        @Override
        public synchronized void parse() throws IOException {
            createTempFile();
            mParser.parse();
        }

        @Override
        public int numPages() throws IOException {
            parse();
            return mParser.numPages();
        }

        @Override
        public InputStream getPage(int num) throws IOException {
            parse();
            return mParser.getPage(num);
        }

        @Override
        public Map getPageMetaData(int num) throws IOException {
            parse();
            return mParser.getPageMetaData(num);
        }

        @Override
        public String getType() {
            return mParser.getType();
        }

        @Override
        protected Object getSource() {
            Object tempSource = mParser.getSource();
            if (tempSource != null)
                return tempSource;
            return super.getSource();
        }

        private void createTempFile() throws IOException {
            if (mParser.getSource() instanceof File)
                return;

            mTempDir = Utils.initCacheDirectory("tempfile");
            Intent intent = (Intent) getSource();
            Uri uri = uriFromIntent(intent);
            // treat last path segment as possible uri (e.g. google files app)
            String filename = Uri.decode(uri.getLastPathSegment());
            if (filename != null) {
                Uri uri2 = Uri.parse(filename);
                if (uri2 != null && uri2.getPath() != null)
                    filename = uri2.getLastPathSegment();
            } else {
                // last resort, should never happen actually
                // may probably fail because of missing file extension
                filename = "tempfile";
            }
            File tempFile = new File(mTempDir, Uri.encode(filename));
            InputStream is = MainApplication.getAppContext().getContentResolver().openInputStream(uri);
            Utils.copyToFile(is, tempFile);
            mParser.setSource(tempFile);
        }

        @Override
        public void destroy() {
            // destroy wrapped parser
            Utils.close(mParser);
            mParser = null;
            Utils.rmDir(mTempDir);
        }
    }

    private static class LenientTryAnotherParserWrapper implements Parser {
        private AbstractParser mParser;
        private boolean mRetriedAlready = false;

        public LenientTryAnotherParserWrapper(AbstractParser parser) {
            mParser = parser;
        }

        private boolean isIgnored(Throwable t) {
            while (t != null) {
                if (t instanceof UnsupportedRarV5Exception)
                    return true;
                t = t.getCause();
            }
            return false;
        }

        @Override
        public synchronized void parse() throws IOException {
            try {
                mParser.parse();
            } catch (Exception e) {
                if (mRetriedAlready || isIgnored(e))
                    rethrow(e);

                mRetriedAlready = true;
                // try zip, if it wasn't before
                if (!mParser.getClass().isAssignableFrom(CommonsZipParser.class))
                    try {
                        CommonsZipParser candidate = new CommonsZipParser();
                        candidate.setSource(mParser.getSource());
                        candidate.parse();
                        mParser = candidate;
                        return;
                    } catch (Exception e2) {
                        // nice try
                    }
                // try zip, if it wasn't before
                if (!mParser.getClass().isAssignableFrom(RarParser.class))
                    try {
                        RarParser candidate = new RarParser();
                        candidate.setSource(mParser.getSource());
                        candidate.parse();
                        mParser = candidate;
                        return;
                    } catch (Exception e2) {
                        // nice try
                    }
                // try zip, if it wasn't before
                if (!mParser.getClass().isAssignableFrom(SevenZStreamParser.class))
                    try {
                        SevenZStreamParser candidate = new SevenZStreamParser();
                        candidate.setSource(mParser.getSource());
                        candidate.parse();
                        mParser = candidate;
                        return;
                    } catch (Exception e2) {
                        // nice try
                    }

                rethrow(e);
            }
        }

        @Override
        public synchronized int numPages() throws IOException {
            try {
                return mParser.numPages();
            } catch (Exception e) {
                if (mRetriedAlready || isIgnored(e))
                    rethrow(e);

                mRetriedAlready = true;
                // try zip, if it wasn't before
                if (!mParser.getClass().isAssignableFrom(CommonsZipParser.class))
                    try {
                        CommonsZipParser candidate = new CommonsZipParser();
                        candidate.setSource(mParser.getSource());
                        int count = candidate.numPages();
                        mParser = candidate;
                        return count;
                    } catch (Exception e2) {
                        // nice try
                    }
                // try zip, if it wasn't before
                if (!mParser.getClass().isAssignableFrom(RarParser.class))
                    try {
                        RarParser candidate = new RarParser();
                        candidate.setSource(mParser.getSource());
                        int count = candidate.numPages();
                        mParser = candidate;
                        return count;
                    } catch (Exception e2) {
                        // nice try
                    }
                // try zip, if it wasn't before
                if (!mParser.getClass().isAssignableFrom(SevenZStreamParser.class))
                    try {
                        SevenZStreamParser candidate = new SevenZStreamParser();
                        candidate.setSource(mParser.getSource());
                        int count = candidate.numPages();
                        mParser = candidate;
                        return count;
                    } catch (Exception e2) {
                        // nice try
                    }

                Log.e("LenientParser", "failed", new IOException("Parser failed. Retries too.", e));
                rethrow(e);
            }
            return 0;
        }

        private void rethrow(Exception e) throws IOException {
            if (!(e instanceof IOException))
                throw new IOException("Parser failed. Retries too.", e);
            else
                throw (IOException) e;
        }

        @Override
        public synchronized InputStream getPage(int num) throws IOException {
            try {
                return mParser.getPage(num);
            } catch (Exception e) {
                if (mRetriedAlready || isIgnored(e))
                    rethrow(e);

                mRetriedAlready = true;
                // try zip, if it wasn't before
                if (!mParser.getClass().isAssignableFrom(CommonsZipParser.class))
                    try {
                        CommonsZipParser candidate = new CommonsZipParser();
                        candidate.setSource(mParser.getSource());
                        InputStream is = candidate.getPage(num);
                        mParser = candidate;
                        return is;
                    } catch (Exception e2) {
                        // nice try
                    }
                // try zip, if it wasn't before
                if (!mParser.getClass().isAssignableFrom(RarParser.class))
                    try {
                        RarParser candidate = new RarParser();
                        candidate.setSource(mParser.getSource());
                        InputStream is = candidate.getPage(num);
                        mParser = candidate;
                        return is;
                    } catch (Exception e2) {
                        // nice try
                    }
                // try zip, if it wasn't before
                if (!mParser.getClass().isAssignableFrom(SevenZStreamParser.class))
                    try {
                        SevenZStreamParser candidate = new SevenZStreamParser();
                        candidate.setSource(mParser.getSource());
                        InputStream is = candidate.getPage(num);
                        mParser = candidate;
                        return is;
                    } catch (Exception e2) {
                        // nice try
                    }

                rethrow(e);
                return null;
            }
        }

        @Override
        public Map getPageMetaData(int num) throws IOException {
            return mParser.getPageMetaData(num);
        }

        @Override
        public String getType() {
            return mParser.getType();
        }

        @Override
        public void destroy() {
            // destroy wrapped parser
            Utils.close(mParser);
        }
    }

    private static class CachingPageMetaDataParserWrapper implements Parser {
        private Parser mParser;
        private boolean mFetchMeta = false;
        private HashMap<Integer, Map> mPagesMetaData = new HashMap();
        private ArrayList mPagesSeen = new ArrayList<Integer>();

        public CachingPageMetaDataParserWrapper(Parser parser) {
            mParser = parser;
        }

        private Integer key(int num){
            return Integer.valueOf(num);
        }

        @Override
        public void parse() throws IOException {
            mParser.parse();
        }

        @Override
        public int numPages() throws IOException {
            return mParser.numPages();
        }

        @Override
        public InputStream getPage(int num) throws IOException {
            InputStream is = null;
            if (mFetchMeta && !mPagesMetaData.containsKey(key(num)))
                is = initPageMetaData(num);
            else if (!mFetchMeta)
                mPagesSeen.add(key(num));

            return is != null ? is : mParser.getPage(num);
        }

        private synchronized InputStream initPageMetaData(int num) throws IOException {
            Integer key = key(num);
            Map pageData = new HashMap();
            InputStream is = null;
            try {
                is = mParser.getPage(num);
                final BitmapFactory.Options options = new BitmapFactory.Options();
                // read bitmap metadata w/o creating another in memory copy
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeStream(is, null, options);

                if (options.outMimeType != null) {
                    pageData.put(Parser.PAGEMETADATA_KEY_MIME, options.outMimeType);
                    pageData.put(Parser.PAGEMETADATA_KEY_WIDTH, options.outWidth);
                    pageData.put(Parser.PAGEMETADATA_KEY_HEIGHT, options.outHeight);
                }
            }
            catch (Exception e) {
                // ignore, just log
                Log.e("bubble2","failed to decode/fetch metadata",e);
            }
            finally {
                // keep memory buffer inputstreams
                if (is instanceof ByteArrayInputStream) {
                    is.reset();
                }
                // discard others, not (re)seekable properly
                else {
                    Utils.close(is);
                    is = null;
                }
                mPagesMetaData.put(key, pageData);
                return is;
            }
        }

        @Override
        public Map getPageMetaData(int num) throws IOException {
            mFetchMeta = true;

            Map<String, String> in = mParser.getPageMetaData(num);
            if (in == null || in.isEmpty())
                in = new HashMap<>();
            Map<String, String> in2 = mPagesMetaData.get(key(num));
            // init if still missing (just enabled?), for already requested pages only
            if (in2 == null && mPagesSeen.contains(key(num))) {
                InputStream is = initPageMetaData(num);
                Utils.close(is);
                in2 = mPagesMetaData.get(key(num));
            }
            // nothing to merge, leave early
            if (in2 == null || in2.isEmpty())
                return in;

            // merge same key values with slash
            for (String key : in2.keySet()) {
                String value2 = String.valueOf(in2.get(key));
                if (in.containsKey(key))
                    in.put(key, String.valueOf(in.get(key)) + "/" + value2);
                else
                    in.put(key, value2);
            }
            return in;
        }

        @Override
        public String getType() {
            return mParser.getType();
        }

        @Override
        public void destroy() {
            mPagesMetaData.clear();
            // destroy wrapped parser
            Utils.close(mParser);
        }
    }

    private static class CachingDecodeJP2ParserWrapper implements Parser {
        private Parser mParser;
        private HashMap<Integer, Map> mPagesMetaData = new HashMap();
        private boolean mCachingEnabled = false;
        private CacheWriter mCacheWriter = null;

        public CachingDecodeJP2ParserWrapper(Parser parser) {
            mParser = parser;
        }

        @Override
        public void parse() throws IOException {
            mParser.parse();
        }

        @Override
        public int numPages() throws IOException {
            return mParser.numPages();
        }

        @Override
        public InputStream getPage(int num) throws IOException {
            return recodeAndCache(num);
        }

        @Override
        public Map getPageMetaData(int num) throws IOException {
            Map<String, String> in = mParser.getPageMetaData(num);
            if (in == null || in.isEmpty())
                in = new HashMap<>();
            Map<String, String> in2 = mPagesMetaData.get(Integer.valueOf(num));
            // nothing to merge, leave early
            if (in2 == null)
                return in;

            for (String key : in2.keySet()) {
                String value2 = String.valueOf(in2.get(key));
                if (in.containsKey(key))
                    in.put(key, String.valueOf(in.get(key)) + "/" + value2);
                else
                    in.put(key, value2);
            }
            return in;
        }

        private InputStream recodeAndCache(int num) throws IOException {
            // don't cache cover only (num=0) requests
            if (!mCachingEnabled && num != 0)
                mCachingEnabled = true;

            RunnerStatus status = mRunnerStatus.get();
            Log.d(getClass().getCanonicalName(), "CacheRunner:" + status + " Num:" + num);

            // consult cache, return if avail
            InputStream cachedStream = cachedPageStream(num);
            if (cachedStream != null) return cachedStream;

            InputStream stream = mParser.getPage(num);

            // any non JP2 stream will be ignored
            BufferedInputStream bis = new BufferedInputStream(stream);
            if (!Utils.isJP2Stream(bis) || !Utils.isKitKatOrLater())
                return bis;

            // initialize cache only once, serve uncached until filled
            if (mCachingEnabled &&
                    status != RunnerStatus.FINISHED &&
                    status != RunnerStatus.RUNNING) {
                mCacheWriter = new CacheWriter(num);
                new Thread(mCacheWriter).start();
            } else if (status == RunnerStatus.RUNNING && mCacheWriter != null) {
                mCacheWriter.reset(num);
            }

            InputStream result = decodeJP2(bis, num, true);

            // decode to memory
            return result;
        }

        private static synchronized Bitmap _decodeJP2(InputStream is){
            try {
                return new JP2Decoder(is).decode();
            }finally {
                Utils.close(is);
            }
        }

        // synchronized to allow only one decoding at all times
        // prevents app restarts because of memory outage
        private synchronized InputStream decodeJP2(InputStream is, int num, boolean returnStream) {
            InputStream cacheStream = cachedPageStream(num);
            if (cacheStream != null) return cacheStream;

            Bitmap source = null, bitmap = null, scaled = null;
            ByteArrayOutputStream bos = null;
            InputStream result = null;
            try {
                source = _decodeJP2(is);
                if (source == null) return null;

                Map pageData = new HashMap();
                pageData.put(Parser.PAGEMETADATA_KEY_MIME, "image/x-jp2");
                pageData.put(Parser.PAGEMETADATA_KEY_WIDTH, source.getWidth());
                pageData.put(Parser.PAGEMETADATA_KEY_HEIGHT, source.getHeight());
                mPagesMetaData.put(Integer.valueOf(num), pageData);

                // mDblTapScale in PageImageView is 1.5 currently, so set this as our limit
                DisplayMetrics displayMetrics = MainApplication.getAppContext().getResources().getDisplayMetrics();
                int max = Math.round(1.0f * Math.max(displayMetrics.widthPixels, displayMetrics.heightPixels));
                BitmapFactory.Options options = new BitmapFactory.Options();
                //options.inPreferredConfig = Bitmap.Config.RGB_565;
                float scale = max / (float) Math.max(source.getHeight(), source.getWidth());
                if (scale < 1) {
                    scaled = Bitmap.createScaledBitmap(source, Math.round(scale * source.getWidth()), Math.round(scale * source.getHeight()), true);
                    source.recycle();
                    bitmap = scaled;
                    //bitmap.reconfigure(Math.round(scale * bitmap.getWidth()), Math.round(scale * bitmap.getHeight()), Bitmap.Config.RGB_565);
                } else {
                    bitmap = source;
                }

                // "always" cache the result to file system
                if (mCachingEnabled)
                    cachePage(bitmap, num);
                // try again, skips the need to png-ize memory buffer
                if ((cacheStream = cachedPageStream(num)) != null)
                    return cacheStream;

                // just in case all the above failed we will return the bitmap from memory
                if (returnStream) {
                    bos = new ByteArrayOutputStream();
                    bitmap.compress(Bitmap.CompressFormat.PNG, 0 /*png is lossless*/, bos);
                    byte[] byteArray = bos.toByteArray();
                    result = new ByteArrayInputStream(byteArray);
                }
            } finally {
                Utils.close(is);
                Utils.close(bos);
                Utils.close(source);
                Utils.close(bitmap);
                Utils.close(scaled);
            }

            return result;
        }

        private synchronized void cachePage(Bitmap bitmap, int num) {
            // init late
            if (mCachedIndex == null)
                mCachedIndex = new ConcurrentLinkedQueue<Integer>();
            // skip existing
            if (bitmap == null || mCachedIndex.contains(num))
                return;

            String fileName = nameCacheFile(num);
            Log.d(getClass().getCanonicalName(), "Caching -> " + fileName);

            FileOutputStream fos = null;
            try {
                if (mCacheDir == null)
                    mCacheDir = Utils.initCacheDirectory("jp2");
                if (mCacheDir == null || !mCacheDir.exists())
                    throw new IOException("CacheDir does not exist.");

                File file = new File(mCacheDir, fileName);
                fos = new FileOutputStream(file);
                // png is lossless, but pretty big, jpeg 95 is ruffly 1/4 the size
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, fos);

                // reached here? memorize success
                mCachedIndex.add(num);
            } catch (Exception e) {
                Log.e(getClass().getCanonicalName(), "cachePage()", e);
            } finally {
                Utils.close(fos);
            }
        }

        private String nameCacheFile(int num) {
            return String.valueOf(num) + ".jpg";
        }

        private File cachedPageFile(int num) {
            if (mCacheDir == null ||
                    mCachedIndex == null ||
                    !mCachedIndex.contains(num)) return null;

            File file = new File(mCacheDir, nameCacheFile(num));
            if (file.canRead())
                return file;

            return null;
        }

        private InputStream cachedPageStream(int num) {
            File cacheFile = cachedPageFile(num);
            if (cacheFile != null) {
                try {
                    return new FileInputStream(cacheFile);
                } catch (Exception e) {
                    Log.e(getClass().getCanonicalName(),
                            "Supposedly existing cache file missing.", e);
                }
            }
            return null;
        }

        public static enum RunnerStatus {
            STOP, RUNNING, FINISHED
        }

        private AtomicReference<RunnerStatus> mRunnerStatus = new AtomicReference(RunnerStatus.STOP);

        private ConcurrentLinkedQueue<Integer> mCachedIndex = null;
        private File mCacheDir = null;

        private class CacheWriter implements Runnable {
            private int mOffset, newOffset;

            public CacheWriter(int start) {
                newOffset = start;
                mOffset = start;
            }

            public void reset(int other) {
                if (Math.abs(other - mOffset) <= 4)
                    return;
                // relocate loop
                newOffset = other;
            }

            @Override
            public void run() {
                mRunnerStatus.set(RunnerStatus.RUNNING);
                try {
                    int max = mParser.numPages();
                    for (int i = 0; i < max && mRunnerStatus.get() == RunnerStatus.RUNNING; i++) {
                        // offset changed, reset loop
                        if (newOffset != mOffset) {
                            i = 0;
                            mOffset = newOffset;
                        }
                        int num = mOffset + i;

                        if (num >= max)
                            num = num - max;

                        Log.d(getClass().getCanonicalName(), "Caching Loop -> " + num);

                        InputStream stream = mParser.getPage(num);
                        BufferedInputStream bis = new BufferedInputStream(stream);
                        // don't cache nonJP2 files
                        if (!Utils.isJP2Stream(bis))
                            continue;

                        decodeJP2(bis, num, false);
                    }
                    mRunnerStatus.compareAndSet(RunnerStatus.RUNNING, RunnerStatus.FINISHED);
                } catch (Exception e) {
                    Log.e(getClass().getCanonicalName(), "JP2CacheRunner", e);
                } finally {
                    mRunnerStatus.compareAndSet(RunnerStatus.RUNNING, RunnerStatus.STOP);
                }
            }
        }

        @Override
        public String getType() {
            return mParser.getType();
        }

        @Override
        public void destroy() {
            mRunnerStatus.set(RunnerStatus.STOP);
            Log.i("Runner", "state reset.");

            // delete in background
            final File folder = mCacheDir;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    Utils.rmDir(folder);
                }
            }).start();
            mCacheDir = null;
            mCachedIndex = null;

            // destroy wrapped parser
            Utils.close(mParser);
        }
    }
}
