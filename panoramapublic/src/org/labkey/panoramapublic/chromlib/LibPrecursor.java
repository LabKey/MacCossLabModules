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
