package org.labkey.panoramapublic.model.speclib;

import org.jetbrains.annotations.NotNull;
import org.labkey.panoramapublic.speclib.LibraryType;

public class SpecLibKey
{
    private final String _name;
    private final String _fileNameHint;
    private final String _skylineLibraryId;
    private final String _libraryType;
    private final String _revision;

    private static final String SEP = "__&&__";

    public SpecLibKey(@NotNull String name, @NotNull String fileNameHint, String skylineLibraryId, String libraryType, String revision)
    {
        _name = name;
        _fileNameHint = fileNameHint;
        _skylineLibraryId = skylineLibraryId;
        _libraryType = libraryType;
        _revision = revision;
    }

    public String getStringKey()
    {
        return String.format("%s%s%s%s%s", _name, SEP, _libraryType,
                (_fileNameHint != null ? SEP + _fileNameHint : ""),
                (_skylineLibraryId != null ? SEP + _skylineLibraryId : ""),
                (_revision != null ? SEP + _revision : ""));

    }

    public static SpecLibKey from(String key)
    {
        String[] parts = key.split(SEP);
        if (parts.length > 1)
        {
            String name = parts[0];
            String libraryType = parts[1];
            String fileNameHint = parts.length > 2 ? parts[2] : null;
            String skylineLibId = parts.length > 3 ? parts[3] : null;
            String revision = parts.length > 4 ? parts[4] : null;
            return new SpecLibKey(name, fileNameHint, skylineLibId, libraryType, revision);
        }
        return null;
    }
}
