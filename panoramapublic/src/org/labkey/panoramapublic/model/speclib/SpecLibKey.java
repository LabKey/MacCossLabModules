package org.labkey.panoramapublic.model.speclib;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.targetedms.ISpectrumLibrary;
import org.labkey.panoramapublic.speclib.LibraryType;

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

    public static @Nullable SpecLibKey from(String key)
    {
        if (key != null)
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
        }
        return null;
    }

    public static @NotNull SpecLibKey fromLibrary(@NotNull ISpectrumLibrary library)
    {
        return new SpecLibKey(library.getName(), library.getFileNameHint(), library.getSkylineLibraryId(), library.getRevision(), library.getLibraryType());
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

    public static class TestCase extends Assert
    {
        @Test
        public void testSpecLibKey()
        {
            var key = new SpecLibKey("BSA Library", null, null, null, LibraryType.nist.getName());
            var keyString =  LibraryType.nist.getName() + SEP + "BSA Library";
            assertEquals(keyString, key.toString());
            assertEquals(SpecLibKey.from(keyString), key);

            key = new SpecLibKey("1593Lumos_1229",
                    "CFP10_MRM.blib",
                    "urn:lsid:proteome.gs.washington.edu:spectral_library:bibliospec:nr:CFP10_MRM", "1",
                    LibraryType.bibliospec_lite.getName());
            keyString = String.format("%s%s%s%s%s%s%s%s%s",
                    LibraryType.bibliospec_lite.getName(), SEP,
                    "1593Lumos_1229", SEP,
                    "CFP10_MRM.blib", SEP,
                    "urn:lsid:proteome.gs.washington.edu:spectral_library:bibliospec:nr:CFP10_MRM", SEP,
                    "1");
            assertEquals(keyString, key.toString());
            assertEquals(SpecLibKey.from(keyString), key);

            assertNull(SpecLibKey.from(null));
            assertNull(SpecLibKey.from("BSA Library"));
            assertNull(SpecLibKey.from("BSA Library" + SEP));
        }
    }
}
