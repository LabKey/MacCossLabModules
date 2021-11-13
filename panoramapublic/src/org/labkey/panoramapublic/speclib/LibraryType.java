package org.labkey.panoramapublic.speclib;

import org.jetbrains.annotations.NotNull;

public enum LibraryType
{
    bibliospec_lite("BiblioSpec", "blib", true),
    bibliospec("BiblioSpec", "blib", true),
    elib("EncyclopeDIA", "elib", true),
    hunter("X!Hunter", "mgf", false),
    midas("MIDAS", "midas", false),
    nist("NIST", "msp", false),
    spectrast("SpectraST", "sptxt", false),
    chromatogram("Panorama Chromatogram Library", "clib", false),
    unknown("Unknown Library Type", "unknown", false);

    private final String _name;
    private final String _extension;
    private final boolean _supported;

    LibraryType(String type, String extension, boolean supported)
    {
        _name = type;
        _extension = extension;
        _supported = supported;
    }

    public String getName()
    {
        return _name;
    }

    public String getExtension()
    {
        return _extension;
    }

    public boolean isSupported()
    {
        return _supported;
    }

    public static LibraryType getType(@NotNull String typeName)
    {
        try
        {
            return valueOf(typeName);
        }
        catch(IllegalArgumentException e)
        {
            return unknown;
        }
    }
}
