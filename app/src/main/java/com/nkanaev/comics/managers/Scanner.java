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

    public void forceScanLibrary() {
        if (isRunning()) {
            mIsStopped = true;
            mIsRestarted = true;
        } else {
            scanLibrary();
        }
    }

    public void scanLibrary() {
        if (mUpdateThread == null || mUpdateThread.getState() == Thread.State.TERMINATED) {
            LibraryUpdateRunnable runnable = new LibraryUpdateRunnable();
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

    private class LibraryUpdateRunnable implements Runnable {
        @Override
        public void run() {
            try {
                Context ctx = MainApplication.getAppContext();
                String libDir = MainApplication.getPreferences()
                        .getString(Constants.SETTINGS_LIBRARY_DIR, "");
                if (libDir.equals("")) return;
                Storage storage = Storage.getStorage(ctx);
                Map<File, Comic> storageFiles = new HashMap<>();

                // create list of "known" files available in storage database
                for (Comic c : storage.listComics()) {
                    // memorize known
                    if (c.getTotalPages()>1)
                        storageFiles.put(c.getFile(), c);
                    // rescan empties
                    else
                        storage.removeComic(c.getId());
                }

                // search and add "unknown" comics
                Deque<File> directories = new ArrayDeque<>();
                directories.add(new File(libDir));
                while (!directories.isEmpty()) {
                    File dir = directories.pop();
                    File[] files = dir.listFiles();
                    Arrays.sort(files);
                    for (File file : files) {
                        if (mIsStopped) return;

                        // add folder to search list, but continue (might be a dir-comic)
                        if (file.isDirectory()) {
                            directories.add(file);
                        } else if (!Utils.isArchive(file.getName())) {
                            // ignore unknown files
                            continue;
                        }

                        // skip known comics to keep startup fast
                        if (storageFiles.containsKey(file)) {
                            storageFiles.remove(file);
                            continue;
                        }

                        Log.d("Scanner#142", file.getPath().toString());
                        Parser parser = null;
                        try {
                            parser = ParserFactory.create(file);
                            Log.d("Scanner#148",file.toString());
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

                            // ignore empty folders
                            if (parser.getType().equals(DirectoryParser.TYPE) && count < 1)
                                continue;

                            // memorize comic meta data for next run
                            // keep empty comic files, might point to bugs
                            storage.addBook(file, parser.getType(), count);
                            notifyMediaUpdated();
                        } catch (Exception e) {
                            Log.e("Scanner", "update", e);
                        }
                    }
                }

                // delete missing comics
                for (Comic missing : storageFiles.values()) {
                    File coverCache = Utils.getCacheFile(ctx, missing.getFile().getAbsolutePath());
                    coverCache.delete();
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
