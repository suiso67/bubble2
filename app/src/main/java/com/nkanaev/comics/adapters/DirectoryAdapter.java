package com.nkanaev.comics.adapters;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.io.File;

import com.nkanaev.comics.Constants;
import com.nkanaev.comics.MainApplication;
import com.nkanaev.comics.R;
import com.nkanaev.comics.managers.Utils;
import com.nkanaev.comics.parsers.Parser;
import com.nkanaev.comics.parsers.ParserFactory;

public class DirectoryAdapter extends BaseAdapter {
    protected File mRootDir;
    protected Context mContext;
    protected File mCurrentDirectory;
    protected File[] mSubdirectories;

    public DirectoryAdapter(Context context, File currentDirectory, File[] subdirectories) {
        String rootDirPath = MainApplication.getPreferences()
                .getString(Constants.SETTINGS_LIBRARY_DIR, "");
        mRootDir = new File(rootDirPath);

        mContext = context;
        mCurrentDirectory = currentDirectory;
        mSubdirectories = subdirectories;
    }

    public void setCurrentDirectory(File currentDirectory) {
        mCurrentDirectory = currentDirectory;
    }

    public void setSubdirectories(File[] subdirectories) {
        mSubdirectories = subdirectories;
    }

    @Override
    public int getCount() {
        return mSubdirectories.length;
    }

    @Override
    public long getItemId(int position) {
        return 0;
    }

    @Override
    public Object getItem(int position) {
        return mSubdirectories[position];
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        final LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        if (convertView == null) {
            convertView = inflater.inflate(R.layout.row_directory, parent, false);
        }

        initPath(convertView, position);
        initIcon(convertView, position);

        return convertView;
    }

    protected void initPath(View convertView, int position) {
        TextView textView = (TextView) convertView.findViewById(R.id.directory_row_text);
        File file = mSubdirectories[position];

        if (isParentDirectoryItem(position)) {
            textView.setText("..");
        } else {
            textView.setText(file.getName());
        }
    }

    protected boolean isParentDirectoryItem(int position) {
        boolean isRootDirectory = mCurrentDirectory.getAbsolutePath().equals(mRootDir.getAbsolutePath());
        return position == 0 && !isRootDirectory;
    }

    private void initIcon(View convertView, int position) {
        File file = mSubdirectories[position];

        ImageView view = (ImageView) convertView.findViewById(R.id.directory_row_icon);
        ImageView circle = (ImageView) convertView.findViewById(R.id.directory_row_circle);
        GradientDrawable circleDrawable = (GradientDrawable) circle.getDrawable();

        view.setImageResource(R.drawable.ic_folder_24);
        int colorRes = R.color.circle_grey;
        circleDrawable.setColor(mContext.getResources().getColor(colorRes));
        circle.setVisibility(View.VISIBLE);

        // ignore top parent dir entry
        if (position == 0)
            return;

        if (file.isDirectory()) {
            try {
                Parser p = ParserFactory.create(file);
                if (p.numPages()>0) {
                    view.setImageResource(R.drawable.ic_image_folder_24);
                    colorRes = R.color.circle_teal;
                }
            } catch (Exception ignored) {
            }

            // TODO: file listing on folders with many files slows down scrolling
            File[] files = file.listFiles();
            if (files != null)
                for (File f : files) {
                    if (Utils.isArchive(f.getName())) {
                        // show rainbow
                        circle.setVisibility(View.INVISIBLE);
                        break;
                    }
                }

            circleDrawable.setColor(mContext.getResources().getColor(colorRes));
            return;
        }

        view.setImageResource(R.drawable.ic_article_24);
        String name = file.getName();
        if (!Utils.isArchive(name))
            return;

        view.setImageResource(R.drawable.ic_text_image_document_24);
        if (Utils.isPdf(name)) {
            colorRes = R.color.circle_blue;
        } else if (Utils.isZip(name)) {
            colorRes = R.color.circle_green;
        } else if (Utils.isRar(name)) {
            colorRes = R.color.circle_red;
        } else if (Utils.isSevenZ(name)) {
            colorRes = R.color.circle_yellow;
        } else if (Utils.isTarball(name)){
            colorRes = R.color.circle_orange;
        }

        circleDrawable.setColor(mContext.getResources().getColor(colorRes));
    }
}
