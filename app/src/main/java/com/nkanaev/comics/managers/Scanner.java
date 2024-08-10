package com.nkanaev.comics.managers;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.os.Process;
import android.util.Log;
import com.nkanaev.comics.Constants;
import com.nkanaev.comics.MainApplication;
import com.nkanaev.comics.model.Comic;
import com.nkanaev.comics.model.Storage;
import com.nkanaev.comics.parsers.DirectoryParser;
import com.nkanaev.comics.parsers.Parser;
import com.nkanaev.comics.parsers.ParserFactory;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.*;

public class Scanner {
    private Thread mUpdateThread;
    private List<Handler> mUpdateHandler;

    private boolean mIsStopped;
    private boolean mIsRestarted;
    private File mSubFolder = null;
    private boolean mRefreshAll = false;

    private Handler mRestartHandler = new RestartHandler(this);

    private static class RestartHandler extends Handler {
        private WeakReference<Scanner> mScannerRef;

        public RestartHandler(Scanner scanner) {
            mScannerRef = new WeakReference<>(scanner);
        }

        @Override
        public void handleMessage(Message msg) {
            Scanner scanner = mScannerRef.get();
            if (scanner != null) {
                scanner.scanLibrary();
            }
        }
    }

    private static Scanner mInstance;

    public synchronized static Scanner getInstance() {
        if (mInstance == null) {
            mInstance = new Scanner();
        }
        return mInstance;
    }

    private Scanner() {
        mInstance = this;
        mUpdateHandler = new ArrayList<>();
    }

    public boolean isRunning() {
        return mUpdateThread != null &&
                mUpdateThread.isAlive() &&
                mUpdateThread.getState() != Thread.State.TERMINATED &&
                mUpdateThread.getState() != Thread.State.NEW;
    }

    public void stop() {
        mIsStopped = true;
    }

    public void scanLibrary() {
        scanLibrary(null,false);
    }

    public void scanLibrary(File limitToSubFolder, boolean refreshAll) {
        // already running instances are informed to stop and restart with new parameters
        if (isRunning()) {
            mSubFolder = limitToSubFolder;
            mRefreshAll = refreshAll;
            mIsStopped = true;
            mIsRestarted = true;
            return;
        }

        if (mUpdateThread == null || mUpdateThread.getState() == Thread.State.TERMINATED) {
            LibraryUpdateRunnable runnable = new LibraryUpdateRunnable();
            mSubFolder = limitToSubFolder;
            mRefreshAll = refreshAll;
            mUpdateThread = new Thread(runnable);
            mUpdateThread.setPriority(Process.THREAD_PRIORITY_DEFAULT + Process.THREAD_PRIORITY_LESS_FAVORABLE);
            mUpdateThread.start();
        }
    }

    public void addUpdateHandler(Handler handler) {
        mUpdateHandler.add(handler);
    }

    public void removeUpdateHandler(Handler handler) {
        mUpdateHandler.remove(handler);
    }

    private void notifyMediaUpdated() {
        for (Handler h : mUpdateHandler) {
            h.sendEmptyMessage(Constants.MESSAGE_MEDIA_UPDATED);
        }
    }

    private void notifyLibraryUpdateFinished() {
        for (Handler h : mUpdateHandler) {
            h.sendEmptyMessage(Constants.MESSAGE_MEDIA_UPDATE_FINISHED);
        }
    }

    private static void deleteCoverCacheFile(Comic comic) {
        File coverCacheFile = Utils.getCoverCacheFile(comic.getFile().getAbsolutePath(), "jpg");
        coverCacheFile.delete();
    }

    private static Comic findComicInList(List<Comic> comics, File file) {
        if (file != null && comics != null)
            for (Comic comic : comics) {
                if (comic.getFile().equals(file))
                    return comic;
            }
        return null;
    }

    private class LibraryUpdateRunnable implements Runnable {

        public void refreshAll(boolean refreshAll) {
            mRefreshAll = refreshAll;
        }

        public void limitToSubFolder(File subFolder) {
            mSubFolder = subFolder;
        }

