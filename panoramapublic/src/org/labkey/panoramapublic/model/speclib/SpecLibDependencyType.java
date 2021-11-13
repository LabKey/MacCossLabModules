package org.labkey.panoramapublic.model.speclib;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.util.SafeToRenderEnum;

public enum SpecLibDependencyType implements SafeToRenderEnum
{
    STATISTICALLY_DEPENDENT("Statistically dependent results"),
    TARGETS_AND_FRAGMENTS("Used for choosing targets and fragments"),
    TARGETS_ONLY("Used for choosing targets only"),
    SUPPORTING_INFO("Used only as supporting information"),
    IRRELEVANT("Irrelevant to results");

    private final String _label;

    SpecLibDependencyType(String label)
    {
        _label = label;
    }

    public String getLabel()
    {
        return _label;
    }

    public static @Nullable SpecLibDependencyType getFromName(String name)
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
}
