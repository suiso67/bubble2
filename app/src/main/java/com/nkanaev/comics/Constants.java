package com.nkanaev.comics;

public class Constants {
    public static final int COVER_THUMBNAIL_HEIGHT = 260;
    public static final int COVER_THUMBNAIL_WIDTH = 260;

    public static final int MAX_RECENT_COUNT = 5;

    public static final String SETTINGS_NAME = "SETTINGS_COMICS";
    public static final String SETTINGS_LIBRARY_DIR = "SETTINGS_LIBRARY_DIR";

    public static final String SETTINGS_PAGE_VIEW_MODE = "SETTINGS_PAGE_VIEW_MODE";
    public static final String SETTINGS_READING_LEFT_TO_RIGHT = "SETTINGS_READING_LEFT_TO_RIGHT";

    public static final int MESSAGE_MEDIA_UPDATE_FINISHED = 0;
    public static final int MESSAGE_MEDIA_UPDATED = 1;

    public enum PageViewMode {
        ASPECT_FILL(0),
        ASPECT_FIT(1),
        FIT_WIDTH(2);

        PageViewMode(int n) {
            native_int = n;
        }
        public final int native_int;
    }
}