        @Override
        public void run() {
            try {
                Context ctx = MainApplication.getAppContext();
                String libDir = MainApplication.getPreferences()
                        .getString(Constants.SETTINGS_LIBRARY_DIR, "");

                if (libDir.equals(""))
                    return;

                File libDirFile = new File(libDir);
                if (mSubFolder != null) {
                    File folder = mSubFolder.getAbsoluteFile();
                    File absLibDir = libDirFile.getAbsoluteFile();
                    boolean isSubFolder = false;
                    do {
                        if (folder.equals(absLibDir)) {
                            isSubFolder = true;
                            break;
                        }
                    } while ((folder = folder.getParentFile()) != null);
                    if (!isSubFolder)
                        return;
                }

                Storage storage = Storage.getStorage(ctx);
                //Map<File, Comic> storageFiles = new HashMap<>();
                List<Comic> storedComics = storage.listComics(mSubFolder != null ? mSubFolder.toString() : null);

                // create list of "known" files available in storage database
                // for defined subfolder scan all (forced)
                // or null (libDir)
    /*            for (Comic c : storage.listComics(mSubFolder != null ? mSubFolder.toString() : null)) {
                    // memorize known
                    if (mSubFolder != null || c.getTotalPages() > 1) {
                        storageFiles.put(c.getFile(), c);
                    }
                    // rescan empties
                    else {
                        storage.removeComic(c.getId());
                    }
                }
*/
                // search and add "unknown" comics
                Deque<File> directories = new ArrayDeque<>();
                directories.add(mSubFolder != null ? mSubFolder : libDirFile);
                while (!directories.isEmpty()) {
                    if (mIsStopped)
                        break;

                    File dir = directories.pop();
                    File[] files = dir.listFiles();
                    if (files == null)
                        continue;

                    int i = 0;
                    for (File file : files) {
                        i++;
                        if (mIsStopped)
                            break;

                        // add folder to search list, but continue (might be a dir-comic)
                        if (mSubFolder == null && file.isDirectory()) {
                            directories.add(file);
                        } else if (file.isFile() && !Utils.isArchive(file.getName())) {
                            // ignore unknown files
                            continue;
                        }

                        // skip known good comics (pages>0) to keep startup fast
                        // unless we are limited to a subfolder, then the refresh is forced
                        if (!mRefreshAll) {
                            Comic storedComic = findComicInList(storedComics,file);
                            if (storedComic!=null && storedComic.getTotalPages()>0) {
                                storedComics.remove(storedComic);
                                continue;
                            }
                        }

                        Parser parser = null;
                        try {
                            parser = ParserFactory.create(file);
                            Log.d("Scanner#148", file.toString());
                            // no parser? check log
                            if (parser == null)
                                continue;

                            int count = 0;
                            try {
                                parser.parse();
                                count = parser.numPages();
                            } catch (Exception e) {
                                Log.e("Scanner", "parse", e);
                            }

                            // ignore non-comic folders
                            if (parser.getType().equals(DirectoryParser.TYPE) && count < 1)
                                continue;

                            // add/update comic meta data for next run
                            // keep empty comic files, might point to bugs
                            Comic storedComic = findComicInList(storedComics,file);
                            if (storedComic!=null) {
                                storage.updateBook(storedComic.getId(), parser.getType(), count);
                                storedComics.remove(storedComic);
                                deleteCoverCacheFile(storedComic);
                            } else {
                                storage.addBook(file, parser.getType(), count);
                            }
                            // update only every 10th comic for performance reasons
                            if (i % 10 == 0) notifyMediaUpdated();
                        } catch (Exception e) {
                            Log.e("Scanner", "update", e);
                        }
                    }
                }

                // delete removed comics/cleanup cover cache
                // unless we were rudely interrupted
                if (!mIsStopped)
                    for (Comic missing : storedComics) {
                        deleteCoverCacheFile(missing);
                        storage.removeComic(missing.getId());
                    }

            } finally {
                mIsStopped = false;

                if (mIsRestarted) {
                    mIsRestarted = false;
                    mRestartHandler.sendEmptyMessageDelayed(1, 200);
                } else {
                    notifyLibraryUpdateFinished();
                }
            }
        }
    }
}
