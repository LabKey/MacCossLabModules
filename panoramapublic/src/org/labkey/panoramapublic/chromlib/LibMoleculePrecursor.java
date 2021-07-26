package org.labkey.panoramapublic.chromlib;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.targetedms.RepresentativeDataState;

public class LibMoleculePrecursor extends LibGeneralPrecursor
{
    // Fields from the Molecule table
    private String _customIonName;
    private String _ionFormula;
    private Double _massMonoisotopic; // not null
    private Double _massAverage; // not null

    public LibMoleculePrecursor()
    {
    }

    LibMoleculePrecursor(long peptideGroupId, double precursorMz, int charge, RepresentativeDataState state,
                         String customIonName, String ionFormula, Double massMonoisotopic, Double massAverage)
    {
        super(peptideGroupId, precursorMz, charge, state);
        _customIonName = customIonName;
        _ionFormula = ionFormula;
        _massMonoisotopic = massMonoisotopic;
        _massAverage = massAverage;
    }

    public String getIonFormula()
    {
        return _ionFormula;
    }

    public void setIonFormula(String ionFormula)
    {
        _ionFormula = ionFormula;
    }

    public Double getMassMonoisotopic()
    {
        return _massMonoisotopic;
    }

    public void setMassMonoisotopic(Double massMonoisotopic)
    {
        _massMonoisotopic = massMonoisotopic;
    }

    public Double getMassAverage()
    {
        return _massAverage;
    }

    public void setMassAverage(Double massAverage)
    {
        _massAverage = massAverage;
    }

    public String getCustomIonName()
    {
        return _customIonName;
    }

    public void setCustomIonName(String customIonName)
    {
        _customIonName = customIonName;
    }

    @Override
    public LibPrecursorKey getKey()
    {
        return new LibPrecursorKey(getMz(), getCharge(),
                StringUtils.join(new String[]{_customIonName, _ionFormula, String.valueOf(_massMonoisotopic), String.valueOf(_massAverage)}, ','));
    }
}
