package org.labkey.panoramapublic.chromlib;

import org.labkey.api.targetedms.RepresentativeDataState;

public class LibPrecursor extends LibGeneralPrecursor
{
    // Fields from the Precursor table
    private String _modifiedSequence;

    public LibPrecursor()
    {
    }

    LibPrecursor(long peptideGroupId, double precursorMz, int charge, RepresentativeDataState state, String modifiedSequence)
    {
        super(peptideGroupId, precursorMz, charge, state);
        _modifiedSequence = modifiedSequence;
    }

    public String getModifiedSequence()
    {
        return _modifiedSequence;
    }

    public void setModifiedSequence(String modifiedSequence)
    {
        _modifiedSequence = modifiedSequence;
    }

    @Override
    public LibPrecursorKey getKey()
    {
        return new LibPrecursorKey(getMz(), getCharge(), _modifiedSequence);
    }
}
