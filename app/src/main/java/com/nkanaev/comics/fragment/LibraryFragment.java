package com.nkanaev.comics.fragment;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.util.Log;
import android.view.*;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.nkanaev.comics.Constants;
import com.nkanaev.comics.MainApplication;
import com.nkanaev.comics.R;
import com.nkanaev.comics.activity.MainActivity;
import com.nkanaev.comics.managers.DirectoryListingManager;
import com.nkanaev.comics.managers.LocalCoverHandler;
import com.nkanaev.comics.managers.Scanner;
import com.nkanaev.comics.managers.Utils;
import com.nkanaev.comics.model.Comic;
import com.nkanaev.comics.model.Storage;
import com.nkanaev.comics.view.DirectorySelectDialog;
import com.nkanaev.comics.view.PreCachingGridLayoutManager;
import com.squareup.picasso.Picasso;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.List;


public class LibraryFragment extends Fragment
        implements
        DirectorySelectDialog.OnDirectorySelectListener,
        AdapterView.OnItemClickListener,
        SwipeRefreshLayout.OnRefreshListener,
        UpdateHandlerTarget {
    private final static String BUNDLE_DIRECTORY_DIALOG_SHOWN = "BUNDLE_DIRECTORY_DIALOG_SHOWN";

    private DirectoryListingManager mComicsListManager;
    private DirectorySelectDialog mDirectorySelectDialog;
    private SwipeRefreshLayout mRefreshLayout;
    private View mEmptyView;
    private RecyclerView mFolderListView;
    private Picasso mPicasso;
    private boolean mIsRefreshPlanned = false;
    private Handler mUpdateHandler = new UpdateHandler(this);

    public LibraryFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // If you have access to the external storage, do whatever you need
        if (Utils.isROrLater()) {
            if (!Environment.isExternalStorageManager()) {
                // If you don't have access, launch a new activity to show the user the system's dialog
                // to allow access to the external storage
                Intent intent = new Intent();
                intent.setAction(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                Uri uri = Uri.fromParts("package", MainActivity.PACKAGE_NAME, null);
                intent.setData(uri);
                startActivity(intent);
            }
        } else {
            String permission = Manifest.permission.READ_EXTERNAL_STORAGE;
            if (ContextCompat.checkSelfPermission(getActivity(), permission)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(getActivity(),
                        new String[]{permission},
                        1);
            }
        }

        /*
        // ways to get sdcard mount points
        // this is API R 30, N 24 only
        final StorageManager storageManager = (StorageManager) getContext()
                .getSystemService(MainActivity.STORAGE_SERVICE);
        List<StorageVolume> volumes = storageManager.getStorageVolumes();
        for (StorageVolume v : volumes) {
            Log.i("Volumes", v.getDirectory().toString());
        }
        // this is Q 29
        Set<String> names = MediaStore.getExternalVolumeNames(getContext());
        for (String n : names) {
            Log.i("MS-Names", n);
        }
        */
        File[] externalStorageFiles = Utils.listExternalStorageDirs();
        for (File f : externalStorageFiles) {
            Log.d(getClass().getCanonicalName(), "External Storage -> " + f.toString());
        }

        mDirectorySelectDialog = new DirectorySelectDialog(getActivity(), externalStorageFiles);
        mDirectorySelectDialog.setCurrentDirectory(Environment.getExternalStorageDirectory());
        mDirectorySelectDialog.setOnDirectorySelectListener(this);

        getComics();

        setHasOptionsMenu(true);
    }

    @Override
    public void onResume() {
        super.onResume();
        Scanner.getInstance().addUpdateHandler(mUpdateHandler);
        if (Scanner.getInstance().isRunning()) {
            setLoading(true);
        }
    }

    @Override
    public void onPause() {
        Scanner.getInstance().removeUpdateHandler(mUpdateHandler);
        setLoading(false);
        super.onPause();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final ViewGroup view = (ViewGroup) inflater.inflate(R.layout.fragment_library, container, false);

        mPicasso = ((MainActivity) getActivity()).getPicasso();

        mRefreshLayout = view.findViewById(R.id.fragmentLibraryRefreshLayout);
        mRefreshLayout.setColorSchemeResources(R.color.primary);
        //mRefreshLayout.setProgressBackgroundColorSchemeColor(Color.BLACK);
        mRefreshLayout.setOnRefreshListener(this);
        mRefreshLayout.setEnabled(true);

        mFolderListView = view.findViewById(R.id.groupGridView);
        mFolderListView.setAdapter(new GroupGridAdapter());

        final int numColumns = calculateNumColumns();
        PreCachingGridLayoutManager layoutManager = new PreCachingGridLayoutManager(getActivity(), numColumns);
        int height = Utils.getDeviceHeightPixels();
        layoutManager.setExtraLayoutSpace(height*2);
        mFolderListView.setLayoutManager(layoutManager);

        int spacing = (int) getResources().getDimension(R.dimen.grid_margin);
        mFolderListView.addItemDecoration(new GridSpacingItemDecoration(numColumns, spacing));

        // some performance optimizations
        mFolderListView.setHasFixedSize(true);
        mFolderListView.setItemViewCacheSize(Math.max(4 * numColumns,40));
        //mFolderListView.getRecycledViewPool().setMaxRecycledViews();

        mEmptyView = view.findViewById(R.id.library_empty);

        showEmptyMessage(mComicsListManager.getCount() == 0);
        getActivity().setTitle(R.string.menu_library);
        String folder = getLibraryDir();
        ((MainActivity) getActivity()).setSubTitle(Utils.appendSlashIfMissing(folder));

        return view;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        menu.clear();
        inflater.inflate(R.menu.library, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menuLibrarySetDir) {
            if (Scanner.getInstance().isRunning()) {
                Scanner.getInstance().stop();
            }

            mDirectorySelectDialog.show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putBoolean(BUNDLE_DIRECTORY_DIALOG_SHOWN,
                (mDirectorySelectDialog != null) && mDirectorySelectDialog.isShowing());
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onDirectorySelect(File file) {
        SharedPreferences preferences = MainApplication.getPreferences();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(Constants.SETTINGS_LIBRARY_DIR, file.getAbsolutePath());
        editor.apply();

        Scanner.getInstance().forceScanLibrary();
        showEmptyMessage(false);
        setLoading(true);
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        String path = mComicsListManager.getDirectoryAtIndex(position);
        LibraryBrowserFragment fragment = LibraryBrowserFragment.create(path);
        ((MainActivity) getActivity()).pushFragment(fragment);
    }

    @Override
    public void onRefresh() {
        if (!Scanner.getInstance().isRunning()) {
            setLoading(true);
            Scanner.getInstance().scanLibrary();
        }
    }

    private void getComics() {
        List<Comic> comics = Storage.getStorage(getActivity()).listDirectoryComics();
        mComicsListManager = new DirectoryListingManager(comics, getLibraryDir());
        Log.d("","");
    }

    private void refreshLibrary( boolean finished ) {
        if (!mIsRefreshPlanned || finished) {
            final Runnable updateRunnable = new Runnable() {
                @Override
                public void run() {
                    getComics();
                    mFolderListView.getAdapter().notifyDataSetChanged();
                    mIsRefreshPlanned = false;
                }
            };
            mIsRefreshPlanned = true;
            mFolderListView.postDelayed(updateRunnable, 100);
        }
    }

    public void refreshLibraryDelayed(){
        refreshLibrary(false);
    }

    public void refreshLibraryFinished() {
        refreshLibrary(true);
        setLoading(false);
    }

    private void setLoading(boolean isLoading) {
        if (isLoading) {
            mRefreshLayout.setRefreshing(true);
        } else {
            mRefreshLayout.setRefreshing(false);
            showEmptyMessage(mComicsListManager.getCount() < 1);
        }
    }

    private String getLibraryDir() {
        return getActivity()
                .getSharedPreferences(Constants.SETTINGS_NAME, 0)
                .getString(Constants.SETTINGS_LIBRARY_DIR, "");
    }

    private void showEmptyMessage(boolean show) {
        mEmptyView.setVisibility(show ? View.VISIBLE : View.GONE);
        mRefreshLayout.setEnabled(!show);
    }

    public static class UpdateHandler extends Handler {
        private WeakReference<UpdateHandlerTarget> mOwner;

        public UpdateHandler(UpdateHandlerTarget fragment) {
            mOwner = new WeakReference<>(fragment);
        }

        @Override
        public void handleMessage(Message msg) {
            UpdateHandlerTarget fragment = mOwner.get();
            if (fragment == null) {
                return;
            }

            if (msg.what == Constants.MESSAGE_MEDIA_UPDATED) {
                fragment.refreshLibraryDelayed();
            } else if (msg.what == Constants.MESSAGE_MEDIA_UPDATE_FINISHED) {
                fragment.refreshLibraryFinished();
            }
        }
    }

    private int calculateNumColumns() {
        int deviceWidth = Utils.getDeviceWidth(getActivity());
        int columnWidth = getActivity().getResources().getInteger(R.integer.grid_group_column_width);

        float value = (float) deviceWidth / columnWidth;

        return Math.round(value);
    }

    private final class GroupGridAdapter extends RecyclerView.Adapter {

        public GroupGridAdapter() {
            super();
            // implemented getItemId() below
            setHasStableIds(true);
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int viewType) {
            Context ctx = viewGroup.getContext();

            View view = LayoutInflater.from(ctx)
                    .inflate(R.layout.card_group, viewGroup, false);
            return new GroupViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder viewHolder, int position) {
            GroupViewHolder holder = (GroupViewHolder) viewHolder;
            holder.setup(position);
        }

        @Override
        public int getItemCount() {
            return mComicsListManager.getCount();
        }

        @Override
        public long getItemId(int position) {
            Comic comic = mComicsListManager.getComicAtIndex(position);
            return (long)comic.getId();
        }
    }

    private class GroupViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        ImageView groupImageView;
        TextView tv;

        public GroupViewHolder(View itemView) {
            super(itemView);
            groupImageView = (ImageView) itemView.findViewById(R.id.card_group_imageview);
            tv = (TextView) itemView.findViewById(R.id.comic_group_folder);

            itemView.setClickable(true);
            itemView.setOnClickListener(this);
        }

        public void setup(int position) {
            Comic comic = mComicsListManager.getComicAtIndex(position);
            Uri uri = LocalCoverHandler.getComicCoverUri(comic);
            mPicasso.load(uri).into(groupImageView);

            String dirDisplay = mComicsListManager.getDirectoryDisplayAtIndex(position);
            tv.setText(dirDisplay);
        }

        @Override
        public void onClick(View v) {
            int position = getAbsoluteAdapterPosition();
            String path = mComicsListManager.getDirectoryAtIndex(position);
            LibraryBrowserFragment fragment = LibraryBrowserFragment.create(path);
            ((MainActivity) getActivity()).pushFragment(fragment);
        }
    }

    private final class GridSpacingItemDecoration extends RecyclerView.ItemDecoration {
        private int mSpanCount;
        private int mSpacing;

        public GridSpacingItemDecoration(int spanCount, int spacing) {
            mSpanCount = spanCount;
            mSpacing = spacing;
        }

        @Override
        public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
            int position = parent.getChildAdapterPosition(view);
            int column = position % mSpanCount;

            outRect.left = mSpacing - column * mSpacing / mSpanCount;
            outRect.right = (column + 1) * mSpacing / mSpanCount;

            if (position < mSpanCount) {
                outRect.top = mSpacing;
            }
            outRect.bottom = mSpacing;
        }
    }
}
