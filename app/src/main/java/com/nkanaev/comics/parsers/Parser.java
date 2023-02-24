package com.nkanaev.comics.parsers;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

public interface Parser {
    static String PAGEMETADATA_KEY_NAME = "name";
    static String PAGEMETADATA_KEY_MIME= "mime";
    static String PAGEMETADATA_KEY_WIDTH = "width";
    static String PAGEMETADATA_KEY_HEIGHT = "height";

    void parse() throws IOException;

    int numPages() throws IOException;

    InputStream getPage(int num) throws IOException;

    Map getPageMetaData(int num) throws IOException;

    String getType();

    void destroy();
}
