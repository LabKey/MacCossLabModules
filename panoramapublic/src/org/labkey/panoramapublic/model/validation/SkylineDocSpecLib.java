/*
 * Copyright (c) 2022-2026 LabKey Corporation
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
package org.labkey.panoramapublic.model.validation;

// For table panoramapublic.skylinedocspeclib
public class SkylineDocSpecLib
{
    private int _id;
    private int _skylineDocValidationId;
    private int _speclibValidationId;
    private boolean _included; // true if the library file is included in the .sky.zip
    private long _spectrumLibraryId; // targetedms.SpectrumLibrary.Id

    public int getId()
    {
        return _id;
    }

    public void setId(int id)
    {
        _id = id;
    }

    public int getSkylineDocValidationId()
    {
        return _skylineDocValidationId;
    }

    public void setSkylineDocValidationId(int skylineDocValidationId)
    {
        _skylineDocValidationId = skylineDocValidationId;
    }

    public int getSpeclibValidationId()
    {
        return _speclibValidationId;
    }

    public void setSpeclibValidationId(Integer speclibValidationId)
    {
        _speclibValidationId = speclibValidationId;
    }

    public boolean isIncluded()
    {
        return _included;
    }

    public void setIncluded(boolean included)
    {
        _included = included;
    }

    public long getSpectrumLibraryId()
    {
        return _spectrumLibraryId;
    }

    public void setSpectrumLibraryId(long spectrumLibraryId)
    {
        _spectrumLibraryId = spectrumLibraryId;
    }
}
