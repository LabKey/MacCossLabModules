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
