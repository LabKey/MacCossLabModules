package org.labkey.panoramapublic.proteomexchange.validator;

import org.labkey.api.targetedms.ISampleFile;
import org.labkey.panoramapublic.model.validation.SkylineDocSampleFile;

public class ValidatorSampleFile extends SkylineDocSampleFile
{
    private ISampleFile _sampleFile;

    public ValidatorSampleFile() {}

    public ValidatorSampleFile(ISampleFile sampleFile)
    {
        setName(sampleFile.getFileName());
        setFilePathImported(sampleFile.getFilePath());
        _sampleFile = sampleFile;
    }

    public ISampleFile getSampleFile()
    {
        return _sampleFile;
    }
}
