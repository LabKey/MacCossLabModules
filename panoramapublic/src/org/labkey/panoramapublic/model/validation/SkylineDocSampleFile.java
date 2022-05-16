package org.labkey.panoramapublic.model.validation;

import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.targetedms.TargetedMSService;

import java.nio.file.Path;

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
