package org.labkey.panoramapublic.model.speclib;

import org.jetbrains.annotations.NotNull;
import java.util.Objects;

public class SpecLibKey
{
    private final String _name;
    private final String _fileNameHint;
    private final String _skylineLibraryId;
    private final String _libraryType;
    private final String _revision;

    private static final String SEP = "__&&__";

    public SpecLibKey(@NotNull String name, String fileNameHint, String skylineLibraryId, String revision, @NotNull String libraryType)
    {
        _name = name;
        _fileNameHint = fileNameHint;
        _skylineLibraryId = skylineLibraryId;
        _revision = revision;
        _libraryType = libraryType;
    }

    public String toString()
    {
        return String.format("%s%s%s%s%s%s", _libraryType, SEP, _name,
                (_fileNameHint != null ? SEP + _fileNameHint : ""),
                (_skylineLibraryId != null ? SEP + _skylineLibraryId : ""),
                (_revision != null ? SEP + _revision : ""));

    }

    public static SpecLibKey from(String key)
    {
        var parts = key.split(SEP);
        if (parts.length > 1)
        {
            var libraryType = parts[0];
            var name = parts[1];
            var fileNameHint = parts.length > 2 ? parts[2] : null;
            var skylineLibId = parts.length > 3 ? parts[3] : null;
            var revision = parts.length > 4 ? parts[4] : null;
            return new SpecLibKey(name, fileNameHint, skylineLibId, revision, libraryType);
        }
        return null;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SpecLibKey that = (SpecLibKey) o;
        return _name.equals(that._name)
                && _libraryType.equals(that._libraryType)
                && Objects.equals(_fileNameHint, that._fileNameHint)
                && Objects.equals(_skylineLibraryId, that._skylineLibraryId)
                && Objects.equals(_revision, that._revision);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(_name, _fileNameHint, _skylineLibraryId, _libraryType, _revision);
    }
}
