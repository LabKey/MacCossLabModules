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

// For table panoramapublic.skylinedocmodification
public class SkylineDocModification
{
    private int _skylineDocValidationId;
    private int _modificationValidationId;

    public SkylineDocModification() {}

    public SkylineDocModification(int skylineDocValidationId, int modificationValidationId)
    {
        _skylineDocValidationId = skylineDocValidationId;
        _modificationValidationId = modificationValidationId;
    }

    public int getSkylineDocValidationId()
    {
        return _skylineDocValidationId;
    }

    public void setSkylineDocValidationId(int skylineDocValidationId)
    {
        _skylineDocValidationId = skylineDocValidationId;
    }

    public int getModificationValidationId()
    {
        return _modificationValidationId;
    }

    public void setModificationValidationId(int modificationValidationId)
    {
        _modificationValidationId = modificationValidationId;
    }
}
