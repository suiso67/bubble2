package com.nkanaev.comics.fragment;

import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.nkanaev.comics.Constants;
import com.nkanaev.comics.R;
import com.nkanaev.comics.MainApplication;
import com.nkanaev.comics.activity.MainActivity;
import com.nkanaev.comics.activity.ReaderActivity;
import com.nkanaev.comics.adapters.DirectoryAdapter;
import com.nkanaev.comics.adapters.ThumbnailDirectoryAdapter;
import com.nkanaev.comics.managers.IgnoreCaseComparator;
import com.nkanaev.comics.managers.Utils;
import com.nkanaev.comics.parsers.Parser;
import com.nkanaev.comics.parsers.ParserFactory;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;


public class BrowserFragment extends Fragment
        implements AdapterView.OnItemClickListener, AdapterView.OnItemLongClickListener, SwipeRefreshLayout.OnRefreshListener {
    private final static String STATE_CURRENT_DIR = "stateCurrentDir";

    private ListView mListView;
    private SwipeRefreshLayout mRefreshLayout;

    private File mRootDir;
    protected DirectoryAdapter mBrowserAdapter;
    protected File mCurrentDir;
    protected File[] mSubdirs = new File[]{};

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String rootDirPath = MainApplication.getPreferences()
                .getString(Constants.SETTINGS_LIBRARY_DIR, "");
        mRootDir = new File(rootDirPath);

        if (savedInstanceState != null) {
            mCurrentDir = (File) savedInstanceState.getSerializable(STATE_CURRENT_DIR);
        } else {
            mCurrentDir = mRootDir;
        }

        getActivity().setTitle(R.string.menu_browser);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.fragment_browser, container, false);

        initListView(view);
        initRefreshView(view);

        setCurrentDirectory(mCurrentDir);

        return view;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putSerializable(STATE_CURRENT_DIR, mCurrentDir);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onDestroyView() {
        /*
        ViewGroup toolbar = (ViewGroup) getActivity().findViewById(R.id.toolbar);
        ViewGroup breadcrumb = (ViewGroup) toolbar.findViewById(R.id.breadcrumb_layout);
        toolbar.removeView(breadcrumb);
        */
        super.onDestroyView();
    }

    private void initListView(View view) {
        mBrowserAdapter = createDirectoryAdapter();
        mListView = (ListView) view.findViewById(R.id.listview_browser);
        mListView.setAdapter(mBrowserAdapter);
        mListView.setOnItemClickListener(this);
        mListView.setOnItemLongClickListener(this);
    }

    protected DirectoryAdapter createDirectoryAdapter() {
        return new DirectoryAdapter(getContext(), mCurrentDir, mSubdirs);
    }

    protected void setCurrentDirectory(File dir) {
        mCurrentDir = dir;
        ArrayList<File> subDirs = new ArrayList<>();

        // list only folders and known archive types
        File[] files = mCurrentDir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory() || Utils.isArchive(f.getName())) {
                    subDirs.add(f);
                }
            }
        }

        File[] validFolders = Utils.listExternalStorageDirs();
        // ensure paths to known storages are listed, even if not browsable
        if (Utils.isOreoOrLater()) {
            Path parent = mCurrentDir.toPath();
            for (File validPath : validFolders) {
                if (!validPath.toPath().startsWith(parent))
                    continue;

                Path relPath = parent.relativize(validPath.toPath());
                if (relPath.getNameCount() < 1)
                    continue;

                File entry = new File(mCurrentDir, relPath.getName(0).toString());
                if (!subDirs.contains(entry))
                    subDirs.add(entry);
            }
        }

        // sort alphabetically ignore-case
        Collections.sort(subDirs, new IgnoreCaseComparator() {
            @Override
            public String stringValue(Object o) {
                return ((File) o).getName();
            }
        });

        // add '..' to top
        if (!mCurrentDir.getAbsolutePath().equals(mRootDir.getAbsolutePath())) {
            subDirs.add(0,mCurrentDir.getParentFile());
        }

        mSubdirs = subDirs.toArray(new File[subDirs.size()]);

        mBrowserAdapter.setCurrentDirectory(mCurrentDir);
        mBrowserAdapter.setSubdirectories(mSubdirs);

        if (mListView != null) {
            mListView.invalidateViews();
        }

        //mDirTextView.setText(dir.getAbsolutePath());
        ((MainActivity)getActivity()).setSubTitle(dir.getAbsolutePath());
    }

    private void initRefreshView(View view) {
        mRefreshLayout = (SwipeRefreshLayout) view.findViewById(R.id.SwipeRefreshLayout);
        if (mRefreshLayout!=null) {
            mRefreshLayout.setColorSchemeResources(R.color.primary);
            mRefreshLayout.setOnRefreshListener(this);
            mRefreshLayout.setEnabled(true);
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        File file = mSubdirs[position];

        // folders can be opened via long click below
        if (file.isDirectory()) {
            setCurrentDirectory(file);
            return;
        }

        Intent intent = new Intent(getActivity(), ReaderActivity.class);
        intent.putExtra(ReaderFragment.PARAM_HANDLER, file);
        intent.putExtra(ReaderFragment.PARAM_MODE, ReaderFragment.Mode.MODE_BROWSER);
        startActivity(intent);
    }

    @Override
    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
        File file = mSubdirs[position];

        if (!file.isDirectory())
            return true;

        // check if directory is folder-based comic
        try {
            Parser p = ParserFactory.create(file);
            if (p.numPages() < 1)
                return true;
        } catch (Exception e) {
            // TODO: not rly expecting an exception here
            Log.e("BrowserFragment","onItemLongClick",e);
            return true;
        }

        Intent intent = new Intent(getActivity(), ReaderActivity.class);
        intent.putExtra(ReaderFragment.PARAM_HANDLER, file);
        intent.putExtra(ReaderFragment.PARAM_MODE, ReaderFragment.Mode.MODE_BROWSER);
        startActivity(intent);
        return true;
    }

    @Override
    public void onRefresh() {
        // refresh on show
        if (mCurrentDir!=null)
            setCurrentDirectory(mCurrentDir);
        mRefreshLayout.setRefreshing(false);
    }
}
