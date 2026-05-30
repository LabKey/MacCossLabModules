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

import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.targetedms.TargetedMSService;

// For table panoramapublic.skylinedocsamplefile
public class SkylineDocSampleFile extends DataFile
{
    private int _skylineDocValidationId;
    private String _filePathImported;
    private Long _sampleFileId;

    public SkylineDocSampleFile() {}

    public int getSkylineDocValidationId()
    {
        return _skylineDocValidationId;
    }

    public void setSkylineDocValidationId(int skylineDocValidationId)
    {
        _skylineDocValidationId = skylineDocValidationId;
    }

    public Long getSampleFileId()
    {
        return _sampleFileId;
    }

    public void setSampleFileId(Long sampleFileId)
    {
        _sampleFileId = sampleFileId;
    }


    /**
     * Path of the sample file imported into the Skyline document
     */
    public String getFilePathImported()
    {
        return _filePathImported;
    }

    public void setFilePathImported(String filePathImported)
    {
        _filePathImported = filePathImported;
    }

    @Override
    @NotNull
    public JSONObject toJSON(Container container)
    {
        JSONObject jsonObject = super.toJSON(container);
        if (_sampleFileId != null && container != null)
        {
            String replicateName = TargetedMSService.get().getSampleReplicateName(_sampleFileId, container);
            if (replicateName != null)
            {
                jsonObject.put("replicate", replicateName);
            }
            if (isAmbiguous())
            {
                jsonObject.put("container", container.getPath());
            }
        }
        return jsonObject;
    }
}
