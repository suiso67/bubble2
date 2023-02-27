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
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;

public class ParserFactory {
    //    private static Context context = null;
    private static HashMap<String, Parser> mParserCache = new HashMap();

    /*   public static void setContext(Context ctx) {
           context = ctx;
       }
   */
    public static Parser create(Object o) throws Exception {
        try {
            String key = o.toString();
            // use cache
            if (mParserCache.containsKey(key)) {
                Parser p = mParserCache.get(key);
                // ignore/recreate zero page parsers, probably erroneous
                if (p.numPages() > 0)
                    return p;
            }

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
                mParserCache.put(key, p);
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

        Class parserClass = findParser(new File(uri.getLastPathSegment()));
        AbstractParser p = (AbstractParser) parserClass.getDeclaredConstructor().newInstance();
        if (p.canParse(File.class)) {
            AbstractParser intentParser = new IntentWithTempFileParserWrapper(p);
            intentParser.setSource(intent);
            return intentParser;
        }

        throw new UnsupportedOperationException("Parser " + parserClass.getCanonicalName() + " does not support opening File!");
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
        private AbstractParser mInstance;
        private File mTempDir = null;

        public IntentWithTempFileParserWrapper(AbstractParser parser) throws Exception {
            super(new Class[]{Intent.class});
            mInstance = parser;
        }

        @Override
        public synchronized void parse() throws IOException {
            createTempFile();
            mInstance.parse();
        }

        @Override
        public int numPages() throws IOException {
            parse();
            return mInstance.numPages();
        }

        @Override
        public InputStream getPage(int num) throws IOException {
            parse();
            return mInstance.getPage(num);
        }

        @Override
        public Map getPageMetaData(int num) throws IOException {
            parse();
            return mInstance.getPageMetaData(num);
        }

        @Override
        public String getType() {
            return mInstance.getType();
        }

        @Override
        protected Object getSource() {
            Object tempSource = mInstance.getSource();
            if (tempSource != null)
                return tempSource;
            return super.getSource();
        }

        private void createTempFile() throws IOException {
            if (mInstance.getSource() instanceof File)
                return;

            mTempDir = Utils.initCacheDirectory("tempfile");
            Intent intent = (Intent) getSource();
            Uri uri = uriFromIntent(intent);
            // treat last path segment as possible uri (e.g. google files app)
            String filename = Uri.decode(uri.getLastPathSegment());
            if (filename!=null) {
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
            mInstance.setSource(tempFile);
        }

        @Override
        public void destroy() {
            mInstance.destroy();
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
            mParser.destroy();
        }
    }

    private static class CachingPageMetaDataParserWrapper implements Parser {
        private Parser mParser;
        private HashMap<Integer, Map> mPagesMetaData = new HashMap();

        public CachingPageMetaDataParserWrapper(Parser parser) {
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

        // synchronized so we do not access te same file channel concurrently
        @Override
        public synchronized InputStream getPage(int num) throws IOException {
            InputStream is = mParser.getPage(num);
            Integer key = Integer.valueOf(num);
            ByteArrayInputStream bais = null;
            // bail out if copying to bytearray fails (lack of memory?)
            try {
                bais = new ByteArrayInputStream(Utils.toByteArray(is));
            } catch (Throwable t) {
                Log.e("", "", t);
                return mParser.getPage(num);
            }
            try {
                final BitmapFactory.Options options = new BitmapFactory.Options();
                // read bitmap metadata w/o creating another in memory copy
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeStream(bais, null, options);
                Map pageData = new HashMap();
                if (options.outMimeType != null) {
                    pageData.put(Parser.PAGEMETADATA_KEY_MIME, options.outMimeType);
                    pageData.put(Parser.PAGEMETADATA_KEY_WIDTH, options.outWidth);
                    pageData.put(Parser.PAGEMETADATA_KEY_HEIGHT, options.outHeight);
                    mPagesMetaData.put(key, pageData);
                }
            } finally {
                bais.reset();
                return bais;
            }
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

        @Override
        public String getType() {
            return mParser.getType();
        }

        @Override
        public void destroy() {
            mParser.destroy();
        }
    }

    private static class CachingDecodeJP2ParserWrapper implements Parser {
        private Parser mParser;
        private HashMap<Integer, Map> mPagesMetaData = new HashMap();

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
            // consult cache if avail
            RunnerStatus status = mRunnerStatus.get();
            Log.i("Runner", "" + status);
            if (mCachedIndex != null && mCachedIndex.contains(num)) {
                File cacheFile = new File(mCacheDir, String.valueOf(num));
                if (cacheFile.canRead())
                    return new FileInputStream(cacheFile);
            }

            InputStream stream = mParser.getPage(num);

            // any non JP2 stream will be ignored
            BufferedInputStream bis = new BufferedInputStream(stream);
            if (!Utils.isJP2Stream(bis) || !Utils.isKitKatOrLater())
                return bis;

            // initialize cache only once, serve uncached until filled
            if (num != 0 &&
                    status != RunnerStatus.FINISHED &&
                    status != RunnerStatus.RUNNING &&
                    mCacheDir == null)
                new Thread(new CacheWriter(num)).start();

            // decode to memory
            Bitmap bitmap = decodeJP2(bis, num);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 0 /*png is lossless*/, bos);
            byte[] byteArray = bos.toByteArray();
            Utils.close(bos);
            bitmap.recycle();

            return new ByteArrayInputStream(byteArray);
        }

        private Bitmap decodeJP2(InputStream is, int num) {
            Bitmap bitmap = new JP2Decoder(is).decode();
            Utils.close(is);

            Map pageData = new HashMap();
            pageData.put(Parser.PAGEMETADATA_KEY_MIME, "image/x-jp2");
            pageData.put(Parser.PAGEMETADATA_KEY_WIDTH, bitmap.getWidth());
            pageData.put(Parser.PAGEMETADATA_KEY_HEIGHT, bitmap.getHeight());
            mPagesMetaData.put(Integer.valueOf(num), pageData);


            // mDblTapScale in PageImageView is 1.5 currently, so set this as our limit
            DisplayMetrics displayMetrics = MainApplication.getAppContext().getResources().getDisplayMetrics();
            int max = Math.round(1.0f * Math.max(displayMetrics.widthPixels, displayMetrics.heightPixels));
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            float scale = max / (float) Math.max(bitmap.getHeight(), bitmap.getWidth());
            if (scale < 1) {
                Bitmap scaled = Bitmap.createScaledBitmap(bitmap, Math.round(scale * bitmap.getWidth()), Math.round(scale * bitmap.getHeight()), true);
                bitmap.recycle();
                return scaled;
                //bitmap.reconfigure(Math.round(scale * bitmap.getWidth()), Math.round(scale * bitmap.getHeight()), Bitmap.Config.RGB_565);
            }
            return bitmap;
        }

        public static enum RunnerStatus {
            STOP, RUNNING, FINISHED
        }

        private AtomicReference<RunnerStatus> mRunnerStatus = new AtomicReference(RunnerStatus.STOP);

        private ConcurrentLinkedQueue<Integer> mCachedIndex = null;
        private File mCacheDir = null;

        private class CacheWriter implements Runnable {
            private int mOffset = 0;

            public CacheWriter(int start) {
                mOffset = start;
            }

            @Override
            public void run() {
                mRunnerStatus.set(RunnerStatus.RUNNING);
                try {
                    if (mCacheDir == null)
                        mCacheDir = Utils.initCacheDirectory("jp2");
                    if (mCacheDir == null || !mCacheDir.exists())
                        throw new IOException("CacheDir does not exist.");

                    int max = mParser.numPages();
                    for (int i = 0; i < max && mRunnerStatus.get() == RunnerStatus.RUNNING; i++) {
                        int num = mOffset + i;
                        if (num >= max)
                            num = num - max;

                        Log.i("Caching", "" + num);

                        InputStream stream = mParser.getPage(num);
                        BufferedInputStream bis = new BufferedInputStream(stream);
                        // don't cache nonJP2 files
                        if (!Utils.isJP2Stream(bis))
                            continue;

                        Bitmap bitmap = decodeJP2(bis, num);
                        File file = new File(mCacheDir, String.valueOf(num));
                        FileOutputStream fos = new FileOutputStream(file);
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 100 /*png is lossless*/, fos);
                        Utils.close(fos);
                        bitmap.recycle();

                        // memorize
                        if (mCachedIndex == null)
                            mCachedIndex = new ConcurrentLinkedQueue<Integer>();
                        mCachedIndex.add(num);
                    }
                } catch (Exception e) {
                    Log.e("ParserFactory#433", "JP2CacheRunner", e);
                } finally {
                    mRunnerStatus.compareAndSet(RunnerStatus.RUNNING, RunnerStatus.FINISHED);
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
            mParser.destroy();
        }
    }
}
