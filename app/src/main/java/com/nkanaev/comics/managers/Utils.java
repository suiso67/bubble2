package com.nkanaev.comics.managers;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.ApplicationInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Insets;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowMetrics;
import androidx.annotation.AttrRes;
import androidx.annotation.ColorInt;
import androidx.annotation.StyleRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.core.content.ContextCompat;
import com.gemalto.jp2.JP2Decoder;
import com.nkanaev.comics.MainApplication;
import com.nkanaev.comics.R;
import com.nkanaev.comics.parsers.Parser;

import java.io.*;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.UUID;
import java.util.zip.ZipFile;

import static android.content.Context.ACTIVITY_SERVICE;


public final class Utils {
    public static int getScreenDpWidth(Context context) {
        DisplayMetrics displayMetrics = context.getResources().getDisplayMetrics();
        return Math.round(displayMetrics.widthPixels / displayMetrics.density);
    }

    public static boolean isIceCreamSandwitchOrLater() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH;
    }

    public static boolean isHoneycombOrLater() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB;
    }

    public static boolean isHoneycombMR1orLater() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR1;
    }

    public static boolean isJellyBeanMR1orLater() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1;
    }

    public static boolean isKitKatOrLater() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;
    }

    public static boolean isLollipopOrLater() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
    }

    // FileChannel
    public static boolean isNougatOrLater() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N;
    }

    // java.nio.path
    public static boolean isOreoOrLater() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O;
    }

    public static boolean isROrLater() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R;
    }

    public static int getHeapSize(Context context) {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        boolean isLargeHeap = (context.getApplicationInfo().flags & ApplicationInfo.FLAG_LARGE_HEAP) != 0;
        int memoryClass = am.getMemoryClass();
        if (isLargeHeap && Utils.isHoneycombOrLater()) {
            memoryClass = am.getLargeMemoryClass();
        }
        return 1024 * memoryClass;
    }

    public static int calculateBitmapSize(Bitmap bitmap) {
        int sizeInBytes;
        if (Utils.isHoneycombMR1orLater()) {
            sizeInBytes = bitmap.getByteCount();
        } else {
            sizeInBytes = bitmap.getRowBytes() * bitmap.getHeight();
        }
        return sizeInBytes / 1024;
    }

    public static boolean isImage(String filename) {
        return filename.matches("(?i).*\\.(jpe?g|bmp|gif|png|webp|jp2|j2k)$");
    }

    public static boolean isJP2Stream(BufferedInputStream bis) throws IOException {
        bis.mark(24);
        // TODO: JP2Decoder may need only 12bytes looks like, read 24 anyway
        byte[] b = new byte[24];
        bis.read(b);
        bis.reset();
        return JP2Decoder.isJPEG2000(b);
    }

    public static boolean isPdf(String filename) {
        return filename.matches("(?i).*\\.(pdf)$" );
    }

    public static boolean isZip(String filename) {
        return filename.matches("(?i).*\\.(zip|cbz)$");
    }

    public static boolean isRar(String filename) {
        return filename.matches("(?i).*\\.(rar|cbr)$");
    }

    public static boolean isTarball(String filename) {
        return filename.matches("(?i).*\\.(cbt|tar)$") ||
                isCompressedTarball(filename);
    }

    public static boolean isCompressedTarball(String filename) {
        return isTGZ(filename) || isTBZ(filename) ||
                isTXZ(filename) || isTZST(filename) ||
                isTBR(filename);
    }

    public static boolean isTGZ(String filename) {
        return filename.matches("(?i).*\\.(tar\\.gz|tgz)$");
    }

    public static boolean isTBZ(String filename) {
        return filename.matches("(?i).*\\.(tar\\.bz2?|tbz2?)$");
    }

    public static boolean isTXZ(String filename) {
        return filename.matches("(?i).*\\.(tar\\.xz|txz)$");
    }

    public static boolean isTZST(String filename) {
        return filename.matches("(?i).*\\.(tar\\.zstd?|tzs(|t|td))$");
    }

    public static boolean isTBR(String filename) {
        return filename.toLowerCase().matches("(?i).*\\.(tar\\.br|tbr)$");
    }

    public static boolean isSevenZ(String filename) {
        return filename.matches("(?i).*\\.(cb7|7z)$");
    }

    public static boolean isArchive(String filename) {
        return isZip(filename) || isRar(filename) ||
                isTarball(filename) || isSevenZ(filename) ||
                isPdf(filename);
    }

    public static int getDeviceWidth(Context context) {
        DisplayMetrics displayMetrics = MainApplication.getAppContext().getResources().getDisplayMetrics();
        WindowManager windowManager = (WindowManager) MainApplication.getAppContext().getSystemService(Context.WINDOW_SERVICE);
        // this gives wrong scaledDensity, also it is nonsensical,
        // why should the current screen be the default screen?
        //DisplayMetrics displayMetrics = new DisplayMetrics();
        //windowManager.getDefaultDisplay().getMetrics(displayMetrics);

        // the old way
        int width = displayMetrics.widthPixels;
        // using scaledDensity, because on PocoX4Pro5G 'density=2.75'
        // but 'scaledDensity=2.25' which is the accurate value
        float density = displayMetrics.scaledDensity;
        // the new way
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowMetrics windowMetrics = windowManager.getCurrentWindowMetrics();
            Insets insets = windowMetrics.getWindowInsets()
                    .getInsetsIgnoringVisibility(WindowInsets.Type.systemBars());
            if (Build.VERSION.SDK_INT >= 34) {
                //density = windowMetrics.getDensity();
            }
            width = Math.abs(windowMetrics.getBounds().width()) - insets.left - insets.right;
        }

        int value = Math.round(width / density);
        return value;
    }

    public static int getDeviceHeightPixels() {
        DisplayMetrics displayMetrics = MainApplication.getAppContext().getResources().getDisplayMetrics();
        return displayMetrics.heightPixels;
    }

    public static int getMaxPageSize(){
        DisplayMetrics displayMetrics = MainApplication.getAppContext().getResources().getDisplayMetrics();
        int w = displayMetrics.widthPixels;
        int h = displayMetrics.heightPixels;
        return Math.round(1.25f * Math.max(w, h));
    }

    public static String MD5(String string) {
        try {
            byte[] strBytes = string.getBytes();
            MessageDigest messageDigest = MessageDigest.getInstance("MD5");
            byte[] digest = messageDigest.digest(strBytes);
            StringBuffer sb = new StringBuffer();
            for (int i = 0; i < digest.length; ++i) {
                sb.append(Integer.toHexString((digest[i] & 0xFF) | 0x100).substring(1, 3));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            return string.replace("/", ".");
        }
    }

    public static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        // Raw height and width of image
        int height = options.outHeight;
        int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {

            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while ((halfHeight / inSampleSize) > reqHeight
                    && (halfWidth / inSampleSize) > reqWidth) {
                inSampleSize *= 2;
            }
        }

        return inSampleSize;
    }

    public static int calculateMemorySize(Context context, int percentage) {
        ActivityManager activityManager = (ActivityManager) context.getSystemService(ACTIVITY_SERVICE);
        int memoryClass = activityManager.getLargeMemoryClass();
        return 1024 * 1024 * memoryClass / percentage;
    }

    private static File getCacheFolder(){
        Context context = MainApplication.getAppContext();
        File dir = context.getExternalCacheDir();
        if (dir == null)
            dir = context.getCacheDir();
        if (dir==null)
            throw new RuntimeException("Couldn't find cache dir!");
        return dir;
    }

    public static File getCoverCacheFile(String identifier, String extension) {
        File dir = getCacheFolder();
        return new File(dir, "cover-"+Utils.MD5(identifier)+(extension!=null?"."+extension:""));
    }

    public static ByteArrayInputStream toByteArrayInputStream(InputStream is) throws IOException {
        return new ByteArrayInputStream(toByteArray(is));
    }

    public static byte[] toByteArray(InputStream is) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            byte[] b = new byte[4096];
            int n = 0;
            while ((n = is.read(b)) != -1) {
                output.write(b, 0, n);
            }
            return output.toByteArray();
        } catch (Exception e) {
            Log.e("Utils.toByteArray", e.getMessage(), e);
            throw new IOException(e);
        } finally {
            close(output);
            close(is);
        }
    }

    public static void copyToFile(InputStream inStream, File file) throws IOException {
        OutputStream outStream = new FileOutputStream(file);
        byte[] buffer = new byte[4 * 1024];
        int bytesRead;
        while ((bytesRead = inStream.read(buffer)) != -1) {
            outStream.write(buffer, 0, bytesRead);
        }
        close(inStream);
        close(outStream);
    }

    public static void close(ZipFile z) {
        if (z == null) return;
        try {
            //  java.util.zip.ZipFile does not implement Closeable on API 18--
            z.close();
        } catch (final Throwable ignored) {
            // do nothing;
        }
    }

    public static void close(final Closeable c) {
        if (c == null) return;
        try {
            c.close();
        } catch (final Throwable t) {
            Log.e("Bubble2","Utils.close(Closeable)",t);
        }
    }

    public static void close(final Object o) {
        if (o == null) return;
        try {
            Class c = o.getClass();
            Method m;
            if (Parser.class.isAssignableFrom(c)) {
                m = c.getMethod("destroy");
            } else if (Bitmap.class.isAssignableFrom(c)) {
                m = c.getMethod("recycle");
            } else {
                m = c.getMethod("close");
            }
            m.invoke(o);
        } catch (final Throwable t) {
            Log.e("Bubble2","Utils.close(Object)",t);
        }
    }

    public static File initCacheDirectory(String prefix) {
        File appCacheDir = getCacheFolder();
        // create a unique id
        String uuid = UUID.randomUUID().toString();
        prefix = (prefix == null || "".equals(prefix)) ? "" : prefix + "-";
        File mCacheDir = new File(appCacheDir, prefix + uuid);
        if (!mCacheDir.exists()) {
            boolean success = mCacheDir.mkdirs();
        }
        return mCacheDir;
    }

    // remove probably stale folders, exempt files (covers)
    public static void cleanCacheDir(){
        new Thread(){
            @Override
            public void run() {
                File cacheDir = getCacheFolder();
                File[] files = cacheDir.listFiles();
                if (files!=null)
                    for (File f: files) {
                        if (f.isDirectory())
                            rmDir(f,true);
                    }
            }
        }.run();
    }

    public static void rmDir(File dir) {
        rmDir(dir, true);
    }

    public static void rmDir(File dir, boolean recursive) {
        if (dir == null || !dir.isDirectory())
            return;

        File[] files = dir.listFiles();
        if (files != null)
            for (File f : files) {
                if (recursive && f.isDirectory())
                    rmDir(f, true);
                else if (f.isFile())
                    f.delete();
            }
        dir.delete();
    }

    public static File[] listExternalStorageDirs() {
        File[] externalFilesDirs = ContextCompat.getExternalFilesDirs(MainApplication.getAppContext(), null);
        // strip null entries (at least API16)
        ArrayList<File> entries = new ArrayList();
        for (int i = 0; externalFilesDirs != null && i < externalFilesDirs.length; i++) {
            File entry = externalFilesDirs[i];
            if (entry!=null)
                entries.add(entry);
        }
        return entries.toArray(new File[0]);
    }

    public static void showOKDialog(Activity activity, String title, String message) {
        AlertDialog dialog = new AlertDialog.Builder(activity, R.style.AppCompatAlertDialogStyle)
                .setTitle(title)
                .setMessage(message)
                .setNeutralButton(R.string.alert_action_neutral, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // nothing to do
                    }
                })
                .create();
        dialog.show();
    }

    public static String appendSlashIfMissing(String path){
        // don't treat empty strings
        if (path==null || path.equals(""))
            return path;

        if (!path.endsWith("/"))
            path += "/";

        return path;
    }

    public static String removeExtensionIfAny(String fileName) {
        int i = fileName.lastIndexOf('.');
        if (i < 1)
            return fileName;

        String name = fileName.substring(0, i);
        return name;
    }

    public static @ColorInt int getThemeColor(@AttrRes int resid, @StyleRes int themeid){
        TypedValue typedValue = new TypedValue();
        Resources.Theme theme = new ContextThemeWrapper(MainApplication.getAppContext(), themeid).getTheme();
        if (theme.resolveAttribute(resid, typedValue, true)) {
            @ColorInt int color = typedValue.data;
            return color;
        }
        return 0;
    }

    /**
     *  a helper method to measure time frames in milliseconds
     * @return current time in milliseconds
     */
    public static long now(){
        return System.currentTimeMillis();
    }

    /**
     *  a helper method to measure time frames in milliseconds
     * @param i time in milliseconds
     * @return milliseconds since time
     */
    public static long milliSecondsSince( long i ){
        return System.currentTimeMillis() - i;
    }

    /**
     * a helper method to nicely format the above output e.g. 12046ms -> 12.05s
     * @param i milliseconds
     * @return eg. "1.05" for a difference of 1047ms
     */
    public static String secondsSinceString( long i ){
        return String.format("%.2f", milliSecondsSince(i)/1000f);
    }
}
