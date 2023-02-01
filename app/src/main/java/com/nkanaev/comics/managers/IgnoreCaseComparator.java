package com.nkanaev.comics.managers;

public abstract class IgnoreCaseComparator extends NaturalOrderComparator{
    public abstract String stringValue(Object o);

    public int compare(Object a, Object b) {
        String strA = stringValue(a);
        if (strA==null)
            strA = "";

        String strB = stringValue(b);
        if (strB==null)
            strB = "";

        return super.compare(strA.toLowerCase(),strB.toLowerCase());
    }
}