package com.nkanaev.comics.fragment;

import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.activity.OnBackPressedCallback;
import android.util.Log;

import java.io.File;

import com.nkanaev.comics.adapters.DirectoryAdapter;
import com.nkanaev.comics.adapters.ThumbnailDirectoryAdapter;
import com.nkanaev.comics.Constants;
import com.nkanaev.comics.MainApplication;

public class ThumbnailBrowserFragment extends BrowserFragment {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        initBackPressedCallback();
    }

    private void initBackPressedCallback() {
        requireActivity().getOnBackPressedDispatcher()
            .addCallback(this, new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    String rootDirPath = MainApplication.getPreferences()
                            .getString(Constants.SETTINGS_LIBRARY_DIR, "");
                    File rootDir = new File(rootDirPath);

                    if (!mCurrentDir.equals(rootDir)) {
                        File parentFile = mCurrentDir.getParentFile();
                        setCurrentDirectory(parentFile);
                    } else {
                        getActivity().finish();
                    }
                }
        });
    }

    @Override
    protected DirectoryAdapter createDirectoryAdapter() {
        return new ThumbnailDirectoryAdapter(getContext(), mCurrentDir, mSubdirs);
    }
}
