package com.nkanaev.comics.managers;

import java.util.Comparator;

public abstract class IgnoreCaseComparator implements Comparator{
    public abstract String stringValue(Object o);

    public int compare(Object a, Object b) {
        String strA = stringValue(a);
        if (strA==null)
            strA = "";

        String strB = stringValue(b);
        if (strB==null)
            strB = "";

        return strA.compareToIgnoreCase(strB);
    }
}