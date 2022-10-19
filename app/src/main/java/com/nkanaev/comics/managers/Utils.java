package com.nkanaev.comics.managers;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.Log;
import com.gemalto.jp2.JP2Decoder;
import com.nkanaev.comics.MainApplication;

import java.io.*;
import java.security.MessageDigest;
import java.util.UUID;

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
        return filename.matches("(?i).*\\.(jpg|jpeg|bmp|gif|png|webp|jp2|j2k)$");
    }

    public static boolean isJP2Stream(BufferedInputStream bis) throws IOException {
        bis.mark(24);
        // TODO: JP2Decoder may need only 12bytes looks like, read 24 anyway
        byte[] b = new byte[24];
        bis.read(b);
        bis.reset();
        return JP2Decoder.isJPEG2000(b);
    }

    public static boolean isZip(String filename) {
        return filename.toLowerCase().matches(".*\\.(zip|cbz)$");
    }

    public static boolean isRar(String filename) {
        return filename.toLowerCase().matches(".*\\.(rar|cbr)$");
    }

    public static boolean isTarball(String filename) {
        return filename.matches("(?i).*\\.(cbt|tar)$") || isTGZ(filename) || isTBZ(filename) || isTXZ(filename) || isTZST(filename) || isTBR(filename);
    }

    public static boolean isTGZ(String filename) {
        return filename.toLowerCase().matches("(?i).*\\.(tar\\.gz|tgz)$");
    }

    public static boolean isTBZ(String filename) {
        return filename.toLowerCase().matches("(?i).*\\.(tar\\.bz2?|tbz2?)$");
    }

    public static boolean isTXZ(String filename) {
        return filename.toLowerCase().matches("(?i).*\\.(tar\\.xz|txz)$");
    }

    public static boolean isTZST(String filename) {
        return filename.toLowerCase().matches("(?i).*\\.(tar\\.zstd?|tzs(|t|td))$");
    }

    public static boolean isTBR(String filename) {
        return filename.toLowerCase().matches("(?i).*\\.(tar\\.br|tbr)$");
    }

    public static boolean isSevenZ(String filename) {
        return filename.toLowerCase().matches(".*\\.(cb7|7z)$");
    }

    public static boolean isArchive(String filename) {
        return isZip(filename) || isRar(filename) || isTarball(filename) || isSevenZ(filename);
    }

    public static int getDeviceWidth(Context context) {
        DisplayMetrics displayMetrics = context.getResources().getDisplayMetrics();
        int value = Math.round(displayMetrics.widthPixels / displayMetrics.density);
        return value;
    }

    public static int getDeviceHeight(Context context) {
        DisplayMetrics displayMetrics = context.getResources().getDisplayMetrics();
        int value = Math.round(displayMetrics.heightPixels / displayMetrics.density);
        return value;
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

    public static File getCacheFile(Context context, String identifier) {
        File dir = context.getExternalCacheDir();
        if (dir == null)
            dir = context.getCacheDir();
        return new File(dir, Utils.MD5(identifier));
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
            output.close();
        }
    }

    public static void copyToFile(InputStream inStream, File file) throws IOException {
        OutputStream outStream = new FileOutputStream(file);
        byte[] buffer = new byte[4 * 1024];
        int bytesRead;
        while ((bytesRead = inStream.read(buffer)) != -1) {
            outStream.write(buffer, 0, bytesRead);
        }
        Utils.close(inStream);
        Utils.close(outStream);
    }

    public static void close(final Closeable c) {
        if (c == null) return;
        try {
            c.close();
        } catch (final Throwable ignored) {
            // do nothing;
        }
    }

    public static File initCacheDirectory(String prefix) {
        File appCacheDir = MainApplication.getAppContext().getExternalCacheDir();
        if (appCacheDir == null)
            appCacheDir = MainApplication.getAppContext().getCacheDir();
        // create a unique id
        String uuid = UUID.randomUUID().toString();
        prefix = (prefix == null || "".equals(prefix)) ? "" : prefix + "-";
        File mCacheDir = new File(appCacheDir, prefix + uuid);
        if (!mCacheDir.exists()) {
            boolean success = mCacheDir.mkdirs();
        }
        return mCacheDir;
    }

    public static void rmDir(File dir) {
        rmDir(dir, true);
    }

    public static void rmDir(File dir, boolean recursive) {
        if (dir == null || !dir.isDirectory())
            return;

        for (File f : dir.listFiles()) {
            if (recursive && f.isDirectory())
                rmDir(f, true);
            else if (f.isFile())
                f.delete();
        }
        dir.delete();
    }

}
