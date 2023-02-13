package com.nkanaev.comics.managers;

import com.nkanaev.comics.model.Comic;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DirectoryListingManager {
    private final List<Comic> mComics;
    private final List<String> mDirectoryDisplays;
    private final File mLibraryDir;

    public DirectoryListingManager(List<Comic> comics, String libraryDir) {
        mLibraryDir = new File(libraryDir != null ? libraryDir : "/");

        // sort naturally ignore-case
        // library dir ends up first in any case
        Collections.sort(comics, new IgnoreCaseComparator() {
            @Override
            public String stringValue(Object o) {
                return o==null?"":((Comic) o).getFile().getParentFile().getAbsolutePath();
            }
        });
        mComics = comics;

        List<String> directoryDisplays = new ArrayList<>();
        for (Comic comic : mComics) {
            File comicDir = comic.getFile().getParentFile();

            if (comicDir.equals(mLibraryDir)) {
                directoryDisplays.add("~ (" + comicDir.getName() + ")");
            }
            else if (comicDir.getParentFile().equals(mLibraryDir)) {
                directoryDisplays.add(comicDir.getName());
            }
            else {
                List<String> intermediateDirs = new ArrayList<>();
                File current = comicDir;
                /*
                while (current != null && !current.equals(mLibraryDir)) {
                    intermediateDirs.add(0, current.getName());
                    current = current.getParentFile();
                }
                if (current == null) {
                    // impossible, but occurs
                    directoryDisplays.add(comicDir.getName());
                }
                else {
                    directoryDisplays.add(TextUtils.join(" | ", intermediateDirs));
                }
                */
                String dirText = mLibraryDir.toURI().relativize(current.toURI()).getPath();
                // strip trailing slash(es)
                dirText = dirText.replaceFirst("/*$", "");
                directoryDisplays.add(dirText);
            }
        }

        mDirectoryDisplays = directoryDisplays;
    }

    public String getDirectoryDisplayAtIndex(int idx) {
        return mDirectoryDisplays.get(idx);
    }

    public Comic getComicAtIndex(int idx) {
        return mComics.get(idx);
    }

    public String getDirectoryAtIndex(int idx) {
        return mComics.get(idx).getFile().getParent();
    }

    public int getCount() {
        return mComics.size();
    }
}
