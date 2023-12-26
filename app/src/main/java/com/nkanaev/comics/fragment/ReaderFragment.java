package com.nkanaev.comics.fragment;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.*;
import android.view.animation.Animation;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;
import com.nkanaev.comics.BuildConfig;
import com.nkanaev.comics.Constants;
import com.nkanaev.comics.R;
import com.nkanaev.comics.activity.ReaderActivity;
import com.nkanaev.comics.managers.LocalComicHandler;
import com.nkanaev.comics.managers.Utils;
import com.nkanaev.comics.model.Comic;
import com.nkanaev.comics.model.Storage;
import com.nkanaev.comics.parsers.Parser;
import com.nkanaev.comics.parsers.ParserFactory;
import com.nkanaev.comics.view.CircularPathAnimation;
import com.nkanaev.comics.view.ComicViewPager;
import com.nkanaev.comics.view.PageImageView;
import com.squareup.picasso.MemoryPolicy;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.RequestCreator;
import com.squareup.picasso.Target;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.*;


public class ReaderFragment extends Fragment implements View.OnTouchListener {
    public static final String PARAM_HANDLER = "PARAM_HANDLER";
    public static final String PARAM_URI = "PARAM_URI";
    public static final String PARAM_MODE = "PARAM_MODE";

    public static final String STATE_FULLSCREEN = "STATE_FULLSCREEN";
    public static final String STATE_PAGEINFO = "STATE_PAGEINFO";
    public static final String STATE_NEW_COMIC = "STATE_NEW_COMIC";
    public static final String STATE_NEW_COMIC_TITLE = "STATE_NEW_COMIC_TITLE";
    public static final String STATE_PAGE_ROTATIONS = "STATE_PAGE_ROTATIONS";

    private ComicViewPager mViewPager;
    private View mPageNavLayout;
    private SeekBar mPageSeekBar;
    private TextView mPageNavTextView;
    private TextView mPageInfoTextView;
    private View mPageInfoButton;

    private SharedPreferences mPreferences;
    private GestureDetector mGestureDetector;

    private final static HashMap<Integer, Constants.PageViewMode> RESOURCE_VIEW_MODE;
    // default to not showing menu
    private static boolean mIsFullscreen = true;
    // default to not showing page info
    private static boolean mIsPageInfoShown = false;

    private File mFile = null;
    private Uri mUri = null;
    private Constants.PageViewMode mPageViewMode;
    private boolean mIsLeftToRight;

    private Parser mParser;
    private Exception mParserException = null;
    private int mPageCount = 0;
    private Picasso mPicasso;
    private LocalComicHandler mComicHandler;
    private SparseArray<MyTarget> mTargets = new SparseArray<>();
    private HashMap<Integer,Integer> mRotations = new HashMap();

    private Comic mComic = null;
    private Comic mNewComic;
    private int mNewComicTitle;

    public enum Mode {
        MODE_LIBRARY,
        MODE_BROWSER,
        MODE_INTENT
    }

    static {
        RESOURCE_VIEW_MODE = new HashMap<Integer, Constants.PageViewMode>();
        RESOURCE_VIEW_MODE.put(R.id.view_mode_aspect_fill, Constants.PageViewMode.ASPECT_FILL);
        RESOURCE_VIEW_MODE.put(R.id.view_mode_aspect_fit, Constants.PageViewMode.ASPECT_FIT);
        RESOURCE_VIEW_MODE.put(R.id.view_mode_fit_width, Constants.PageViewMode.FIT_WIDTH);
    }

    public static ReaderFragment create(int comicId) {
        ReaderFragment fragment = new ReaderFragment();
        Bundle args = new Bundle();
        args.putSerializable(PARAM_MODE, Mode.MODE_LIBRARY);
        args.putInt(PARAM_HANDLER, comicId);
        fragment.setArguments(args);
        return fragment;
    }

