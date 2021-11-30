package org.labkey.panoramapublic.model.speclib;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.util.SafeToRenderEnum;

public enum SpecLibSourceType implements SafeToRenderEnum
{
    LOCAL("Uploaded to project"),
    PUBLIC_LIBRARY("Public library"),
    OTHER_REPOSITORY("Source files in external repository"),
    UNAVAILABLE("Source files unavailable");

    private final String _label;

    SpecLibSourceType(String label)
    {
        _label = label;
    }

    public String getLabel()
    {
        return _label;
    }

    public static @Nullable SpecLibSourceType getForName(String name)
    {
        try
        {
            return name != null ? valueOf(name) : null;
        }
        catch(IllegalArgumentException e)
        {
            return null;
        }
    }

    public static SpecLibSourceType[] valuesForLibrary(SpectralLibrary library)
    {
        if (library.isSupported())
        {
            return values();
        }
        return new SpecLibSourceType[]{PUBLIC_LIBRARY};
    }
}
