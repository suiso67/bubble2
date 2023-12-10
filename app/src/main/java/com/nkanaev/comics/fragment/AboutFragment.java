package com.nkanaev.comics.fragment;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Bundle;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.AlignmentSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.fragment.app.Fragment;
import com.nkanaev.comics.R;
import com.nkanaev.comics.activity.MainActivity;
import net.sf.sevenzipjbinding.SevenZip;

public class AboutFragment extends Fragment implements View.OnClickListener {
    static private class LibraryDescription {
        public final CharSequence name;
        public final CharSequence description;
        public final CharSequence license;
        public final CharSequence owner;
        public final CharSequence link;

        LibraryDescription(CharSequence name, CharSequence description, CharSequence license, CharSequence owner, CharSequence link) {
            this.name = name;
            this.description = description;
            this.license = license;
            this.owner = owner;
            this.link = link;
        }
    }

    private static CharSequence lib7zVersions() {
        SevenZip.Version version = SevenZip.getSevenZipVersion();
        CharSequence out = "\n" +
                "7-zip version: " + version.version + ", " + version.date + ", " + version.copyright + "\n" +
                "7-Zip-JBinding version: " + SevenZip.getSevenZipJBindingVersion() + "\n" +
                "Native library initialized: " + SevenZip.isInitializedSuccessfully();
        final SpannableString s = new SpannableString(out);
        s.setSpan(new AlignmentSpan.Standard(Layout.Alignment.ALIGN_OPPOSITE),0,s.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        return s;
    }

    private LibraryDescription[] mDescriptions = new LibraryDescription[]{
            new LibraryDescription(
                    "Picasso",
                    "A powerful image downloading and caching library for Android",
                    "Apache Version 2.0",
                    "Square",
                    "https://github.com/square/picasso"
            ),
            new LibraryDescription(
                    "Junrar",
                    "Plain java unrar util",
                    "Unrar license",
                    "Edmund Wagner",
                    "https://github.com/edmund-wagner/junrar"
            ),
            new LibraryDescription(
                    "Apache Commons Compress",
                    "Defines an API for working with tar, zip and bzip2 files",
                    "Apache Version 2.0",
                    "Apache Software Foundation",
                    "https://commons.apache.org/proper/commons-compress/"
            ),
            new LibraryDescription(
                    "XZ Utils",
                    "XZ Utils is free general-purpose data compression software with a high compression ratio",
                    "Public Domain",
                    "Tukaani Developers",
                    "http://tukaani.org/xz/java.html"
            ),
            new LibraryDescription(
                    "7-Zip-JBinding-4Android",
                    TextUtils.concat("Android library version of 7-Zip-JBinding java wrapper.", lib7zVersions()),
                    "GNU LGPL 2.1 or later + unRAR restriction",
                    "Igor Pavlov, Boris Brodski, Fredrik Claesson",
                    "https://github.com/omicronapps/7-Zip-JBinding-4Android"
            ),
            new LibraryDescription(
                    "Zstd-jni",
                    "JNI bindings to Zstd Library",
                    "BSD license",
                    "Luben Karavelov",
                    "https://github.com/luben/zstd-jni"
            ),
            new LibraryDescription(
                    "Brotli",
                    "Generic-purpose lossless compression algorithm",
                    "MIT license",
                    "Google",
                    "https://github.com/google/brotli"
            ),
            new LibraryDescription(
                    "JP2 for Android",
                    "Open-source JPEG-2000 image encoder/decoder for Android based on OpenJPEG",
                    "BSD (2-clause) license",
                    "ThalesGroup",
                    "https://github.com/ThalesGroup/JP2ForAndroid"
            ),
            new LibraryDescription(
                    "Natural Sorting for Java",
                    "Perform 'natural order' comparisons of strings in Java.",
                    "MIT license",
                    "Jagoba Gascón",
                    "https://github.com/jagobagascon/Natural-Sorting-for-Java"
            ),
            new LibraryDescription(
                    "Material Design Icons",
                    "Official icon sets from Google designed under the material design guidelines.",
                    "Apache Version 2.0",
                    "Google",
                    "https://github.com/google/material-design-icons"
            ),
            new LibraryDescription(
                    "Android Jetpack",
                    "Androidx suite of tools and libraries",
                    "Apache Version 2.0",
                    "Google",
                    "https://developer.android.com/jetpack"
            ),
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.fragment_about, container, false);

        ((TextView) view.findViewById(R.id.aboutVersion)).setText(getVersionString());
        View appLayout = view.findViewById(R.id.about_application);
        appLayout.setTag(getString(R.string.app_link));
        TextView descView = view.findViewById(R.id.aboutDescription);
        descView.setText("- "+getString(R.string.app_description));
        appLayout.setOnClickListener(this);

        LinearLayout libsLayout = (LinearLayout) view.findViewById(R.id.about_libraries);
        for (int i = 0; i < mDescriptions.length; i++) {
            View cardView = inflater.inflate(R.layout.card_deps, libsLayout, false);

            ((TextView) cardView.findViewById(R.id.libraryName)).setText(mDescriptions[i].name);
            ((TextView) cardView.findViewById(R.id.libraryCreator)).setText(mDescriptions[i].owner);
            ((TextView) cardView.findViewById(R.id.libraryDescription)).setText(mDescriptions[i].description);
            ((TextView) cardView.findViewById(R.id.libraryLicense)).setText(mDescriptions[i].license);

            cardView.setTag(mDescriptions[i].link);
            cardView.setOnClickListener(this);
            libsLayout.addView(cardView);
        }

        getActivity().setTitle(R.string.menu_about);
        ((MainActivity)getActivity()).setSubTitle("");
        return view;
    }

    private String getVersionString() {
        try {
            PackageInfo pi = getActivity()
                    .getPackageManager()
                    .getPackageInfo(getActivity().getPackageName(), 0);
            return "Version " + pi.versionName + " (" + Integer.toString(pi.versionCode) + ")";
        } catch (Exception e) {
            Log.e("AboutFragment#117","getVersionString()",e);
            return "";
        }
    }

    @Override
    public void onClick(View v) {
        String link = (String) v.getTag();
        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(link));
        startActivity(browserIntent);
    }
}
