package com.nkanaev.comics.fragment;

import android.content.Intent;
import android.content.res.Resources;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import com.nkanaev.comics.R;
import com.nkanaev.comics.activity.ReaderActivity;
import com.nkanaev.comics.managers.Utils;
import com.nkanaev.comics.parsers.Parser;
import com.nkanaev.comics.parsers.ParserFactory;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;


public class BrowserFragment extends Fragment
        implements AdapterView.OnItemClickListener, AdapterView.OnItemLongClickListener {
    private final static String STATE_CURRENT_DIR = "stateCurrentDir";

    private ListView mListView;
    private File mCurrentDir;
    private File mRootDir = new File("/");
    private File[] mSubdirs = new File[]{};
    private TextView mDirTextView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            mCurrentDir = (File) savedInstanceState.getSerializable(STATE_CURRENT_DIR);
        } else {
            mCurrentDir = Environment.getExternalStorageDirectory();
        }

        getActivity().setTitle(R.string.menu_browser);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.fragment_browser, container, false);

        ViewGroup toolbar = (ViewGroup) getActivity().findViewById(R.id.toolbar);
        ViewGroup breadcrumbLayout = (ViewGroup) inflater.inflate(R.layout.breadcrumb, toolbar, false);
        toolbar.addView(breadcrumbLayout);
        mDirTextView = (TextView) breadcrumbLayout.findViewById(R.id.dir_textview);

        setCurrentDir(mCurrentDir);

        mListView = (ListView) view.findViewById(R.id.listview_browser);
        mListView.setAdapter(new DirectoryAdapter());
        mListView.setOnItemClickListener(this);
        mListView.setOnItemLongClickListener(this);

        return view;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putSerializable(STATE_CURRENT_DIR, mCurrentDir);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onDestroyView() {
        ViewGroup toolbar = (ViewGroup) getActivity().findViewById(R.id.toolbar);
        ViewGroup breadcrumb = (ViewGroup) toolbar.findViewById(R.id.breadcrumb_layout);
        toolbar.removeView(breadcrumb);
        super.onDestroyView();
    }

    private void setCurrentDir(File dir) {
        mCurrentDir = dir;
        ArrayList<File> subDirs = new ArrayList<>();
        if (!mCurrentDir.getAbsolutePath().equals(mRootDir.getAbsolutePath())) {
            subDirs.add(mCurrentDir.getParentFile());
        }
        File[] files = mCurrentDir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory() || Utils.isArchive(f.getName())) {
                    subDirs.add(f);
                }
            }
        }

        File[] validFolders = Utils.listExternalStorageDirs();
        // ensure paths to storages are listed, even if not browsable
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

        Collections.sort(subDirs);
        mSubdirs = subDirs.toArray(new File[subDirs.size()]);

        if (mListView != null) {
            mListView.invalidateViews();
        }

        mDirTextView.setText(dir.getAbsolutePath());
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        File file = mSubdirs[position];

        // folders can be opened via long click below
        if (file.isDirectory()) {
            setCurrentDir(file);
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

    private void setIcon(View convertView, File file) {
        ImageView view = (ImageView) convertView.findViewById(R.id.directory_row_icon);
        GradientDrawable shape = (GradientDrawable) view.getBackground();
        ImageView rainbow = (ImageView) convertView.findViewById(R.id.directory_row_rainbow);
        rainbow.setVisibility(View.INVISIBLE);

        // default bg color is grey
        int colorRes = R.color.circle_grey;

        if (file.isDirectory()) {
            view.setImageResource(R.drawable.ic_folder_white_24dp);

            try {
                Parser p = ParserFactory.create(file);
                if (p.numPages()>0) {
                    colorRes = android.R.color.transparent;
                    rainbow.setVisibility(View.VISIBLE);
                }
            } catch (Exception ignored) {
            }
        } else {
            view.setImageResource(R.drawable.ic_file_document_box_white_24dp);

            String name = file.getName();
            if (Utils.isZip(name)) {
                colorRes = R.color.circle_green;
            } else if (Utils.isRar(name)) {
                colorRes = R.color.circle_red;
            } else if (Utils.isSevenZ(name)) {
                colorRes = R.color.circle_yellow;
            } else if (Utils.isTarball(name)){
                colorRes = R.color.circle_orange;
            }
        }

        shape.setColor(getResources().getColor(colorRes));
    }

    private final class DirectoryAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return mSubdirs.length;
        }

        @Override
        public long getItemId(int position) {
            return 0;
        }

        @Override
        public Object getItem(int position) {
            return mSubdirs[position];
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = getActivity().getLayoutInflater().inflate(R.layout.row_directory, parent, false);
            }

            File file = mSubdirs[position];
            TextView textView = (TextView) convertView.findViewById(R.id.directory_row_text);

            if (position == 0 && !mCurrentDir.getAbsolutePath().equals(mRootDir.getAbsolutePath())) {
                textView.setText("..");
            } else {
                textView.setText(file.getName());
            }

            setIcon(convertView, file);

            return convertView;
        }
    }
}
