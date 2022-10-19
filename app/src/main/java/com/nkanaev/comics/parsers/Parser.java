package com.nkanaev.comics.parsers;

import java.io.IOException;
import java.io.InputStream;

public interface Parser {
    void parse() throws IOException;

    int numPages() throws IOException;

    InputStream getPage(int num) throws IOException;

    String getType();

    void destroy();
}
