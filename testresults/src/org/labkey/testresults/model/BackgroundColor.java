package org.labkey.testresults.model;

public enum BackgroundColor
{
    pass("#caff95"),
    error("#ffcaca"),
    warn("#ffffca"),
    unknown("#cccccc");

    private final String htmlText;

    BackgroundColor(String text) {
        htmlText = text;
    }

    public String toString() {
        return htmlText;
    }
}
