package org.labkey.panoramapublic.catalog;

public class CatalogEntrySettings
{
    // Defaults
    public static final long MAX_FILE_SIZE = 5 * 1024 * 1024;
    public static final int IMG_WIDTH = 600;
    public static final int IMG_HEIGHT = 400;
    public static final int MAX_TEXT_CHARS = 500;
    public static final int MAX_ENTRIES = 25;

    private final boolean _enabled;
    private final long _maxFileSize;
    private final int _imgWidth;
    private final int _imgHeight;
    private final int _maxTextChars;
    private final int _maxDisplayEntries;

    public static CatalogEntrySettings DISABLED = new CatalogEntrySettings(false, MAX_FILE_SIZE, IMG_WIDTH, IMG_HEIGHT, MAX_TEXT_CHARS, MAX_ENTRIES);

    public CatalogEntrySettings(long maxFileSize, int imgWidth, int imgHeight, int maxTextChars, int maxDisplayEntries)
    {
        this(true, maxFileSize, imgWidth, imgHeight, maxTextChars, maxDisplayEntries);
    }

    private CatalogEntrySettings(boolean enabled, long maxFileSize, int imgWidth, int imgHeight, int maxTextChars, int maxDisplayEntries)
    {
        _enabled = enabled;
        _maxFileSize = maxFileSize;
        _imgWidth = imgWidth;
        _imgHeight = imgHeight;
        _maxTextChars = maxTextChars;
        _maxDisplayEntries = maxDisplayEntries;
    }

    public boolean isEnabled()
    {
        return _enabled;
    }

    public long getMaxFileSize()
    {
        return _maxFileSize;
    }

    public int getImgWidth()
    {
        return _imgWidth;
    }

    public int getImgHeight()
    {
        return _imgHeight;
    }

    public int getMaxTextChars()
    {
        return _maxTextChars;
    }

    public int getMaxDisplayEntries()
    {
        return _maxDisplayEntries;
    }
}