    public static ReaderFragment create(File comicpath) {
        ReaderFragment fragment = new ReaderFragment();
        Bundle args = new Bundle();
        args.putSerializable(PARAM_MODE, Mode.MODE_BROWSER);
        args.putSerializable(PARAM_HANDLER, comicpath);
        fragment.setArguments(args);
        return fragment;
    }

    public static ReaderFragment create(Intent intent) {
        ReaderFragment fragment = new ReaderFragment();
        Bundle args = new Bundle();
        args.putSerializable(PARAM_MODE, Mode.MODE_INTENT);
        args.putParcelable(PARAM_HANDLER, intent);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle bundle = getArguments();
        Mode mode = (Mode) bundle.getSerializable(PARAM_MODE);

        String error = "";
        try {
            if (mode == Mode.MODE_INTENT) {
                Intent intent = (Intent) bundle.getParcelable(PARAM_HANDLER);
                mUri = intent.getData();
                // google files app provides an url encoded file:// url as path,
                // try it, prevents the need to copy the file
                Uri pathUri = Uri.parse(mUri.getLastPathSegment());
                if (pathUri != null && "file".equalsIgnoreCase(pathUri.getScheme())) {
                    mUri = pathUri;
                    intent.setData(mUri);
                }

                String type = intent.getType();
                Log.i("URI", mUri.toString());

                mParser = ParserFactory.create(intent);
            }
            else if (mode == Mode.MODE_LIBRARY) {
                int comicId = bundle.getInt(PARAM_HANDLER);
                mComic = Storage.getStorage(getActivity()).getComic(comicId);
                mFile = mComic.getFile();

                mParser = ParserFactory.create(mFile);
            }
            else if (mode == Mode.MODE_BROWSER) {
                mFile = (File) bundle.getSerializable(PARAM_HANDLER);
                mParser = ParserFactory.create(mFile);
            }
        } catch (Exception e) {
            error = e.getMessage();
        }

        if (mParser == null) {
            Utils.showOKDialog(getActivity(), "No Parser", error);
            mParser = new Parser() {
                @Override
                public void parse() throws IOException {
                }

                @Override
                public int numPages() throws IOException {
                    return 0;
                }

                @Override
                public InputStream getPage(int num) throws IOException {
                    return null;
                }

                @Override
                public Map getPageMetaData(int num) throws IOException {
                    return Collections.emptyMap();
                }

                @Override
                public String getType() {
                    return "dummy";
                }

                @Override
                public void destroy() {
                }
            };
        }

        // start parsing early in background
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    mParser.parse();
                } catch (IOException e) {
                    mParserException = e;
                }
            }
        }).start();

        // setup picasso
        mComicHandler = new LocalComicHandler(mParser);
        mPicasso = new Picasso.Builder(getActivity())
                .loggingEnabled(BuildConfig.DEBUG)
                .indicatorsEnabled(BuildConfig.DEBUG)
                .addRequestHandler(mComicHandler)
                .build();

        mGestureDetector = new GestureDetector(getActivity(), new MyTouchListener());

        mPreferences = getActivity().getSharedPreferences(Constants.SETTINGS_NAME, 0);
        int viewModeInt = mPreferences.getInt(
                Constants.SETTINGS_PAGE_VIEW_MODE,
                Constants.PageViewMode.ASPECT_FIT.native_int);
        mPageViewMode = Constants.PageViewMode.values()[viewModeInt];
        mIsLeftToRight = mPreferences.getBoolean(Constants.SETTINGS_READING_LEFT_TO_RIGHT, true);

        // workaround: extract rar achive
        /*
        if (mParser instanceof RarParser) {
            File cacheDir = new File(getActivity().getExternalCacheDir(), "c");
            if (!cacheDir.exists()) {
                cacheDir.mkdir();
            }
            else {
                for (File f : cacheDir.listFiles()) {
                    f.delete();
                }
            }
            ((RarParser)mParser).setCacheDirectory(cacheDir);
        }
        */

        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.fragment_reader, container, false);
        mPageNavLayout = getActivity().findViewById(R.id.pageNavLayout);

        // setup seekbar
        mPageSeekBar = (SeekBar) mPageNavLayout.findViewById(R.id.pageSeekBar);
        mPageSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    if (mIsLeftToRight)
                        setCurrentPage(progress + 1);
                    else
                        setCurrentPage(mPageSeekBar.getMax() - progress + 1);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                mPicasso.pauseTag(ReaderFragment.this.getActivity());
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                mPicasso.resumeTag(ReaderFragment.this.getActivity());
            }
        });
        updateSeekBar();

        mPageNavTextView = (TextView) mPageNavLayout.findViewById(R.id.pageNavTextView);
        mPageNavTextView.setText(""); // strip dummy text

        // setup page info button
        mPageInfoButton = mPageNavLayout.findViewById(R.id.pageInfoButton);
        mPageInfoTextView = mPageNavLayout.findViewById(R.id.pageInfoTextView);
        mPageInfoTextView.setText(""); // strip dummy text
        setPageInfoShown(mIsPageInfoShown);
        View.OnClickListener ocl = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setPageInfoShown(!mIsPageInfoShown);
            }
        };
        mPageInfoButton.setOnClickListener(ocl);
        mPageInfoTextView.setOnClickListener(ocl);

        // setup view pager, set adapter after parsing in bg thread below
        mViewPager = view.findViewById(R.id.viewPager);
        mViewPager.setOffscreenPageLimit(2);
        mViewPager.setOnTouchListener(this);
        mViewPager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                if (mIsLeftToRight) {
                    setCurrentPage(position + 1);
                } else {
                    setCurrentPage(mPageCount - position);
                }
            }
        });
        mViewPager.setOnSwipeOutListener(new ComicViewPager.OnSwipeOutListener() {
            @Override
            public void onSwipeOutAtStart() {
                if (mIsLeftToRight)
                    hitBeginning();
                else
                    hitEnding();
            }

            @Override
            public void onSwipeOutAtEnd() {
                if (mIsLeftToRight)
                    hitEnding();
                else
                    hitBeginning();
            }
        });

        if (savedInstanceState != null) {
            boolean fullscreen = savedInstanceState.getBoolean(STATE_FULLSCREEN, true);
            setFullscreen(fullscreen);

            boolean infoOn = savedInstanceState.getBoolean(STATE_PAGEINFO, false);
            setPageInfoShown(infoOn);

            int newComicId = savedInstanceState.getInt(STATE_NEW_COMIC);
            if (newComicId != -1) {
                int titleRes = savedInstanceState.getInt(STATE_NEW_COMIC_TITLE);
                confirmSwitch(Storage.getStorage(getActivity()).getComic(newComicId), titleRes);
            }
            // restore previous rotations
            HashMap pageRotations = (HashMap) savedInstanceState.getSerializable(STATE_PAGE_ROTATIONS);
            if (pageRotations!=null)
                mRotations = pageRotations;
        } else {
            setFullscreen(mIsFullscreen);
        }

        // set actionbar title
        String title = "";
        if (mFile != null)
            title += mFile.getName();
        else if (mUri != null)
            title += mUri.getLastPathSegment();
        ((TextView) getActivity().findViewById(R.id.action_bar_title)).setText(title);

        // move parsing into bg thread to return view early
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (mParserException == null)
                    try {
                        mPageCount = mParser.numPages();
                    } catch (IOException e) {
                        Log.e("", "", e);
                    }
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mViewPager.setAdapter(new ComicPagerAdapter());
                        mPageSeekBar.setMax(mPageCount - 1);

                        int curPage = (mComic != null) ? mComic.getCurrentPage() : 0;
                        setCurrentPage(Math.max(curPage, 1));

                        view.findViewById(R.id.progressPlaceholder).setVisibility(View.GONE);
                        mViewPager.setVisibility(View.VISIBLE);

                        TextView titleTextView = getActivity().findViewById(R.id.action_bar_title);
                        titleTextView.append((titleTextView.getText().toString().isEmpty() ? "" : "  ")+"[" + mParser.getType() + "]");
                    }
                });
            }
        }).start();

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.reader, menu);

        switch (mPageViewMode) {
            case ASPECT_FILL:
                menu.findItem(R.id.view_mode_aspect_fill).setChecked(true);
                break;
            case ASPECT_FIT:
                menu.findItem(R.id.view_mode_aspect_fit).setChecked(true);
                break;
            case FIT_WIDTH:
                menu.findItem(R.id.view_mode_fit_width).setChecked(true);
                break;
        }

        if (mIsLeftToRight) {
            menu.findItem(R.id.reading_left_to_right).setChecked(true);
        } else {
            menu.findItem(R.id.reading_right_to_left).setChecked(true);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putBoolean(STATE_FULLSCREEN, isFullscreen());
        outState.putBoolean(STATE_PAGEINFO, (mPageInfoTextView.getVisibility() == View.VISIBLE));
        outState.putInt(STATE_NEW_COMIC, mNewComic != null ? mNewComic.getId() : -1);
        outState.putInt(STATE_NEW_COMIC_TITLE, mNewComic != null ? mNewComicTitle : -1);
        outState.putSerializable(STATE_PAGE_ROTATIONS, mRotations);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onPause() {
        if (mComic != null) {
            mComic.setCurrentPage(getCurrentPage());
        }
        super.onPause();
    }

    @Override
    public void onResume() {
        setFullscreen(isFullscreen());
        super.onResume();
    }

    public void onDestroyView() {
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        mPicasso.shutdown();
        Utils.close(mParser);
    }

    public void onDetach() {
        super.onDetach();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // fixup image position etc. on rotation
        updatePageViews(mViewPager);
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        return mGestureDetector.onTouchEvent(event);
    }

    public int getCurrentPage() {
        if (mViewPager == null)
            return -1;

        if (mIsLeftToRight)
            return mViewPager.getCurrentItem() + 1;
        else
            return mPageCount - mViewPager.getCurrentItem();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        SharedPreferences.Editor editor = mPreferences.edit();
        switch (item.getItemId()) {
            case R.id.view_mode_aspect_fill:
            case R.id.view_mode_aspect_fit:
            case R.id.view_mode_fit_width:
                item.setChecked(true);
                mPageViewMode = RESOURCE_VIEW_MODE.get(item.getItemId());
                editor.putInt(Constants.SETTINGS_PAGE_VIEW_MODE, mPageViewMode.native_int);
                editor.apply();
                updatePageViews(mViewPager);
                break;
            case R.id.reading_left_to_right:
            case R.id.reading_right_to_left:
                item.setChecked(true);
                int page = getCurrentPage();
                mIsLeftToRight = (item.getItemId() == R.id.reading_left_to_right);
                editor.putBoolean(Constants.SETTINGS_READING_LEFT_TO_RIGHT, mIsLeftToRight);
                editor.apply();
                setCurrentPage(page, false);
                mViewPager.getAdapter().notifyDataSetChanged();
                updateSeekBar();
                break;
            case R.id.rotate:
                // add 90 degree to current page rotation
                int pos = getCurrentPage()-1;
                Integer degrees = mRotations.get(pos);
                if (degrees == null)
                    degrees = 0;
                degrees += 90;
                mRotations.put(pos,degrees);
                //apply rotation
                mViewPager.getAdapter().notifyDataSetChanged();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void setCurrentPage(int page) {
        setCurrentPage(page, true);
    }

    int mPrevItem = Integer.MIN_VALUE;

    private void setCurrentPage(int page, boolean animated) {
        int newItem = page - 1;

        if (mIsLeftToRight) {
            mViewPager.setCurrentItem(newItem, animated);
            mPageSeekBar.setProgress(page - 1);
        } else {
            mViewPager.setCurrentItem(mPageCount - page, animated);
            mPageSeekBar.setProgress(mPageCount - page);
        }

        if (mPrevItem == newItem)
            return;
        else
            mPrevItem = newItem;

        String navText = new StringBuilder()
                .append(page).append("/").append(mPageCount)
                .toString();
        mPageNavTextView.setText(navText);

        mPageInfoTextView.setVisibility(View.INVISIBLE);
        updatePageImageInfo();
    }

    private void updatePageImageInfo() {
        if (!mIsPageInfoShown)
            return;

        int pageNum = getCurrentPage() - 1;
        if (pageNum < 0 || pageNum >= mPageCount)
            return;

        // move parser access to bg thread to keep ui responsive
        new Thread(new Runnable() {
            @Override
            public void run() {
                Map<String, String> metadata = null;
                try {
                    metadata = mParser.getPageMetaData(pageNum);
                } catch (IOException e) {
                    Log.e("", "", e);
                }
                String metaText = "";
                if (metadata != null && !metadata.isEmpty()) {
                    String name = metadata.get(Parser.PAGEMETADATA_KEY_NAME);
                    if (name != null)
                        metaText += name;
                    String t = metadata.get(Parser.PAGEMETADATA_KEY_MIME);
                    String w = metadata.get(Parser.PAGEMETADATA_KEY_WIDTH);
                    String h = metadata.get(Parser.PAGEMETADATA_KEY_HEIGHT);
                    if (t != null)
                        metaText += (metaText.isEmpty() ? "" : "\n")
                                + String.valueOf(t) + ", "
                                + String.valueOf(w) + "x" + String.valueOf(h) + "px";
                    // append the rest, ignore the already added from above
                    List<String> ignoredKeys =
                            Arrays.asList(new String[]{
                                    Parser.PAGEMETADATA_KEY_NAME,
                                    Parser.PAGEMETADATA_KEY_MIME,
                                    Parser.PAGEMETADATA_KEY_WIDTH,
                                    Parser.PAGEMETADATA_KEY_HEIGHT});
                    for (Map.Entry<String, String> entry : metadata.entrySet()) {
                        String key = entry.getKey();
                        if (ignoredKeys.contains(key))
                            continue;
                        metaText += (metaText.isEmpty() ? "" : "\n") +
                                key + ": " + entry.getValue();
                    }
                }
                final String text = metaText;
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // just in case the above took too long and user
                        // switched page already, skip the now obsolete write
                        if (getCurrentPage() - 1 != pageNum)
                            return;
                        if (!mIsPageInfoShown || mPageInfoTextView == null)
                            return;

                        mPageInfoTextView.setText(text);
                        mPageInfoTextView.setVisibility(View.VISIBLE);
                    }
                });
            }
        }).start();
    }

    private class ComicPagerAdapter extends PagerAdapter {

         @Override
        public int getItemPosition(Object object) {
            return POSITION_NONE;
        }

        @Override
        public int getCount() {
            return mPageCount;
        }

        @Override
        public boolean isViewFromObject(View view, Object o) {
            return view == o;
        }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            final LayoutInflater inflater = (LayoutInflater) getActivity()
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);

            View layout = inflater.inflate(R.layout.fragment_reader_page, container, false);

            PageImageView pageImageView = (PageImageView) layout.findViewById(R.id.pageImageView);
            if (mPageViewMode == Constants.PageViewMode.ASPECT_FILL)
                pageImageView.setTranslateToRightEdge(!mIsLeftToRight);
            pageImageView.setViewMode(mPageViewMode);
            pageImageView.setOnTouchListener(ReaderFragment.this);

            container.addView(layout);

            MyTarget t = new MyTarget(layout, position);
            loadImage(t);
            mTargets.put(position, t);

            return layout;
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            View layout = (View) object;
            mPicasso.cancelRequest(mTargets.get(position));
            mTargets.delete(position);
            container.removeView(layout);

            ImageView iv = (ImageView) layout.findViewById(R.id.pageImageView);
            Drawable drawable = iv.getDrawable();
            if (drawable instanceof BitmapDrawable) {
                BitmapDrawable bd = (BitmapDrawable) drawable;
                Bitmap bm = bd.getBitmap();
                if (bm != null) {
                    bm.recycle();
                }
            }
        }
    }

    private void loadImage(MyTarget t) {
        int pos;
        if (mIsLeftToRight) {
            pos = t.position;
        } else {
            pos = mViewPager.getAdapter().getCount() - t.position - 1;
        }

        RequestCreator rc = mPicasso.load(mComicHandler.getPageUri(pos))
                //.config(Bitmap.Config.RGB_565)
                .memoryPolicy(MemoryPolicy.NO_STORE)
                .tag(getActivity());
        // disabled as tests on real devices flawlessly load bitmap > texturesize
        // might be needed in future though depending on bug reports
        if (false) {
            // mDblTapScale in PageImageView is 1.5 currently, so set this as our limit
            int max = Utils.getMaxPageSize();

            //max = Utils.glMaxTextureSize();
            rc = rc.resize(max, max).centerInside().onlyScaleDown();
        }
        // apply rotation if any
        Integer degrees = mRotations.get(pos);
        if (degrees != null && degrees != 0)
            rc.rotate(degrees);
        rc.into(t);
    }

    // toggle visibility states
    private enum Show {
        PAGE, PROGRESS, ERROR
    }

    private class MyTarget implements Target, View.OnClickListener {
        public WeakReference<View> mLayout;
        private Animation mProgressAnimation = null;
        public final int position;

        public MyTarget(View layout, int position) {
            mLayout = new WeakReference<>(layout);
            this.position = position;
        }

        private int visibilityFlag(boolean visible) {
            return visible ? View.VISIBLE : View.GONE;
        }

        private void setVisibility(Show v) {
            View layout = mLayout.get();
            if (layout == null)
                return;

            layout.findViewById(R.id.pageImageView).setVisibility(visibilityFlag(v == Show.PAGE));

            boolean showProgress = (v == Show.PROGRESS);
            ImageView progressImage = layout.findViewById(R.id.progressImage);
            if (showProgress) {
                int radius = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24, getResources().getDisplayMetrics());
                mProgressAnimation = new CircularPathAnimation(radius);
                mProgressAnimation.setDuration(2000);
                mProgressAnimation.setRepeatCount(Animation.INFINITE);
                mProgressAnimation.setInterpolator(new FastOutSlowInInterpolator());
                progressImage.startAnimation(mProgressAnimation);
            } else if (mProgressAnimation != null) {
                mProgressAnimation.cancel();
                mProgressAnimation.reset();
            }
            progressImage.setVisibility(visibilityFlag(showProgress));

            boolean showError = (v == Show.ERROR);
            ImageButton errorButton = (ImageButton) layout.findViewById(R.id.errorButton);
            if (showError) {
                errorButton.setOnClickListener(this);
            }
            errorButton.setVisibility(visibilityFlag(showError));
        }

        @Override
        public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from) {
            View layout = mLayout.get();
            if (layout == null)
                return;

            setVisibility(Show.PAGE);
            ImageView iv = (ImageView) layout.findViewById(R.id.pageImageView);
            iv.setImageBitmap(bitmap);

            if (getCurrentPage() - 1 == position)
                updatePageImageInfo();
        }

        @Override
        public void onBitmapFailed(Exception e, Drawable errorDrawable) {
            // TODO: show error stack in textview
            Log.e("onBitmapFailed()", "", e);
            setVisibility(Show.ERROR);
        }

        @Override
        public void onPrepareLoad(Drawable placeHolderDrawable) {
            setVisibility(Show.PROGRESS);
        }

        @Override
        public void onClick(View v) {
            loadImage(this);
        }
    }

    private class MyTouchListener extends GestureDetector.SimpleOnGestureListener {
        /**
         * switch menus and pageseekbar on/off on long press anywhere
         *
         * @param e The initial on down motion event that started the longpress.
         */
        @Override
        public void onLongPress(MotionEvent e) {
            // always switch of menus first
            if (!isFullscreen()) {
                setFullscreen(true);
                return;
            }

            float x = e.getX();
            float y = e.getY();
            float width = (float) mViewPager.getWidth();
            float height = (float) mViewPager.getHeight();

            // hotspot only 60% centered
            int div = 10;
            if (x < width / div * 2 || x > width / div * 8
                    || y < height / div * 2 || y > height / div * 8)
                return;

            boolean fullScreen = !isFullscreen();
            setFullscreen(fullScreen);
        }

        /**
         * single taps on left/ride side switch to prev/next page
         *
         * @param e The down motion event of the single-tap.
         * @return boolean true if the event is consumed, else false
         */
        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            float x = e.getX();

            // tap left side
            if (x < (float) mViewPager.getWidth() / 10 * 3) {
                if (mIsLeftToRight) {
                    if (getCurrentPage() == 1)
                        hitBeginning();
                    else
                        setCurrentPage(getCurrentPage() - 1);
                } else {
                    if (getCurrentPage() == mPageCount)
                        hitEnding();
                    else
                        setCurrentPage(getCurrentPage() + 1);
                }
                return true;
            }
            // tap right side
            else if (x > (float) mViewPager.getWidth() / 10 * 7) {
                if (mIsLeftToRight) {
                    if (getCurrentPage() == mPageCount)
                        hitEnding();
                    else
                        setCurrentPage(getCurrentPage() + 1);
                } else {
                    if (getCurrentPage() == 1)
                        hitBeginning();
                    else
                        setCurrentPage(getCurrentPage() - 1);
                }
                return true;
            }

            // switch of menus if not navigating
            if (!isFullscreen()) {
                setFullscreen(true);
                return true;
            }

            return false;
        }
    }

    private void updatePageViews(ViewGroup parentView) {
        for (int i = 0; i < parentView.getChildCount(); i++) {
            final View child = parentView.getChildAt(i);
            if (child instanceof ViewGroup) {
                updatePageViews((ViewGroup) child);
            } else if (child instanceof PageImageView) {
                PageImageView view = (PageImageView) child;
                if (mPageViewMode == Constants.PageViewMode.ASPECT_FILL)
                    view.setTranslateToRightEdge(!mIsLeftToRight);
                view.setViewMode(mPageViewMode);
            }
        }
    }

    private ActionBar getActionBar() {
        return ((AppCompatActivity) getActivity()).getSupportActionBar();
    }

    private void setPageInfoShown(boolean shown) {
        mIsPageInfoShown = shown;
        if (shown) {
            updatePageImageInfo();
            mPageInfoButton.setVisibility(View.GONE);
            mPageInfoTextView.setVisibility(View.VISIBLE);
            if (mFile != null)
                ((ReaderActivity) getActivity()).setSubTitle(mFile.getAbsolutePath());
            else if (mUri != null) {
                ((ReaderActivity) getActivity()).setSubTitle(mUri.toString());
            }
        } else {
            mPageInfoTextView.setVisibility(View.GONE);
            mPageInfoButton.setVisibility(View.VISIBLE);
            ((ReaderActivity) getActivity()).setSubTitle("");
        }
    }

    private void setFullscreen(boolean fullscreen) {
        ActionBar actionBar = getActionBar();
        View decorView = getActivity().getWindow().getDecorView();
        // the new way (setting flags is deprecated)
        // blend in/out looks worse on Android 12 tho
//       WindowInsetsControllerCompat wic = new WindowInsetsControllerCompat(getActivity().getWindow(), mViewPager);
//        WindowCompat.setDecorFitsSystemWindows(getActivity().getWindow(), false);

        if (fullscreen) {
            if (actionBar != null) actionBar.hide();
            mPageNavLayout.setVisibility(View.INVISIBLE);

//            wic.hide(WindowInsetsCompat.Type.systemBars());

            int flag = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_FULLSCREEN;
            if (Utils.isKitKatOrLater()) {
                flag |= View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
                flag |= View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
                flag |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            }
            decorView.setSystemUiVisibility(flag);

            // replaced by value in ReaderTheme style
            /*
            // allow full screen over display cutouts/holes (since Android 9)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                Window w = getActivity().getWindow();
                WindowManager.LayoutParams layoutParams = w.getAttributes();
                layoutParams.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
                w.setAttributes(layoutParams);
            }
            */

        } else {
//            wic.show(WindowInsetsCompat.Type.systemBars());

            int flag = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
            if (Utils.isKitKatOrLater()) {
                flag |= View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
                flag |= View.SYSTEM_UI_FLAG_VISIBLE;
            }
            decorView.setSystemUiVisibility(flag);

            mPageSeekBar.setMax(mPageCount - 1);
            if (actionBar != null) actionBar.show();
            mPageNavLayout.setVisibility(View.VISIBLE);

            // WORKAROUND:
            // status bar & navigation bar background won't show, being transparent,
            // at times. reproducible on Android 9 (Lineage 16)
            if (Utils.isLollipopOrLater()) {
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        Window w = getActivity().getWindow();
                        w.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
                        w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
                    }
                }, 100);
            }
        }

        mIsFullscreen = fullscreen;
    }

    private boolean isFullscreen() {
        return mIsFullscreen;
    }

    private void hitBeginning() {
        if (mComic != null) {
            Comic c = Storage.getStorage(getActivity()).getPrevComic(mComic);
            confirmSwitch(c, R.string.switch_prev_comic);
        }
    }

    private void hitEnding() {
        if (mComic != null) {
            Comic c = Storage.getStorage(getActivity()).getNextComic(mComic);
            confirmSwitch(c, R.string.switch_next_comic);
        }
    }

    private void confirmSwitch(Comic newComic, int titleRes) {
        if (newComic == null)
            return;

        mNewComic = newComic;
        mNewComicTitle = titleRes;

        AlertDialog dialog = new AlertDialog.Builder(getActivity(), R.style.AppCompatAlertDialogStyle)
                .setTitle(titleRes)
                .setMessage(newComic.getFile().getName())
                .setPositiveButton(R.string.alert_action_positive, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        ReaderActivity activity = (ReaderActivity) getActivity();
                        activity.setFragment(ReaderFragment.create(mNewComic.getId()));
                    }
                })
                .setNegativeButton(R.string.alert_action_negative, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mNewComic = null;
                    }
                })
                .create();
        // apply systembars hidden/shown status to dialog's window from activity's window
        // fixes "statusbar is and stays enabled when dialog is shown" on Android9
        Window dialogWindow = dialog.getWindow();
        dialogWindow.setFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        dialogWindow.getDecorView().setSystemUiVisibility(getActivity().getWindow().getDecorView().getSystemUiVisibility());
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface di) {
                //Clear the not focusable flag from the window
                dialogWindow.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
                //Update the WindowManager with the new attributes (no nicer way I know of to do this)..
                WindowManager wm = (WindowManager) getActivity().getSystemService(Context.WINDOW_SERVICE);
                wm.updateViewLayout(dialogWindow.getDecorView(), dialogWindow.getAttributes());
            }
        });
        dialog.show();
    }

    private void updateSeekBar() {
        int seekRes = (mIsLeftToRight)
                ? R.drawable.reader_nav_progress
                : R.drawable.reader_nav_progress_inverse;

        Drawable d = getActivity().getResources().getDrawable(seekRes);
        Rect bounds = mPageSeekBar.getProgressDrawable().getBounds();
        mPageSeekBar.setProgressDrawable(d);
        mPageSeekBar.getProgressDrawable().setBounds(bounds);
    }

}
