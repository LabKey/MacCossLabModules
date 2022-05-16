package org.labkey.panoramapublic.model.validation;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public abstract class GenericValidationStatus <D extends SkylineDocValidation, L extends SpecLibValidation>
{
    private DataValidation _validation;

    public GenericValidationStatus() {}

    public DataValidation getValidation()
    {
        return _validation;
    }

    public void setValidation(DataValidation validation)
    {
        _validation = validation;
    }

    public abstract @NotNull List<L> getSpectralLibraries();
    public abstract @NotNull List<D> getSkylineDocs();
    public abstract @NotNull List<Modification> getModifications();

    public PxStatus getPxStatus()
    {
        boolean allSampleFilesFound = getSkylineDocs().stream().allMatch(SkylineDocValidation::foundAllSampleFiles);
        boolean allModsValid = getModifications().stream().allMatch(Modification::isValid);
        boolean specLibsValid = getSpectralLibraries().stream().allMatch(SpecLibValidation::isValid);
        if (allSampleFilesFound)
        {
            return (allModsValid && specLibsValid) ? PxStatus.Complete : PxStatus.IncompleteMetadata;
        }
        return PxStatus.NotValid;
    }
}
