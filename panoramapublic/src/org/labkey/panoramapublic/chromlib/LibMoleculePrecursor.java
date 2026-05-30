/*
 * Copyright (c) 2021-2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.panoramapublic.chromlib;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.targetedms.RepresentativeDataState;

public class LibMoleculePrecursor extends LibGeneralPrecursor
{
    // Fields from the MoleculePrecursor table
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
