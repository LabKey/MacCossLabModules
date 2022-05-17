package org.labkey.panoramapublic.proteomexchange.validator;

import org.labkey.api.targetedms.ISpectrumLibrary;
import org.labkey.api.targetedms.ITargetedMSRun;
import org.labkey.panoramapublic.model.validation.SkylineDocSpecLib;

public class ValidatorSkylineDocSpecLib extends SkylineDocSpecLib
{
    private ITargetedMSRun _run;
    private ISpectrumLibrary _library;

    public ValidatorSkylineDocSpecLib() {}

    public ValidatorSkylineDocSpecLib(ISpectrumLibrary library, ITargetedMSRun run)
    {
        _library = library;
        _run = run;
        setSpectrumLibraryId(library.getId());
    }

    public ISpectrumLibrary getLibrary()
    {
        return _library;
    }

    public ITargetedMSRun getRun()
    {
        return _run;
    }
}
