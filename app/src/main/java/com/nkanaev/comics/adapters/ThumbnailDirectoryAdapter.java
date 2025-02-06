package com.nkanaev.comics.adapters;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.io.File;
import java.util.List;

import com.nkanaev.comics.R;
import com.nkanaev.comics.managers.LocalCoverHandler;
import com.nkanaev.comics.managers.Utils;
import com.nkanaev.comics.model.Comic;
import com.nkanaev.comics.model.Storage;
import com.nkanaev.comics.parsers.Parser;
import com.nkanaev.comics.parsers.ParserFactory;
import com.squareup.picasso.Picasso;

import android.util.Log;

public class ThumbnailDirectoryAdapter extends DirectoryAdapter {
    private Picasso mPicasso;

    public ThumbnailDirectoryAdapter(Context context, File currentDirectory, File[] subdirectories) {
        super(context, currentDirectory, subdirectories);

        mPicasso = new Picasso.Builder(context)
                .addRequestHandler(new LocalCoverHandler(context))
                .build();

        Storage storage = Storage.getStorage(mContext);
        List<Comic> comics = storage.listComics();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        final LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        if (convertView == null) {
            convertView = inflater.inflate(R.layout.row_directory_thumbnail, parent, false);
        }

        initPath(convertView, position);
        initThumbnail(convertView, position);

        return convertView;
    }

    private void initThumbnail(View convertView, int position) {
        ImageView imageView = (ImageView) convertView.findViewById(R.id.directory_row_thumbnail_imageview);

        if (isParentDirectoryItem(position)) {
            imageView.setImageDrawable(null);
            return;
        }

        File subdirectory = mSubdirectories[position];
        Storage storage = Storage.getStorage(mContext);
        List<Comic> comics = storage.listComicsUnder(subdirectory.getAbsolutePath());

        if (comics.size() == 0) {
            comics = storage.listComics(mCurrentDirectory.getAbsolutePath(), subdirectory.getName());
        }

        if (comics.size() == 0) {
            imageView.setImageDrawable(null);
            return;
        }

        Comic thumbnailComic = comics.get(0);
        Uri uri = LocalCoverHandler.getComicCoverUri(thumbnailComic);
        mPicasso.load(uri).into(imageView);
    }
}
