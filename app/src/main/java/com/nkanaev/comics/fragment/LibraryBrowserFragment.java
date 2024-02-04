package com.nkanaev.comics.fragment;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.AlignmentSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.*;
import android.widget.*;
import androidx.annotation.ColorInt;
import androidx.annotation.StyleRes;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.appcompat.view.menu.MenuItemImpl;
import androidx.appcompat.widget.SearchView;
import androidx.core.content.ContextCompat;
import androidx.core.view.MenuItemCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.nkanaev.comics.BuildConfig;
import com.nkanaev.comics.Constants;
import com.nkanaev.comics.R;
import com.nkanaev.comics.activity.MainActivity;
import com.nkanaev.comics.activity.ReaderActivity;
import com.nkanaev.comics.managers.IgnoreCaseComparator;
import com.nkanaev.comics.managers.LocalCoverHandler;
import com.nkanaev.comics.managers.Scanner;
import com.nkanaev.comics.managers.Utils;
import com.nkanaev.comics.model.Comic;
import com.nkanaev.comics.model.Storage;
import com.nkanaev.comics.view.PreCachingGridLayoutManager;
import com.squareup.picasso.Picasso;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;
import java.util.*;

public class LibraryBrowserFragment extends Fragment
        implements SearchView.OnQueryTextListener,
        SwipeRefreshLayout.OnRefreshListener,
        UpdateHandlerTarget {
    public static final String PARAM_PATH = "browserCurrentPath";

    final int ITEM_VIEW_TYPE_COMIC = -1;
    final int ITEM_VIEW_TYPE_HEADER_RECENT = -2;
    final int ITEM_VIEW_TYPE_HEADER_ALL = -3;

    final int NUM_HEADERS = 2;

    private List<Comic> mComics = new ArrayList<>();
    private List<Comic> mAllItems = new ArrayList<>();
    private List<Comic> mRecentItems = new ArrayList<>();

    private String mPath;
    private Picasso mPicasso;
    private String mFilterSearch = "";
    private int mFilterRead = R.id.menu_browser_filter_all;
    private int mSort = R.id.sort_name_asc;

    private RecyclerView mComicListView;
    private SwipeRefreshLayout mRefreshLayout;
    private Handler mUpdateHandler = new LibraryFragment.UpdateHandler(this);
    private MenuItem mRefreshItem;
    private Long mCacheStamp = Long.valueOf(System.currentTimeMillis());
    private HashMap<Uri, Long> mCache = new HashMap();

    public static LibraryBrowserFragment create(String path) {
        LibraryBrowserFragment fragment = new LibraryBrowserFragment();
        Bundle args = new Bundle();
        args.putString(PARAM_PATH, path);
        fragment.setArguments(args);
        return fragment;
    }

    public LibraryBrowserFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mPath = getArguments().getString(PARAM_PATH);
        getComics();
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.fragment_librarybrowser, container, false);

        final int numColumns = calculateNumColumns();
        int spacing = (int) getResources().getDimension(R.dimen.grid_margin);

        PreCachingGridLayoutManager layoutManager = new PreCachingGridLayoutManager(getActivity(), numColumns);
        layoutManager.setSpanSizeLookup(createSpanSizeLookup());
        int height = Utils.getDeviceHeightPixels();
        layoutManager.setExtraLayoutSpace(height*2);

        mComicListView = (RecyclerView) view.findViewById(R.id.library_grid);
        // some preformance optimizations
        mComicListView.setHasFixedSize(true);
        // raise default cache values (number of cards) from a very low DEFAULT_CACHE_SIZE=2
        mComicListView.setItemViewCacheSize(Math.max(4 * numColumns,40));
        //mComicListView.getRecycledViewPool().setMaxRecycledViews(ITEM_VIEW_TYPE_COMIC,20);

        mComicListView.setLayoutManager(layoutManager);
        mComicListView.setAdapter(new ComicGridAdapter());
        mComicListView.addItemDecoration(new GridSpacingItemDecoration(numColumns, spacing));

        mRefreshLayout = (SwipeRefreshLayout) view.findViewById(R.id.fragmentLibraryBrowserRefreshLayout);
        mRefreshLayout.setColorSchemeResources(R.color.primary);
        mRefreshLayout.setOnRefreshListener(this);
        mRefreshLayout.setEnabled(true);

        File path = new File(getArguments().getString(PARAM_PATH));
        getActivity().setTitle(path.getName());
        ((MainActivity) getActivity()).setSubTitle(Utils.appendSlashIfMissing(path.getPath()));
        mPicasso = ((MainActivity) getActivity()).getPicasso();

        return view;
    }

    @Override
    public void onResume() {
        getComics();
        Scanner.getInstance().addUpdateHandler(mUpdateHandler);
        setLoading(Scanner.getInstance().isRunning());
        super.onResume();
    }

    @Override
    public void onPause() {
        Scanner.getInstance().removeUpdateHandler(mUpdateHandler);
        setLoading(false);
        super.onPause();
    }

    private Menu mFilterMenu, mSortMenu;

    @Override
    public void onPrepareOptionsMenu(Menu menu){
        super.onPrepareOptionsMenu(menu);
    }

    SearchView mSearchView;

    @SuppressLint("RestrictedApi")
    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        menu.clear();
        inflater.inflate(R.menu.browser, menu);
        // hack to enable icons in overflow menu
        if(menu instanceof MenuBuilder){
            ((MenuBuilder)menu).setOptionalIconsVisible(true);
        }

        MenuItem searchItem = menu.findItem(R.id.search);
        mSearchView = (SearchView) MenuItemCompat.getActionView(searchItem);
        mSearchView.setOnQueryTextListener(this);

        MenuItem filterItem = menu.findItem(R.id.menu_browser_filter);
        mFilterMenu = filterItem.getSubMenu();

        // fixup menu formatting
        updateColors();

        super.onCreateOptionsMenu(menu, inflater);

        // memorize refresh item
        mRefreshItem = menu.findItem(R.id.menu_browser_refresh);
        // switch refresh icon
        setLoading(Scanner.getInstance().isRunning());

        // align header title of generated sub menu right
        SpannableString title = new SpannableString(getResources().getString(R.string.menu_browser_filter));
        title.setSpan(new AlignmentSpan.Standard(Layout.Alignment.ALIGN_OPPOSITE),0,title.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        menu.findItem(R.id.menu_browser_filter).getSubMenu().setHeaderTitle(title);
    }

    @SuppressLint("RestrictedApi")
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item == null)
            return false;

        switch (item.getItemId()) {
            case R.id.menu_browser_filter_all:
            case R.id.menu_browser_filter_read:
            case R.id.menu_browser_filter_unread:
            case R.id.menu_browser_filter_unfinished:
            case R.id.menu_browser_filter_reading:
                item.setChecked(true);
                // TODO: workaround
                //  should probably done with xml styles properly
                //  couldn't find out how though
                updateColors();
                mFilterRead = item.getItemId();
                filterContent();
                libraryChanged();
                return true;
            case R.id.menu_browser_refresh:
                // if running, stop is requested
                if (Scanner.getInstance().isRunning()) {
                    setLoading(false);
                    Scanner.getInstance().stop();
                } else {
                    onRefresh();
                }
                return true;
            case R.id.sort:
                // apparently you need to implement custom layout submenus yourself
                View popupView = getLayoutInflater().inflate(R.layout.layout_librarybrowser_sort, null);
                // show header conditionally
                if (((MenuItemImpl)item).isActionButton()) {
                    popupView.findViewById(R.id.sort_header).setVisibility(View.GONE);
                    popupView.findViewById(R.id.sort_header_divider).setVisibility(View.GONE);
                }
                // creation time needs java.nio only avail on API26+
                if (!Utils.isOreoOrLater()) {
                    popupView.findViewById(R.id.sort_creation).setVisibility(View.GONE);
                    popupView.findViewById(R.id.sort_creation_divider).setVisibility(View.GONE);
                }

                @StyleRes int theme = ((MainActivity) getActivity()).getToolbar().getPopupTheme();
                @ColorInt int normal = Utils.getThemeColor(androidx.appcompat.R.attr.colorControlNormal, theme);
                @ColorInt int active = Utils.getThemeColor(androidx.appcompat.R.attr.colorControlActivated, theme);

                PopupWindow popupWindow = new PopupWindow(popupView,RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT, true);
                // weirdly needed on preAPI21 to dismiss on tap outside
                popupWindow.setBackgroundDrawable(new ColorDrawable(androidx.appcompat.R.attr.colorPrimary));

                int[] ids = new int[]{
                        R.id.sort_modified_asc, R.id.sort_modified_desc,
                        R.id.sort_creation_asc, R.id.sort_creation_desc,
                        R.id.sort_name_asc, R.id.sort_name_desc,
                        R.id.sort_size_asc, R.id.sort_size_desc,
                        R.id.sort_pages_asc, R.id.sort_pages_desc,
                        R.id.sort_pages_read_asc, R.id.sort_pages_read_desc,
                        R.id.sort_pages_left_asc, R.id.sort_pages_left_desc,
                        R.id.sort_access_asc, R.id.sort_access_desc,
                };
                for (int id: ids ) {
                    ImageView v = popupView.findViewById(id);
                    int tint = id == mSort ? active : normal;
                    ImageViewCompat.setImageTintList(v, ColorStateList.valueOf(tint));
                    v.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            onSortItemSelected(id);
                            popupWindow.dismiss();
                        }
                    });
                }

                float dp = getResources().getDisplayMetrics().density;
                // place popup window top right
                int xOffset, yOffset;
                // lil space on the right side
                xOffset=Math.round(4*dp);
                // below status bar
                yOffset=Math.round(30*dp);
                // API21+ place submenu popups below status+actionbar
                if (Utils.isLollipopOrLater()) {
                    yOffset = Math.round(17 * dp) + ((MainActivity) getActivity()).getToolbar().getHeight();
                    popupWindow.setElevation(16);
                }
                // show at location
                popupWindow.showAtLocation(getActivity().getWindow().getDecorView(),Gravity.TOP|Gravity.RIGHT,xOffset,yOffset);
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void onSortItemSelected(int id) {
        if (false && BuildConfig.DEBUG)
            Toast.makeText(
                    getActivity(),
                    "sort "+id,
                    Toast.LENGTH_SHORT).show();
        mSort = id;
        sortContent();
        libraryChanged();
    }

    private void updateColors() {
        if (false) return;

        // fetch styling
        @StyleRes int theme = ((MainActivity) getActivity()).getToolbar().getPopupTheme();
        @ColorInt int normal = Utils.getThemeColor(androidx.appcompat.R.attr.colorControlNormal, theme);
        @ColorInt int active = Utils.getThemeColor(androidx.appcompat.R.attr.colorControlActivated, theme);

        for (int i = 0; mFilterMenu != null && i < mFilterMenu.size(); i++) {
            MenuItem item = mFilterMenu.getItem(i);
            if (item.isChecked())
                styleItem(item, active, true);
            else
                styleItem(item, normal, false);
        }

        for (int i = 0; mSortMenu != null && i < mSortMenu.size(); i++) {
            MenuItem item = mSortMenu.getItem(i);
            if (item.isChecked())
                styleItem(item, active, true);
            else
                styleItem(item, normal, false);
        }
    }

    // this is a workaround, couldn't find a way to style popup menu item text color/type
    // depending on selection state
    private void styleItem(MenuItem item, @ColorInt int colorInt, boolean bold) {
        if (item == null) return;

        // reset formatting
        CharSequence text = item.getTitle().toString();
        SpannableString s = new SpannableString(text);
        // style away
        s.setSpan(new ForegroundColorSpan(colorInt), 0, s.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        s.setSpan(new StyleSpan(bold ? Typeface.BOLD : Typeface.NORMAL), 0, s.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        item.setTitle(s);
    }

    @Override
    public boolean onQueryTextChange(String s) {
        mFilterSearch = s;
        filterContent();
        libraryChanged();
        return true;
    }

    @Override
    public boolean onQueryTextSubmit(String s) {
        return true;
    }

    public void openComic(Comic comic) {
        if (!comic.getFile().exists()) {
            Toast.makeText(
                    getActivity(),
                    R.string.warning_missing_file,
                    Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(getActivity(), ReaderActivity.class);
        intent.putExtra(ReaderFragment.PARAM_HANDLER, comic.getId());
        intent.putExtra(ReaderFragment.PARAM_MODE, ReaderFragment.Mode.MODE_LIBRARY);
        startActivity(intent);
    }

    private void getComics() {
        mCacheStamp = Long.valueOf(System.currentTimeMillis());
        mComics = Storage.getStorage(getActivity()).listComics(mPath);
        findRecents();
        filterContent();
        sortContent();
        libraryChanged();
    }

    private void findRecents() {
        mRecentItems.clear();

        for (Comic c : mComics) {
            if (c.getUpdatedAt() > 0) {
                mRecentItems.add(c);
            }
        }

        if (mRecentItems.size() > 0) {
            Collections.sort(mRecentItems, new Comparator<Comic>() {
                @Override
                public int compare(Comic lhs, Comic rhs) {
                    return Long.compare(rhs.getUpdatedAt(),lhs.getUpdatedAt());
                }
            });
        }

        if (mRecentItems.size() > Constants.MAX_RECENT_COUNT) {
            mRecentItems
                    .subList(Constants.MAX_RECENT_COUNT, mRecentItems.size())
                    .clear();
        }
    }

    private void filterContent() {
        mAllItems.clear();

        for (Comic c : mComics) {
            if (mFilterSearch.length() > 0 && !c.getFile().getName().contains(mFilterSearch))
                continue;
            if (mFilterRead != R.id.menu_browser_filter_all) {
                if (mFilterRead == R.id.menu_browser_filter_read && c.getCurrentPage() != c.getTotalPages())
                    continue;
                if (mFilterRead == R.id.menu_browser_filter_unread && c.getCurrentPage() != 0)
                    continue;
                if (mFilterRead == R.id.menu_browser_filter_unfinished && c.getCurrentPage() == c.getTotalPages())
                    continue;
                if (mFilterRead == R.id.menu_browser_filter_reading &&
                        (c.getCurrentPage() == 0 || c.getCurrentPage() == c.getTotalPages()))
                    continue;
            }
            mAllItems.add(c);
        }
    }

    private void sortContent(){
        if (mAllItems == null || mAllItems.isEmpty())
            return;

        Comparator comparator;
        switch (mSort){
            case R.id.sort_name_desc:
                comparator = new IgnoreCaseComparator.Reverse() {
                    @Override
                    public String stringValue(Object o) {
                        return ((Comic) o).getFile().getName();
                    }
                };
                break;
            case R.id.sort_size_asc:
                comparator = new Comparator<Comic>() {
                    @Override
                    public int compare(Comic a, Comic b) {
                        long aSize = getSize(a.getFile());
                        long bSize = getSize(b.getFile());
                        long diff = aSize - bSize;
                        return diff < 0 ? -1 : (diff > 0 ? 1 : 0);
                    }

                    private long getSize(File f) {
                        if (f == null)
                            return 0;
                        else if (f.isDirectory())
                            return Utils.getFolderSize(f, false);

                        return f.length();
                    }
                };
                break;
            case R.id.sort_size_desc:
                comparator = new Comparator<Comic>() {
                    public int compare(Comic a, Comic b) {
                        long aSize = getSize(b.getFile());
                        long bSize = getSize(a.getFile());
                        long diff = aSize - bSize;
                        return diff < 0 ? -1 : (diff > 0 ? 1 : 0);
                    }

                    private long getSize(File f) {
                        if (f == null)
                            return 0;
                        else if (f.isDirectory())
                            return Utils.getFolderSize(f, false);

                        return f.length();
                    }
                };
                break;
            case R.id.sort_creation_asc:
                comparator = new Comparator<Comic>() {
                    @Override
                    public int compare(Comic a, Comic b) {
                        long aTime = creationTime(a.getFile());
                        long bTime = creationTime(b.getFile());
                        return Long.compare(aTime, bTime);
                    }

                    private long creationTime(File f){
                        try {
                            FileTime creationTime = (FileTime) Files.getAttribute(f.toPath(), "creationTime");
                            return creationTime.toMillis();
                        } catch (IOException ex) {
                            return 0;
                        }
                    }
                };
                break;
            case R.id.sort_creation_desc:
                comparator = new Comparator<Comic>() {
                    @Override
                    public int compare(Comic a, Comic b) {
                        long aTime = creationTime(b.getFile());
                        long bTime = creationTime(a.getFile());
                        return Long.compare(aTime, bTime);
                    }

                    private long creationTime(File f){
                        try {
                            FileTime creationTime = (FileTime) Files.getAttribute(f.toPath(), "creationTime");
                            return creationTime.toMillis();
                        } catch (IOException ex) {
                            return 0;
                        }
                    }
                };
                break;
            case R.id.sort_modified_asc:
                comparator = new Comparator<Comic>() {
                    @Override
                    public int compare(Comic a, Comic b) {
                        long aTime = a.getFile().lastModified();
                        long bTime = b.getFile().lastModified();
                        return Long.compare(aTime, bTime);
                    }
                };
                break;
            case R.id.sort_modified_desc:
                comparator = new Comparator<Comic>() {
                    @Override
                    public int compare(Comic a, Comic b) {
                        long aTime = b.getFile().lastModified();
                        long bTime = a.getFile().lastModified();
                        return Long.compare(aTime, bTime);
                    }
                };
                break;
            case R.id.sort_pages_asc:
                comparator = new Comparator<Comic>() {
                    @Override
                    public int compare(Comic a, Comic b) {
                        return a.getTotalPages() - b.getTotalPages();
                    }
                };
                break;
            case R.id.sort_pages_desc:
                comparator = new Comparator<Comic>() {
                    @Override
                    public int compare(Comic a, Comic b) {
                        return b.getTotalPages() - a.getTotalPages();
                    }
                };
                break;
            case R.id.sort_pages_read_asc:
                comparator = new Comparator<Comic>() {
                    @Override
                    public int compare(Comic a, Comic b) {
                        return a.getCurrentPage() - b.getCurrentPage();
                    }
                };
                break;
            case R.id.sort_pages_read_desc:
                comparator = new Comparator<Comic>() {
                    @Override
                    public int compare(Comic a, Comic b) {
                        return b.getCurrentPage() - a.getCurrentPage();
                    }
                };
                break;
            case R.id.sort_pages_left_asc:
                comparator = new Comparator<Comic>() {
                    @Override
                    public int compare(Comic a, Comic b) {
                        return (a.getTotalPages()-a.getCurrentPage()) -
                                (b.getTotalPages()-b.getCurrentPage());
                    }
                };
                break;
            case R.id.sort_pages_left_desc:
                comparator = new Comparator<Comic>() {
                    @Override
                    public int compare(Comic a, Comic b) {
                        return (b.getTotalPages()-b.getCurrentPage()) -
                                (a.getTotalPages()-a.getCurrentPage());
                    }
                };
                break;
            case R.id.sort_access_asc:
                comparator = new Comparator<Comic>() {
                    @Override
                    public int compare(Comic a, Comic b) {
                        return Long.compare(a.getUpdatedAt(),b.getUpdatedAt());
                    }
                };
                break;
            case R.id.sort_access_desc:
                comparator = new Comparator<Comic>() {
                    @Override
                    public int compare(Comic a, Comic b) {
                        return Long.compare(b.getUpdatedAt(),a.getUpdatedAt());
                    }
                };
                break;
            default:
                comparator = new IgnoreCaseComparator() {
                    @Override
                    public String stringValue(Object o) {
                        return ((Comic) o).getFile().getName();
                    }
                };
                break;
        }

        Collections.sort(mAllItems, comparator);
    }

    private Comic getComicAtPosition(int position) {
        Comic comic;
        if (hasRecent()) {
            if (position > 0 && position < mRecentItems.size() + 1)
                comic = mRecentItems.get(position - 1);
            else
                comic = mAllItems.get(position - mRecentItems.size() - NUM_HEADERS);
        } else {
            comic = mAllItems.get(position);
        }
        return comic;
    }

    private int getItemViewTypeAtPosition(int position) {
        if (hasRecent()) {
            if (position == 0)
                return ITEM_VIEW_TYPE_HEADER_RECENT;
            else if (position == mRecentItems.size() + 1)
                return ITEM_VIEW_TYPE_HEADER_ALL;
        }
        return ITEM_VIEW_TYPE_COMIC;
    }

    private boolean hasRecent() {
        return mFilterSearch.length() == 0
                && mFilterRead == R.id.menu_browser_filter_all
                && mRecentItems.size() > 0;
    }

    private int calculateNumColumns() {
        int deviceWidth = Utils.getDeviceWidth(getActivity());
        int columnWidth = getActivity().getResources().getInteger(R.integer.grid_comic_column_width);

        return Math.round((float) deviceWidth / columnWidth);
    }

    private GridLayoutManager.SpanSizeLookup createSpanSizeLookup() {
        final int numColumns = calculateNumColumns();

        return new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                if (getItemViewTypeAtPosition(position) == ITEM_VIEW_TYPE_COMIC)
                    return 1;
                return numColumns;
            }
        };
    }

    @Override
    public void onRefresh() {
       setLoading(true);
       Scanner.getInstance().forceScanLibrary(new File(mPath));
    }

    private void libraryChanged(){
        // we modified items list, notify the grid adapter accordingly
        if (mComicListView == null) return;

        mComicListView.getAdapter().notifyDataSetChanged();
    }

    public void refreshLibraryDelayed() {
    }

    public void refreshLibraryFinished() {
        getComics();
        setLoading(false);
    }

    private void setLoading(boolean isLoading) {
        if (isLoading) {
            mRefreshLayout.setRefreshing(true);
            if (mRefreshItem != null)
                mRefreshItem.setIcon(ContextCompat.getDrawable(getContext(), R.drawable.ic_refresh_stop_24));
        } else {
            if (mRefreshItem != null)
                mRefreshItem.setIcon(ContextCompat.getDrawable(getContext(), R.drawable.ic_refresh_24));
            mRefreshLayout.setRefreshing(false);
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

            if (hasRecent()) {
                // those are headers
                if (position == 0 || position == mRecentItems.size() + 1)
                    return;

                if (position > 0 && position < mRecentItems.size() + 1) {
                    position -= 1;
                } else {
                    position -= (NUM_HEADERS + mRecentItems.size());
                }
            }

            int column = position % mSpanCount;

            outRect.left = mSpacing - column * mSpacing / mSpanCount;
            outRect.right = (column + 1) * mSpacing / mSpanCount;

            if (position < mSpanCount) {
                outRect.top = mSpacing;
            }
            outRect.bottom = mSpacing;
        }
    }


    private final class ComicGridAdapter extends RecyclerView.Adapter {

        public ComicGridAdapter() {
            super();
            // implemented getItemId() below
            setHasStableIds(true);
        }

        @Override
        public int getItemCount() {
            if (hasRecent()) {
                return mAllItems.size() + mRecentItems.size() + NUM_HEADERS;
            }
            return mAllItems.size();
        }

        @Override
        public int getItemViewType(int position) {
            return getItemViewTypeAtPosition(position);
        }

        @Override
        public long getItemId(int position) {
            int type = getItemViewTypeAtPosition(position);
            if (type == ITEM_VIEW_TYPE_COMIC) {
                Comic comic = getComicAtPosition(position);
                return comic.getId();
            }
            return type;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
            Context ctx = viewGroup.getContext();

            if (i == ITEM_VIEW_TYPE_HEADER_RECENT) {
                TextView view = (TextView) LayoutInflater.from(ctx)
                        .inflate(R.layout.header_library, viewGroup, false);
                view.setText(R.string.library_header_recent);

                int spacing = (int) getResources().getDimension(R.dimen.grid_margin);
                RecyclerView.LayoutParams lp = (RecyclerView.LayoutParams) view.getLayoutParams();
                lp.setMargins(0, spacing, 0, 0);

                return new HeaderViewHolder(view);
            } else if (i == ITEM_VIEW_TYPE_HEADER_ALL) {
                TextView view = (TextView) LayoutInflater.from(ctx)
                        .inflate(R.layout.header_library, viewGroup, false);
                view.setText(R.string.library_header_all);

                return new HeaderViewHolder(view);
            }

            View view = LayoutInflater.from(ctx)
                    .inflate(R.layout.card_comic, viewGroup, false);
            return new ComicViewHolder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder viewHolder, int i) {
            if (viewHolder.getItemViewType() == ITEM_VIEW_TYPE_COMIC) {
                Comic comic = getComicAtPosition(i);
                ComicViewHolder holder = (ComicViewHolder) viewHolder;
                holder.setupComic(comic);
            }
        }

    }

    private class HeaderViewHolder extends RecyclerView.ViewHolder {
        public HeaderViewHolder(View itemView) {
            super(itemView);
        }

        public void setTitle(int titleRes) {
            ((TextView) itemView).setText(titleRes);
        }
    }

    private class ComicViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        private ImageView mCoverView;
        private TextView mTitleTextView;
        private TextView mPagesTextView;

        public ComicViewHolder(View itemView) {
            super(itemView);
            mCoverView = (ImageView) itemView.findViewById(R.id.comicImageView);
            mTitleTextView = (TextView) itemView.findViewById(R.id.comicTitleTextView);
            mPagesTextView = (TextView) itemView.findViewById(R.id.comicPagerTextView);

            itemView.setClickable(true);
            itemView.setOnClickListener(this);
        }

        public void setupComic(Comic comic) {
            mTitleTextView.setText(comic.getFile().getName());
            mPagesTextView.setText(Integer.toString(comic.getCurrentPage()) + '/' + Integer.toString(comic.getTotalPages()));

            Uri uri = LocalCoverHandler.getComicCoverUri(comic);
            Long lastCacheStamp = mCache.get(uri);
            if (lastCacheStamp != null && !lastCacheStamp.equals(mCacheStamp))
                mPicasso.invalidate(uri);
            mPicasso.load(uri)
                    .into(mCoverView);
            mCache.put(uri, mCacheStamp);
        }

        @Override
        public void onClick(View v) {
            int i = getAdapterPosition();
            Comic comic = getComicAtPosition(i);
            openComic(comic);
        }
    }
}
